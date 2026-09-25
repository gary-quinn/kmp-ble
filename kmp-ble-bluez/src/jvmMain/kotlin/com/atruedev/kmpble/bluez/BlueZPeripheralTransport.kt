package com.atruedev.kmpble.bluez

import com.atruedev.kmpble.backend.GattServiceRecord
import com.atruedev.kmpble.backend.PeripheralEvent
import com.atruedev.kmpble.backend.PeripheralEventListener
import com.atruedev.kmpble.backend.PeripheralFeatures
import com.atruedev.kmpble.backend.PeripheralTransport
import com.atruedev.kmpble.bonding.BondRemovalResult
import com.atruedev.kmpble.bonding.BondState
import com.atruedev.kmpble.bonding.PairingHandler
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.ConnectionFailed
import com.atruedev.kmpble.error.ConnectionFailureReason
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.gatt.WriteType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.bluez.exceptions.BluezInProgressException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * [PeripheralTransport] over BlueZ `Device1` and `GattCharacteristic1` / `GattDescriptor1`.
 *
 * `Connect` and `Pair` are asynchronous D-Bus calls polled from a coroutine, so they are not
 * capped by dbus-java's 20 s reply timeout and a cancelled caller aborts them with `Disconnect`
 * / `CancelPairing`. Other calls block on [Dispatchers.IO] through [runInterruptible], so a
 * GATT timeout interrupts the waiting thread instead of holding the queue until D-Bus replies.
 *
 * Powering the adapter off makes BlueZ emit `Device1.Connected = false` shortly before
 * `Adapter1.Powered = false`, so a link loss is reported after [linkLossGrace] and becomes
 * [PeripheralEvent.AdapterOff] when the adapter goes down within that window.
 */
