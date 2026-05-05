@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.peripheral

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.bonding.BondRemovalResult
import com.atruedev.kmpble.bonding.BondState
import com.atruedev.kmpble.connection.BondingPreference
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.ReconnectionStrategy
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.connection.internal.ConnectionEvent
import com.atruedev.kmpble.connection.internal.ReconnectionHandler
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.ConnectionFailed
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.OperationFailed
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.gatt.internal.CCCD_UUID
import com.atruedev.kmpble.gatt.internal.DISABLE_NOTIFICATION_VALUE
import com.atruedev.kmpble.gatt.internal.ENABLE_INDICATION_VALUE
import com.atruedev.kmpble.gatt.internal.ENABLE_NOTIFICATION_VALUE
import com.atruedev.kmpble.gatt.internal.GattResult
import com.atruedev.kmpble.gatt.internal.LargeWriteHandler
import com.atruedev.kmpble.gatt.internal.NotConnectedException
import com.atruedev.kmpble.gatt.internal.ObservationManager
import com.atruedev.kmpble.gatt.internal.PendingOp
import com.atruedev.kmpble.gatt.internal.PendingOperations
import com.atruedev.kmpble.l2cap.AndroidL2capChannel
import com.atruedev.kmpble.l2cap.BluetoothL2capSocket
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.l2cap.L2capException
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import com.atruedev.kmpble.peripheral.internal.LifecycleSlots
import com.atruedev.kmpble.peripheral.internal.ObservationToBytes
import com.atruedev.kmpble.peripheral.internal.ObservationToObservation
import com.atruedev.kmpble.peripheral.internal.PeripheralContext
import com.atruedev.kmpble.peripheral.internal.PeripheralRegistry
import com.atruedev.kmpble.peripheral.internal.awaitGatt
import com.atruedev.kmpble.peripheral.internal.buildObservationFlow
import com.atruedev.kmpble.peripheral.internal.findCharacteristic
import com.atruedev.kmpble.peripheral.internal.findDescriptor
import com.atruedev.kmpble.quirks.BleQuirks
import com.atruedev.kmpble.quirks.QuirkRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
public class AndroidPeripheral internal constructor(
    private val device: BluetoothDevice,
    context: Context,
    internal val quirkRegistry: QuirkRegistry,
) : Peripheral {
    public constructor(device: BluetoothDevice, context: Context) :
        this(device, context, QuirkRegistry.getInstance())

    override val identifier: Identifier = Identifier(device.address)
    private val peripheralContext = PeripheralContext(identifier)
    private val bridge = AndroidGattBridge(device, context)

    private val pendingOps = PendingOperations()
    private val observationManager = ObservationManager()
    private val slots = LifecycleSlots()

    private val nativeCharMap = mutableMapOf<Characteristic, BluetoothGattCharacteristic>()
    private val nativeDescMap = mutableMapOf<Descriptor, BluetoothGattDescriptor>()

    private val bondManager = AndroidBondManager(device, context, peripheralContext)

    @OptIn(ExperimentalBleApi::class)
    private val pairingRequestHandler =
        AndroidPairingRequestHandler(device, context, peripheralContext.scope, peripheralContext.dispatcher)

    override val state: StateFlow<State> get() = peripheralContext.state
    override val bondState: StateFlow<BondState> get() = bondManager.bondState
    override val services: StateFlow<List<DiscoveredService>?> get() = peripheralContext.services
    override val maximumWriteValueLength: StateFlow<Int> get() = peripheralContext.maximumWriteValueLength

    @Volatile
    private var closed = false

    /**
     * Confined to [peripheralContext.dispatcher]. Read by [handleConnectionStateChanged]
     * to decide whether bonding is required for the freshly-established link.
     */
    private var currentConnectionOptions: ConnectionOptions? = null

    private val reconnectionHandler =
        ReconnectionHandler(
            scope = peripheralContext.scope,
            stateFlow = peripheralContext.state,
            connectAction = { opts ->
                connect(opts.copy(reconnectionStrategy = ReconnectionStrategy.None))
            },
            onMaxAttemptsExhausted = { observationManager.onPermanentDisconnect() },
        )

    init {
        bridge.onEvent = { event -> handleGattEvent(event) }
        logEvent(
            BleLogEvent.GattOperation(
                identifier,
                "DeviceQuirks: ${quirkRegistry.describe()}",
                uuid = null,
                status = null,
            ),
        )
    }

    @OptIn(ExperimentalBleApi::class)
    override suspend fun connect(options: ConnectionOptions) {
        checkNotClosed()
        reconnectionHandler.start(options)
        bondManager.start()

        withContext(peripheralContext.dispatcher) {
            currentConnectionOptions = options
            pairingRequestHandler.setHandler(options.pairingHandler)
            pairingRequestHandler.start()
            ensureBondedIfRequired(options)
            connectWithRetry(options)
        }
    }

    /**
     * Samsung quirk: certain Galaxy devices must be bonded BEFORE `connectGatt()`,
     * otherwise the connection fails silently or returns GATT 133.
     */
    private suspend fun ensureBondedIfRequired(options: ConnectionOptions) {
        if (!quirkRegistry.resolve(BleQuirks.BondBeforeConnect)) return
        if (peripheralContext.bondState.value != BondState.NotBonded) return
        if (options.bondingPreference == BondingPreference.None) return

        val bondTimeout = quirkRegistry.resolve(BleQuirks.BondStateTimeout)
        logEvent(BleLogEvent.BondEvent(identifier, "Quirk: bond-before-connect initiated"))
        try {
            withTimeout(bondTimeout) { bondManager.createBond() }
            logEvent(BleLogEvent.BondEvent(identifier, "Quirk: bond-before-connect succeeded"))
        } catch (_: TimeoutCancellationException) {
            logEvent(
                BleLogEvent.Error(
                    identifier,
                    "Quirk: bond-before-connect timed out after $bondTimeout, proceeding with connection",
                    cause = null,
                ),
            )
        }
    }

    /**
     * Attempts GATT connection with device-specific retry behavior.
     *
     * Pixel devices commonly return GATT error 133 on the first attempt - a retry with
     * a short delay (1-1.5s) typically succeeds. The retry count and delay are sourced
     * from [QuirkRegistry] so each OEM gets appropriate handling.
     *
     * The effective timeout is `max(options.timeout, quirks.connectionTimeout)` so that
     * user-configured values are respected while still accommodating OEMs that need longer
     * timeouts (e.g. Huawei at 35s vs the 30s default).
     */
    private suspend fun connectWithRetry(options: ConnectionOptions) {
        val maxAttempts = quirkRegistry.resolve(BleQuirks.GattRetryCount)
        val retryDelay = quirkRegistry.resolve(BleQuirks.GattRetryDelay)
        val timeout = maxOf(options.timeout, quirkRegistry.resolve(BleQuirks.ConnectionTimeout))

        repeat(maxAttempts) { attempt ->
            if (attempt > 0) {
                logEvent(
                    BleLogEvent.GattOperation(
                        identifier,
                        "Connection retry ${attempt + 1}/$maxAttempts after $retryDelay",
                        uuid = null,
                        status = null,
                    ),
                )
            }

            peripheralContext.processEvent(ConnectionEvent.ConnectRequested)
            peripheralContext.gattQueue.start(options.gattOperationTimeout)

            val deferred = slots.armConnect()
            val gatt = bridge.connect(options)
            if (gatt == null) {
                peripheralContext.processEvent(
                    ConnectionEvent.ConnectionLost(ConnectionFailed("connectGatt returned null")),
                )
                slots.clearConnect()
                if (attempt < maxAttempts - 1) delay(retryDelay)
                return@repeat
            }

            try {
                withTimeout(timeout) { deferred.await() }
            } catch (_: TimeoutCancellationException) {
                bridge.disconnect()
                bridge.releaseGatt()
                peripheralContext.processEvent(
                    ConnectionEvent.ConnectionLost(ConnectionFailed("Connection timeout after $timeout")),
                )
            } finally {
                slots.clearConnect()
            }

            if (peripheralContext.state.value is State.Connected) return

            if (attempt < maxAttempts - 1) {
                bridge.releaseGatt()
                delay(retryDelay)
            }
        }
    }

    @OptIn(ExperimentalBleApi::class)
    override suspend fun disconnect() {
        checkNotClosed()
        reconnectionHandler.stop()
        bondManager.stop()
        withContext(peripheralContext.dispatcher) {
            pairingRequestHandler.stop()
            if (peripheralContext.state.value is State.Disconnected) return@withContext
            peripheralContext.processEvent(ConnectionEvent.DisconnectRequested)
            val deferred = slots.armDisconnect()
            bridge.disconnect()

            try {
                withTimeout(DISCONNECT_TIMEOUT) { deferred.await() }
            } catch (_: TimeoutCancellationException) {
                peripheralContext.processEvent(
                    ConnectionEvent.ConnectionLost(OperationFailed("Disconnect timeout")),
                )
            } finally {
                slots.clearDisconnect()
                bridge.releaseGatt()
            }
        }
    }

    @OptIn(ExperimentalBleApi::class)
    override fun close() {
        if (closed) return
        closed = true
        reconnectionHandler.stop()
        pairingRequestHandler.closeSync()
        bondManager.stop()
        closeL2capChannels()
        bridge.close()
        observationManager.clear()
        peripheralContext.close()
        PeripheralRegistry.remove(identifier)
    }

    @ExperimentalBleApi
    override fun removeBond(): BondRemovalResult {
        checkNotClosed()
        return bondManager.removeBond()
    }

    override suspend fun refreshServices(): List<DiscoveredService> {
        checkNotClosed()
        return withContext(peripheralContext.dispatcher) {
            val deferred = slots.armDiscovery()
            if (!bridge.discoverServices()) {
                slots.clearDiscovery()
                throw BleException(OperationFailed("discoverServices initiation failed"))
            }
            try {
                withTimeout(SERVICE_DISCOVERY_TIMEOUT) { deferred.await() }
            } finally {
                slots.clearDiscovery()
            }
        }
    }

    override fun findCharacteristic(
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
    ): Characteristic? = services.value.findCharacteristic(serviceUuid, characteristicUuid)

    override fun findDescriptor(
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
        descriptorUuid: Uuid,
    ): Descriptor? = services.value.findDescriptor(serviceUuid, characteristicUuid, descriptorUuid)

    private fun handleGattEvent(event: GattCallbackEvent) {
        peripheralContext.scope.launch {
            when (event) {
                is GattCallbackEvent.ConnectionStateChanged -> handleConnectionStateChanged(event)
                is GattCallbackEvent.ServicesDiscovered -> handleServicesDiscovered(event)
                is GattCallbackEvent.MtuChanged -> handleMtuChanged(event)
                is GattCallbackEvent.CharacteristicRead ->
                    pendingOps.complete(
                        PendingOp.CharacteristicRead,
                        GattResult(event.value, event.status.toGattStatus()),
                    )
                is GattCallbackEvent.CharacteristicWrite ->
                    pendingOps.complete(PendingOp.CharacteristicWrite, event.status.toGattStatus())
                is GattCallbackEvent.CharacteristicChanged -> {
                    val charUuid = Uuid.parse(event.characteristic.uuid.toString())
                    val serviceUuid =
                        Uuid.parse(
                            event.characteristic.service.uuid
                                .toString(),
                        )
                    observationManager.emitByUuid(serviceUuid, charUuid, event.value)
                }
                is GattCallbackEvent.DescriptorRead ->
                    pendingOps.complete(
                        PendingOp.DescriptorRead,
                        GattResult(event.value, event.status.toGattStatus()),
                    )
                is GattCallbackEvent.DescriptorWrite ->
                    pendingOps.complete(PendingOp.DescriptorWrite, event.status.toGattStatus())
                is GattCallbackEvent.ReadRemoteRssi -> handleRssiResult(event)
            }
        }
    }

    private fun handleRssiResult(event: GattCallbackEvent.ReadRemoteRssi) {
        val status = event.status.toGattStatus()
        if (status.isSuccess()) {
            pendingOps.complete(PendingOp.RssiRead, event.rssi)
        } else {
            pendingOps.fail(PendingOp.RssiRead, BleException(GattError("readRssi", status)))
        }
    }

    private suspend fun handleConnectionStateChanged(event: GattCallbackEvent.ConnectionStateChanged) {
        val status = event.status.toGattStatus()
        when (event.newState) {
            BluetoothProfile.STATE_CONNECTED -> handleLinkUp(status, event.status)
            BluetoothProfile.STATE_DISCONNECTED -> handleLinkDown(event.status)
        }
    }

    private suspend fun handleLinkUp(
        status: com.atruedev.kmpble.error.GattStatus,
        rawStatus: Int,
    ) {
        if (!status.isSuccess()) {
            peripheralContext.processEvent(
                ConnectionEvent.ConnectionLost(ConnectionFailed("GATT status: $status", rawStatus)),
            )
            slots.completeConnect()
            return
        }

        peripheralContext.processEvent(ConnectionEvent.LinkEstablished)
        if (!bondIfRequiredForLink()) return
        bridge.discoverServices()
    }

    /**
     * Returns false if the connection has been failed and the caller should not proceed
     * with discovery.
     */
    private suspend fun bondIfRequiredForLink(): Boolean {
        val pref = currentConnectionOptions?.bondingPreference
        if (pref != BondingPreference.Required || device.bondState == BluetoothDevice.BOND_BONDED) return true

        peripheralContext.processEvent(ConnectionEvent.BondRequired)
        val bondTimeout = quirkRegistry.resolve(BleQuirks.BondStateTimeout)
        val bonded =
            try {
                withTimeout(bondTimeout) { bondManager.createBond() }
            } catch (_: TimeoutCancellationException) {
                logEvent(
                    BleLogEvent.Error(
                        identifier,
                        "Bond state change timed out after $bondTimeout",
                        cause = null,
                    ),
                )
                false
            }

        if (!bonded) {
            peripheralContext.processEvent(
                ConnectionEvent.BondFailed(ConnectionFailed("Bonding rejected or timed out")),
            )
            slots.completeConnect()
            return false
        }

        if (quirkRegistry.resolve(BleQuirks.RefreshServicesOnBond)) {
            logEvent(
                BleLogEvent.GattOperation(
                    identifier,
                    "Quirk: refreshing GATT cache after bond",
                    uuid = null,
                    status = null,
                ),
            )
            bridge.refreshDeviceCache()
        }
        return true
    }

    private suspend fun handleLinkDown(rawStatus: Int) {
        if (peripheralContext.state.value is State.Disconnecting.Requested) {
            peripheralContext.processEvent(ConnectionEvent.ConnectionLost(OperationFailed("disconnect")))
            slots.completeDisconnect()
        } else {
            peripheralContext.processEvent(
                ConnectionEvent.ConnectionLost(ConnectionLost("Remote disconnect", rawStatus)),
            )
        }
        onDisconnectCleanup()
        slots.completeConnect()
    }

    private suspend fun handleServicesDiscovered(event: GattCallbackEvent.ServicesDiscovered) {
        val status = event.status.toGattStatus()
        if (!status.isSuccess()) {
            peripheralContext.processEvent(ConnectionEvent.DiscoveryFailed(GattError("discoverServices", status)))
            slots.completeConnect()
            slots.failDiscovery(BleException(GattError("discoverServices", status)))
            return
        }

        val discovered = event.services.map { it.toDiscoveredService() }
        peripheralContext.processEvent(ConnectionEvent.ServicesDiscovered)
        peripheralContext.updateServices(discovered)
        resubscribeObservations()
        peripheralContext.processEvent(ConnectionEvent.ConfigurationComplete)
        slots.completeConnect()
        slots.completeDiscovery(discovered)
    }

    private suspend fun resubscribeObservations() {
        for (key in observationManager.getObservationsToResubscribe()) {
            val char = findCharacteristic(key.serviceUuid, key.charUuid)
            if (char != null) enableNotifications(char) else observationManager.completeObservation(key)
        }
    }

    private suspend fun handleMtuChanged(event: GattCallbackEvent.MtuChanged) {
        if (event.status.toGattStatus().isSuccess()) peripheralContext.updateMtu(event.mtu)
        pendingOps.complete(PendingOp.MtuRequest, event.mtu)
    }

    private fun android.bluetooth.BluetoothGattService.toDiscoveredService(): DiscoveredService {
        val svcUuid = Uuid.parse(uuid.toString())
        return DiscoveredService(
            uuid = svcUuid,
            characteristics =
                characteristics.map { nativeChar ->
                    val char = nativeChar.toCharacteristic(svcUuid)
                    nativeCharMap[char] = nativeChar
                    char.descriptors.forEachIndexed { i, desc ->
                        if (i < nativeChar.descriptors.size) nativeDescMap[desc] = nativeChar.descriptors[i]
                    }
                    char
                },
        )
    }

    private fun BluetoothGattCharacteristic.toCharacteristic(serviceUuid: Uuid): Characteristic {
        val charUuid = Uuid.parse(uuid.toString())
        val props =
            Characteristic.Properties(
                read = (properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0,
                write = (properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0,
                writeWithoutResponse = (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0,
                signedWrite = (properties and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE) != 0,
                notify = (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0,
                indicate = (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0,
            )
        val descs = mutableListOf<Descriptor>()
        val char = Characteristic(serviceUuid, charUuid, props, descs)
        descriptors.forEach { descs.add(Descriptor(char, Uuid.parse(it.uuid.toString()))) }
        return char
    }

    private fun requireNativeChar(c: Characteristic): BluetoothGattCharacteristic =
        nativeCharMap[c]
            ?: throw IllegalArgumentException(
                "Characteristic not found in current GATT profile. Re-acquire from services after connect.",
            )

    private fun requireNativeDesc(d: Descriptor): BluetoothGattDescriptor =
        nativeDescMap[d] ?: throw IllegalArgumentException("Descriptor not found in current GATT profile.")

    override suspend fun read(characteristic: Characteristic): ByteArray {
        checkNotClosed()
        return peripheralContext.gattQueue.enqueue {
            val native = requireNativeChar(characteristic)
            val result =
                pendingOps.awaitGatt(PendingOp.CharacteristicRead, "read") {
                    bridge.readCharacteristic(native)
                }
            if (!result.status.isSuccess()) throw BleException(GattError("read", result.status))
            result.value
        }
    }

    override suspend fun write(
        characteristic: Characteristic,
        data: ByteArray,
        writeType: WriteType,
    ) {
        checkNotClosed()
        LargeWriteHandler.validateForWriteType(data, maximumWriteValueLength.value, writeType)

        val native = requireNativeChar(characteristic)
        val androidWriteType = writeType.toAndroidWriteType()
        val chunks = LargeWriteHandler.chunk(data, maximumWriteValueLength.value)

        peripheralContext.gattQueue.enqueue {
            for (chunk in chunks) {
                val status =
                    pendingOps.awaitGatt(PendingOp.CharacteristicWrite, "write") {
                        bridge.writeCharacteristic(native, chunk, androidWriteType)
                    }
                if (!status.isSuccess()) throw BleException(GattError("write", status))
            }
        }
    }

    private fun WriteType.toAndroidWriteType(): Int =
        when (this) {
            WriteType.WithResponse -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            WriteType.WithoutResponse -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            WriteType.Signed -> BluetoothGattCharacteristic.WRITE_TYPE_SIGNED
        }

    override fun observe(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy,
    ): Flow<Observation> {
        checkNotClosed()
        return buildObservationFlow(
            characteristic = characteristic,
            backpressure = backpressure,
            observationManager = observationManager,
            isReady = { peripheralContext.state.value is State.Connected.Ready },
            enable = ::enableNotifications,
            disable = ::disableNotificationsBestEffort,
            mapper = ObservationToObservation,
        )
    }

    override fun observeValues(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy,
    ): Flow<ByteArray> {
        checkNotClosed()
        return buildObservationFlow(
            characteristic = characteristic,
            backpressure = backpressure,
            observationManager = observationManager,
            isReady = { peripheralContext.state.value is State.Connected.Ready },
            enable = ::enableNotifications,
            disable = ::disableNotificationsBestEffort,
            mapper = ObservationToBytes,
        )
    }

    private suspend fun enableNotifications(characteristic: Characteristic) {
        val native = requireNativeChar(characteristic)
        bridge.setCharacteristicNotification(native, true)
        val cccd = native.getDescriptor(UUID.fromString(CCCD_UUID.toString())) ?: return
        val value = if (characteristic.properties.indicate) ENABLE_INDICATION_VALUE else ENABLE_NOTIFICATION_VALUE
        peripheralContext.gattQueue.enqueue {
            val status =
                pendingOps.awaitGatt(PendingOp.DescriptorWrite, "enableNotifications") {
                    bridge.writeDescriptor(cccd, value)
                }
            if (!status.isSuccess()) throw BleException(GattError("enableNotifications", status))
        }
    }

    /**
     * Best-effort CCCD disable. Failures during flow completion must not propagate
     * back into the consumer's collector.
     */
    private fun disableNotificationsBestEffort(characteristic: Characteristic) {
        if (peripheralContext.state.value !is State.Connected) return
        val native = nativeCharMap[characteristic] ?: return
        bridge.setCharacteristicNotification(native, false)
        val cccd = native.getDescriptor(UUID.fromString(CCCD_UUID.toString())) ?: return
        peripheralContext.scope.launch {
            try {
                peripheralContext.gattQueue.enqueue {
                    pendingOps.awaitGatt(PendingOp.DescriptorWrite, "disableNotifications") {
                        bridge.writeDescriptor(cccd, DISABLE_NOTIFICATION_VALUE)
                    }
                }
            } catch (_: Throwable) {
                // best-effort
            }
        }
    }

    override suspend fun readDescriptor(descriptor: Descriptor): ByteArray {
        checkNotClosed()
        return peripheralContext.gattQueue.enqueue {
            val native = requireNativeDesc(descriptor)
            val result =
                pendingOps.awaitGatt(PendingOp.DescriptorRead, "readDescriptor") {
                    bridge.readDescriptor(native)
                }
            if (!result.status.isSuccess()) throw BleException(GattError("descriptorRead", result.status))
            result.value
        }
    }

    override suspend fun writeDescriptor(
        descriptor: Descriptor,
        data: ByteArray,
    ) {
        checkNotClosed()
        peripheralContext.gattQueue.enqueue {
            val native = requireNativeDesc(descriptor)
            val status =
                pendingOps.awaitGatt(PendingOp.DescriptorWrite, "writeDescriptor") {
                    bridge.writeDescriptor(native, data)
                }
            if (!status.isSuccess()) throw BleException(GattError("descriptorWrite", status))
        }
    }

    override suspend fun readRssi(): Int {
        checkNotClosed()
        return peripheralContext.gattQueue.enqueue {
            pendingOps.awaitGatt(PendingOp.RssiRead, "readRssi") { bridge.readRemoteRssi() }
        }
    }

    override suspend fun requestMtu(mtu: Int): Int {
        checkNotClosed()
        return peripheralContext.gattQueue.enqueue {
            pendingOps.awaitGatt(PendingOp.MtuRequest, "requestMtu") { bridge.requestMtu(mtu) }
        }
    }

    private val activeL2capChannels = MutableStateFlow<List<AndroidL2capChannel>>(emptyList())

    /**
     * Open an L2CAP Connection-Oriented Channel.
     *
     * Requires Android 10 (API 29) or higher. [secure]=true uses
     * `createL2capChannel` (encrypted); false uses `createInsecureL2capChannel`.
     * Blocking socket I/O runs on [Dispatchers.IO].
     */
    override suspend fun openL2capChannel(
        psm: Int,
        secure: Boolean,
        mtu: Int?,
    ): L2capChannel {
        checkNotClosed()
        if (mtu != null) require(mtu > 0) { "mtu must be positive, was $mtu" }

        val current = state.value
        if (current !is State.Connected.Ready) {
            throw L2capException.NotConnected("Peripheral is not connected and ready (state: $current)")
        }

        logEvent(
            BleLogEvent.GattOperation(
                identifier,
                "L2CAP open PSM=$psm secure=$secure",
                uuid = null,
                status = null,
            ),
        )

        return withContext(peripheralContext.dispatcher) {
            val socket =
                withContext(Dispatchers.IO) {
                    if (secure) device.createL2capChannel(psm) else device.createInsecureL2capChannel(psm)
                }

            try {
                withContext(Dispatchers.IO) {
                    withTimeout(L2CAP_OPEN_TIMEOUT) {
                        suspendCancellableCoroutine { cont ->
                            cont.invokeOnCancellation { socket.closeQuietly() }
                            try {
                                socket.connect()
                                cont.resume(Unit)
                            } catch (e: IOException) {
                                socket.closeQuietly()
                                cont.resumeWithException(
                                    L2capException.OpenFailed(psm, "Failed to connect: ${e.message}", e),
                                )
                            }
                        }
                    }
                }

                val channel = AndroidL2capChannel(BluetoothL2capSocket(socket), psm, peripheralContext.scope)
                activeL2capChannels.update { it + channel }

                peripheralContext.scope.launch {
                    try {
                        channel.awaitClosed()
                    } finally {
                        activeL2capChannels.update { it - channel }
                    }
                }

                logEvent(
                    BleLogEvent.GattOperation(
                        identifier,
                        "L2CAP opened PSM=$psm mtu=${channel.mtu}",
                        uuid = null,
                        status = null,
                    ),
                )

                channel
            } catch (e: L2capException) {
                throw e
            } catch (e: CancellationException) {
                socket.closeQuietly()
                throw L2capException.OpenFailed(psm, "Connection timed out", e)
            } catch (e: IOException) {
                socket.closeQuietly()
                throw L2capException.OpenFailed(psm, e.message ?: "Unknown error", e)
            } catch (e: SecurityException) {
                socket.closeQuietly()
                throw L2capException.OpenFailed(psm, "Missing BLUETOOTH_CONNECT permission", e)
            }
        }
    }

    private fun android.bluetooth.BluetoothSocket.closeQuietly() {
        try {
            close()
        } catch (_: IOException) {
        }
    }

    private fun closeL2capChannels() {
        val channels = activeL2capChannels.getAndUpdate { emptyList() }
        if (channels.isNotEmpty()) {
            logEvent(
                BleLogEvent.GattOperation(
                    identifier,
                    "L2CAP closing ${channels.size} channel(s)",
                    uuid = null,
                    status = null,
                ),
            )
        }
        channels.forEach { it.close() }
    }

    private fun onDisconnectCleanup() {
        nativeCharMap.clear()
        nativeDescMap.clear()
        closeL2capChannels()
        observationManager.onDisconnect()
        pendingOps.cancelAll(NotConnectedException())
    }

    private fun checkNotClosed() {
        check(!closed) { "Peripheral is closed" }
    }

    private companion object {
        val L2CAP_OPEN_TIMEOUT = 30.seconds
        val DISCONNECT_TIMEOUT = 5.seconds
        val SERVICE_DISCOVERY_TIMEOUT = 10.seconds
    }
}