internal class BlueZPeripheralTransport(
    private val address: String,
    private val devicePath: String?,
    private val sessionFactory: BlueZDeviceSessionFactory = DefaultBlueZDeviceSessionFactory,
    private val pollInterval: Duration = POLL_INTERVAL,
    private val tableChangeDebounce: Duration = TABLE_CHANGE_DEBOUNCE,
    private val linkLossGrace: Duration = LINK_LOSS_GRACE,
) : PeripheralTransport,
    BlueZDeviceSignals {
    override val features: PeripheralFeatures =
        PeripheralFeatures(supportsReliableWrite = true, supportsBonding = true, supportsL2cap = false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handles = BlueZHandleTable()
    private val readEchoes = BlueZReadEchoFilter()
    private val sessionLock = Any()

    @Volatile private var listener: PeripheralEventListener? = null

    @Volatile private var session: BlueZDeviceSession? = null

    @Volatile private var signalsRegistered = false

    @Volatile private var servicesReady = false

    @Volatile private var lastMtu: Int = DEFAULT_ATT_MTU

    @Volatile private var tableChangeJob: Job? = null

    @Volatile private var pairingHandler: PairingHandler? = null

    private val linkLossPending = AtomicBoolean(false)

    override fun setEventListener(listener: PeripheralEventListener?) {
        this.listener = listener
    }

    override suspend fun connect(options: ConnectionOptions) {
        val session = openSession()
        servicesReady = false
        linkLossPending.set(false)
        withContext(Dispatchers.IO) {
            if (!signalsRegistered) {
                if (!session.registerSignals(this@BlueZPeripheralTransport)) {
                    throw BleException(
                        ConnectionFailed(
                            "Failed to register BlueZ signal handlers",
                            ConnectionFailureReason.UNKNOWN,
                            BlueZ.ERROR_DBUS_UNAVAILABLE,
                        ),
                    )
                }
                signalsRegistered = true
            }
            if (pairingHandler != null) BlueZPairingAgent.ensureRegistered()
        }
        try {
            awaitCall(withContext(Dispatchers.IO) { session.connect() }) { session.disconnect() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!withContext(Dispatchers.IO) { runCatching { session.isConnected() }.getOrDefault(false) }) {
                throw BleException(
                    ConnectionFailed(
                        "BlueZ connect failed: ${e.message}",
                        ConnectionFailureReason.GATT_ERROR,
                        BlueZ.ERROR_CONNECT_FAILED,
                    ),
                )
            }
        }
        awaitCondition { session.isConnected() }
    }

    override suspend fun disconnect() {
        val session = session ?: return
        servicesReady = false
        withContext(Dispatchers.IO) {
            runCatching { session.disconnect() }
            withTimeoutOrNull(DISCONNECT_WAIT) { awaitCondition { !session.isConnected() } }
        }
    }

    override suspend fun discoverServices(): List<GattServiceRecord> {
        val session = requireSession()
        awaitCondition { session.isServicesResolved() }
        val snapshots = runInterruptible(Dispatchers.IO) { session.gattServices() }
        lastMtu =
            snapshots
                .flatMap { it.characteristics }
                .mapNotNull { it.mtu }
                .maxOrNull()
                ?.coerceAtLeast(DEFAULT_ATT_MTU) ?: DEFAULT_ATT_MTU
        servicesReady = true
        return snapshots.toRecords(handles)
    }

    override suspend fun read(characteristic: Long): ByteArray {
        val path = requirePath(characteristic, "characteristic")
        readEchoes.beginRead(path)
        var value: ByteArray? = null
        try {
            return gatt("read") { it.readCharacteristic(path) }.also { value = it }
        } finally {
            readEchoes.endRead(path, value).forEach { held ->
                listener?.onEvent(PeripheralEvent.ValueChanged(characteristic, held))
            }
        }
    }

    override suspend fun write(
        characteristic: Long,
        data: ByteArray,
        type: WriteType,
    ) {
        val bluezType =
            when (type) {
                WriteType.WithResponse -> "request"
                WriteType.WithoutResponse, WriteType.Signed -> "command"
            }
        gatt("write") { it.writeCharacteristic(requirePath(characteristic, "characteristic"), data, bluezType) }
    }

    override suspend fun writeReliable(
        characteristic: Long,
        data: ByteArray,
    ) {
        gatt(
            "writeReliable",
        ) { it.writeCharacteristic(requirePath(characteristic, "characteristic"), data, "reliable") }
    }

    override suspend fun readDescriptor(descriptor: Long): ByteArray =
        gatt("readDescriptor") { it.readDescriptor(requirePath(descriptor, "descriptor")) }

    override suspend fun writeDescriptor(
        descriptor: Long,
        data: ByteArray,
    ) {
        gatt("writeDescriptor") { it.writeDescriptor(requirePath(descriptor, "descriptor"), data) }
    }

    override suspend fun setNotify(
        characteristic: Long,
        enabled: Boolean,
    ) {
        gatt(if (enabled) "startNotify" else "stopNotify") { session ->
            val path = requirePath(characteristic, "characteristic")
            try {
                if (enabled) session.startNotify(path) else session.stopNotify(path)
            } catch (_: BluezInProgressException) {
            }
        }
    }

    override suspend fun readRssi(): Int {
        val rssi = runInterruptible(Dispatchers.IO) { requireSession().rssi() }
        return rssi ?: throw BleException(GattError("readRssi", GattStatus.RequestNotSupported))
    }

    override suspend fun mtu(): Int = lastMtu

    override suspend fun bondState(): BondState =
        withContext(Dispatchers.IO) {
            val session = runCatching { openSessionBlocking() }.getOrNull() ?: return@withContext BondState.Unknown
            runCatching { if (session.isBonded() ?: session.isPaired()) BondState.Bonded else BondState.NotBonded }
                .getOrDefault(BondState.Unknown)
        }

    override suspend fun createBond() {
        val session = openSession()
        if (pairingHandler != null) withContext(Dispatchers.IO) { BlueZPairingAgent.ensureRegistered() }
        try {
            awaitCall(withContext(Dispatchers.IO) { session.pair() }) { session.cancelPairing() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!withContext(Dispatchers.IO) { runCatching { session.isPaired() }.getOrDefault(false) }) {
                throw BleException(
                    ConnectionFailed("BlueZ pairing failed: ${e.message}", ConnectionFailureReason.BONDING_FAILED),
                )
            }
        }
    }

    override fun removeBond(): BondRemovalResult {
        val session =
            try {
                openSessionBlocking()
            } catch (e: BleException) {
                return BondRemovalResult.Failed(e.error.toString())
            }
        val wasConnected = runCatching { session.isConnected() }.getOrDefault(false)
        return try {
            session.removeFromAdapter()
            releaseSession(session)
            if (wasConnected) {
                listener?.onEvent(
                    PeripheralEvent.Disconnected(
                        ConnectionLost("Device removed from BlueZ by removeBond()", ConnectionFailureReason.LINK_LOSS),
                    ),
                )
            }
            BondRemovalResult.Success
        } catch (e: Exception) {
            BondRemovalResult.Failed("BlueZ RemoveDevice failed: ${e.message}")
        }
    }

    override fun setPairingHandler(handler: PairingHandler?) {
        pairingHandler = handler
        BlueZPairingAgent.setHandler(address, handler)
    }

    override fun close() {
        listener = null
        BlueZPairingAgent.setHandler(address, null)
        scope.cancel()
        val closing =
            synchronized(sessionLock) {
                session.also {
                    session = null
                    signalsRegistered = false
                }
            } ?: return
        servicesReady = false
        cleanupScope.launch {
            runCatching { if (closing.isConnected()) closing.disconnect() }
            closing.closeConnection()
        }
    }

    override fun onDeviceChanged(changed: Map<String, Any?>) {
        if ((changed["Connected"] as? Boolean) == false) {
            servicesReady = false
            deferLinkLoss()
        }
        val bonded = (changed["Bonded"] as? Boolean) ?: (changed["Paired"] as? Boolean)
        if (bonded != null) {
            listener?.onEvent(PeripheralEvent.BondStateChanged(if (bonded) BondState.Bonded else BondState.NotBonded))
        }
    }

    override fun onAttributeChanged(
        path: String,
        changed: Map<String, Any?>,
    ) {
        val handle = handles.handleOrNull(path) ?: return
        byteArrayFromAny(changed["Value"])?.let { value ->
            if (readEchoes.admit(path, value)) listener?.onEvent(PeripheralEvent.ValueChanged(handle, value))
        }
        (changed["MTU"] as? Number)?.toInt()?.let { mtu ->
            if (mtu > lastMtu) {
                lastMtu = mtu
                listener?.onEvent(PeripheralEvent.MtuChanged(mtu))
            }
        }
    }

    override fun onGattTableChanged() {
        if (!servicesReady) return
        tableChangeJob?.cancel()
        tableChangeJob =
            scope.launch {
                delay(tableChangeDebounce)
                if (servicesReady) listener?.onEvent(PeripheralEvent.ServicesChanged)
            }
    }

    override fun onAdapterPowered(powered: Boolean) {
        if (!powered) {
            servicesReady = false
            linkLossPending.set(false)
            listener?.onEvent(PeripheralEvent.AdapterOff)
        }
    }

    private fun deferLinkLoss() {
        linkLossPending.set(true)
        scope.launch {
            delay(linkLossGrace)
            if (linkLossPending.compareAndSet(true, false)) listener?.onEvent(PeripheralEvent.Disconnected())
        }
    }

    private suspend fun <T> gatt(
        operation: String,
        block: (BlueZDeviceSession) -> T,
    ): T {
        val session = requireSession()
        return runInterruptible(Dispatchers.IO) {
            try {
                block(session)
            } catch (e: BleException) {
                throw e
            } catch (e: InterruptedException) {
                throw e
            } catch (e: Exception) {
                throw BleException(e.toBlueZGattError(operation))
            }
        }
    }

    private suspend fun awaitCall(
        call: BlueZPendingCall,
        abort: () -> Unit,
    ) {
        try {
            while (!call.isDone()) delay(pollInterval)
        } catch (e: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) { runCatching(abort) }
            throw e
        }
        withContext(Dispatchers.IO) { call.result() }
    }

    private suspend fun openSession(): BlueZDeviceSession = withContext(Dispatchers.IO) { openSessionBlocking() }

    private fun openSessionBlocking(): BlueZDeviceSession =
        synchronized(sessionLock) {
            session?.let { return it }
            when (val opened = sessionFactory.open(devicePath, address)) {
                is BlueZDeviceSessionOpenResult.Ready -> opened.session.also { session = it }
                is BlueZDeviceSessionOpenResult.Failed ->
                    throw BleException(
                        ConnectionFailed(
                            opened.message,
                            if (opened.code == BlueZ.ERROR_DEVICE_NOT_FOUND) {
                                ConnectionFailureReason.UNKNOWN_DEVICE
                            } else {
                                ConnectionFailureReason.UNKNOWN
                            },
                            opened.code,
                        ),
                    )
            }
        }

    private fun releaseSession(released: BlueZDeviceSession) {
        synchronized(sessionLock) {
            if (session === released) {
                session = null
                signalsRegistered = false
            }
        }
        servicesReady = false
        cleanupScope.launch { released.closeConnection() }
    }

    private fun requireSession(): BlueZDeviceSession =
        session
            ?: throw BleException(ConnectionFailed("BlueZ device session is not open", ConnectionFailureReason.UNKNOWN))

    private fun requirePath(
        handle: Long,
        kind: String,
    ): String =
        handles.pathFor(handle) ?: throw BleException(GattError("resolve $kind handle $handle", GattStatus.Failure))

    private suspend fun awaitCondition(predicate: () -> Boolean) {
        while (!runInterruptible(Dispatchers.IO) { predicate() }) delay(pollInterval)
    }

    private companion object {
        const val DEFAULT_ATT_MTU = 23
        val POLL_INTERVAL = 50.milliseconds
        val TABLE_CHANGE_DEBOUNCE = 250.milliseconds
        val LINK_LOSS_GRACE = 500.milliseconds
        val DISCONNECT_WAIT = 3.seconds
        val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
