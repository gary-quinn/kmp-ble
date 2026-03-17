@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.peripheral

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.bonding.BondState
import com.atruedev.kmpble.connection.BondingPreference
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.connection.internal.ConnectionEvent
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
import com.atruedev.kmpble.gatt.internal.ObservationEvent
import com.atruedev.kmpble.gatt.internal.ObservationManager
import com.atruedev.kmpble.gatt.internal.PendingOperations
import com.atruedev.kmpble.gatt.internal.applyBackpressure
import com.atruedev.kmpble.internal.DeviceInfo
import com.atruedev.kmpble.internal.DeviceQuirks
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import com.atruedev.kmpble.peripheral.internal.PeripheralContext
import com.atruedev.kmpble.peripheral.internal.PeripheralRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * @param quirks device-specific BLE workarounds. Defaults to the current device's quirks;
 *   pass a custom instance in tests to simulate OEM-specific behavior.
 */
@OptIn(ExperimentalUuidApi::class)
public class AndroidPeripheral internal constructor(
    private val device: BluetoothDevice,
    context: Context,
    internal val quirks: DeviceQuirks,
) : Peripheral {

    public constructor(device: BluetoothDevice, context: Context) :
        this(device, context, DeviceQuirks(DeviceInfo.current()))

    override val identifier: Identifier = Identifier(device.address)
    private val peripheralContext = PeripheralContext(identifier)
    private val bridge = AndroidGattBridge(device, context)

    private var connectionComplete: CompletableDeferred<Unit>? = null
    private var discoveryComplete: CompletableDeferred<List<DiscoveredService>>? = null
    private var disconnectComplete: CompletableDeferred<Unit>? = null
    private val pendingOps = PendingOperations()
    private val observationManager = ObservationManager()

    private val nativeCharMap = mutableMapOf<Characteristic, BluetoothGattCharacteristic>()
    private val nativeDescMap = mutableMapOf<Descriptor, BluetoothGattDescriptor>()

    private val bondManager = AndroidBondManager(device, context, peripheralContext)

    override val state: StateFlow<State> get() = peripheralContext.state
    override val bondState: StateFlow<BondState> get() = bondManager.bondState
    override val services: StateFlow<List<DiscoveredService>?> get() = peripheralContext.services
    override val maximumWriteValueLength: StateFlow<Int> get() = peripheralContext.maximumWriteValueLength

    private var closed = false
    private var currentConnectionOptions: ConnectionOptions? = null
    private val reconnectionHandler = com.atruedev.kmpble.connection.internal.ReconnectionHandler(
        scope = peripheralContext.scope,
        stateFlow = peripheralContext.state,
        connectAction = { opts -> connect(opts.copy(reconnectionStrategy = com.atruedev.kmpble.connection.ReconnectionStrategy.None)) },
        onMaxAttemptsExhausted = { observationManager.onPermanentDisconnect() },
    )

    init {
        bridge.onEvent = { event -> handleGattEvent(event) }
        logEvent(BleLogEvent.GattOperation(identifier, "DeviceQuirks: ${quirks.describe()}", uuid = null, status = null))
    }

    override suspend fun connect(options: ConnectionOptions) {
        checkNotClosed()
        currentConnectionOptions = options
        reconnectionHandler.start(options)
        bondManager.start()

        withContext(peripheralContext.dispatcher) {
            ensureBondedIfRequired(options)
            connectWithRetry(options)
        }
    }

    /**
     * Samsung quirk: some Galaxy devices require bonding BEFORE calling connectGatt(),
     * otherwise the connection fails silently or returns GATT 133.
     */
    private suspend fun ensureBondedIfRequired(options: ConnectionOptions) {
        if (!quirks.shouldBondBeforeConnect()) return
        if (peripheralContext.bondState.value != BondState.NotBonded) return
        if (options.bondingPreference == BondingPreference.None) return

        logEvent(BleLogEvent.BondEvent(identifier, "Quirk: bond-before-connect initiated"))
        try {
            withTimeout(quirks.bondStateChangeTimeout()) {
                bondManager.createBond()
            }
            logEvent(BleLogEvent.BondEvent(identifier, "Quirk: bond-before-connect succeeded"))
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            logEvent(BleLogEvent.Error(
                identifier,
                "Quirk: bond-before-connect timed out after ${quirks.bondStateChangeTimeout()}, proceeding with connection",
                cause = null,
            ))
        }
    }

    /**
     * Attempts GATT connection with device-specific retry behavior.
     *
     * Pixel devices commonly return GATT error 133 on the first attempt — a retry with
     * a short delay (1–1.5s) typically succeeds. The retry count and delay are sourced
     * from [DeviceQuirks] so each OEM gets appropriate handling.
     *
     * The effective timeout is `max(options.timeout, quirks.connectionTimeout())` so that
     * user-configured values are respected while still accommodating OEMs that need longer
     * timeouts (e.g. Huawei at 35s vs the 30s default).
     */
    private suspend fun connectWithRetry(options: ConnectionOptions) {
        val maxAttempts = quirks.connectGattRetryCount()
        val retryDelay = quirks.gattConnectionRetryDelay()
        val timeout = maxOf(options.timeout, quirks.connectionTimeout())

        repeat(maxAttempts) { attempt ->
            if (attempt > 0) {
                logEvent(BleLogEvent.GattOperation(
                    identifier, "Connection retry ${attempt + 1}/$maxAttempts after ${retryDelay}",
                    uuid = null, status = null,
                ))
            }

            peripheralContext.processEvent(ConnectionEvent.ConnectRequested)
            peripheralContext.gattQueue.start()

            connectionComplete = CompletableDeferred()

            val gatt = bridge.connect(options)
            if (gatt == null) {
                peripheralContext.processEvent(
                    ConnectionEvent.ConnectionLost(ConnectionFailed("connectGatt returned null"))
                )
                connectionComplete = null
                if (attempt < maxAttempts - 1) {
                    delay(retryDelay)
                }
                return@repeat
            }

            try {
                withTimeout(timeout) {
                    connectionComplete!!.await()
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                bridge.disconnect()
                bridge.releaseGatt()
                peripheralContext.processEvent(
                    ConnectionEvent.ConnectionLost(ConnectionFailed("Connection timeout after $timeout"))
                )
            } finally {
                connectionComplete = null
            }

            if (peripheralContext.state.value is State.Connected) {
                return
            }

            if (attempt < maxAttempts - 1) {
                bridge.releaseGatt()
                delay(retryDelay)
            }
        }
    }

    override suspend fun disconnect() {
        checkNotClosed()
        reconnectionHandler.stop()
        bondManager.stop()
        withContext(peripheralContext.dispatcher) {
            if (peripheralContext.state.value is State.Disconnected) return@withContext
            peripheralContext.processEvent(ConnectionEvent.DisconnectRequested)
            disconnectComplete = CompletableDeferred()
            bridge.disconnect()

            try {
                withTimeout(5_000) { disconnectComplete!!.await() }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                peripheralContext.processEvent(
                    ConnectionEvent.ConnectionLost(OperationFailed("Disconnect timeout"))
                )
            } finally {
                disconnectComplete = null
                bridge.releaseGatt()
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        reconnectionHandler.stop()
        bondManager.stop()
        observationManager.clear()
        bridge.close()
        peripheralContext.close()
        PeripheralRegistry.remove(identifier)
    }

    @com.atruedev.kmpble.ExperimentalBleApi
    override fun removeBond(): com.atruedev.kmpble.bonding.BondRemovalResult {
        checkNotClosed()
        return bondManager.removeBond()
    }

    override suspend fun refreshServices(): List<DiscoveredService> {
        checkNotClosed()
        return withContext(peripheralContext.dispatcher) {
            discoveryComplete = CompletableDeferred()
            if (!bridge.discoverServices()) {
                discoveryComplete = null
                throw IllegalStateException("discoverServices() returned false")
            }
            try {
                withTimeout(10_000) { discoveryComplete!!.await() }
            } finally {
                discoveryComplete = null
            }
        }
    }

    override fun findCharacteristic(serviceUuid: Uuid, characteristicUuid: Uuid): Characteristic? {
        return services.value
            ?.firstOrNull { it.uuid == serviceUuid }
            ?.characteristics
            ?.firstOrNull { it.uuid == characteristicUuid }
    }

    override fun findDescriptor(
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
        descriptorUuid: Uuid,
    ): Descriptor? {
        val char = findCharacteristic(serviceUuid, characteristicUuid) ?: return null
        return char.descriptors.firstOrNull { it.uuid == descriptorUuid }
    }

    // --- GATT callback handling (runs on HandlerThread, bridges to PeripheralContext) ---

    private fun handleGattEvent(event: GattCallbackEvent) {
        peripheralContext.scope.launch {
            when (event) {
                is GattCallbackEvent.ConnectionStateChanged -> handleConnectionStateChanged(event)
                is GattCallbackEvent.ServicesDiscovered -> handleServicesDiscovered(event)
                is GattCallbackEvent.MtuChanged -> handleMtuChanged(event)
                is GattCallbackEvent.CharacteristicRead -> {
                    val status = event.status.toGattStatus()
                    pendingOps.characteristicRead?.complete(GattResult(event.value, status))
                    pendingOps.characteristicRead = null
                }
                is GattCallbackEvent.CharacteristicWrite -> {
                    pendingOps.characteristicWrite?.complete(event.status.toGattStatus())
                    pendingOps.characteristicWrite = null
                }
                is GattCallbackEvent.CharacteristicChanged -> {
                    val uuid = Uuid.parse(event.characteristic.uuid.toString())
                    val serviceUuid = Uuid.parse(event.characteristic.service.uuid.toString())
                    observationManager.emitByUuid(serviceUuid, uuid, event.value)
                }
                is GattCallbackEvent.DescriptorRead -> {
                    val status = event.status.toGattStatus()
                    pendingOps.descriptorRead?.complete(GattResult(event.value, status))
                    pendingOps.descriptorRead = null
                }
                is GattCallbackEvent.DescriptorWrite -> {
                    pendingOps.descriptorWrite?.complete(event.status.toGattStatus())
                    pendingOps.descriptorWrite = null
                }
                is GattCallbackEvent.ReadRemoteRssi -> {
                    if (event.status.toGattStatus().isSuccess()) {
                        pendingOps.rssiRead?.complete(event.rssi)
                    } else {
                        pendingOps.rssiRead?.completeExceptionally(
                            Exception("readRssi failed: ${event.status.toGattStatus()}")
                        )
                    }
                    pendingOps.rssiRead = null
                }
            }
        }
    }

    private suspend fun handleConnectionStateChanged(event: GattCallbackEvent.ConnectionStateChanged) {
        val status = event.status.toGattStatus()
        when (event.newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                if (status.isSuccess()) {
                    peripheralContext.processEvent(ConnectionEvent.LinkEstablished)
                    val bondPref = currentConnectionOptions?.bondingPreference
                    if (bondPref == BondingPreference.Required
                        && device.bondState != BluetoothDevice.BOND_BONDED
                    ) {
                        peripheralContext.processEvent(ConnectionEvent.BondRequired)
                        val bonded = try {
                            withTimeout(quirks.bondStateChangeTimeout()) {
                                bondManager.createBond()
                            }
                        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                            logEvent(BleLogEvent.Error(
                                identifier,
                                "Bond state change timed out after ${quirks.bondStateChangeTimeout()}",
                                cause = null,
                            ))
                            false
                        }
                        if (!bonded) {
                            peripheralContext.processEvent(
                                ConnectionEvent.BondFailed(ConnectionFailed("Bonding rejected or timed out"))
                            )
                            connectionComplete?.complete(Unit)
                            return
                        }
                        if (quirks.shouldRefreshServicesOnBond()) {
                            logEvent(BleLogEvent.GattOperation(
                                identifier, "Quirk: refreshing GATT cache after bond",
                                uuid = null, status = null,
                            ))
                            bridge.refreshDeviceCache()
                        }
                    }
                    bridge.discoverServices()
                } else {
                    peripheralContext.processEvent(
                        ConnectionEvent.ConnectionLost(ConnectionFailed("GATT status: $status", event.status))
                    )
                    connectionComplete?.complete(Unit)
                }
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                if (peripheralContext.state.value is State.Disconnecting.Requested) {
                    peripheralContext.processEvent(
                        ConnectionEvent.ConnectionLost(OperationFailed("disconnect"))
                    )
                    disconnectComplete?.complete(Unit)
                } else {
                    peripheralContext.processEvent(
                        ConnectionEvent.ConnectionLost(ConnectionLost("Remote disconnect", event.status))
                    )
                }
                onDisconnectCleanup()
                connectionComplete?.complete(Unit)
            }
        }
    }

    private suspend fun handleServicesDiscovered(event: GattCallbackEvent.ServicesDiscovered) {
        val status = event.status.toGattStatus()
        if (status.isSuccess()) {
            val discovered = event.services.map { it.toDiscoveredService() }
            peripheralContext.processEvent(ConnectionEvent.ServicesDiscovered)
            peripheralContext.updateServices(discovered)

            resubscribeObservations()

            peripheralContext.processEvent(ConnectionEvent.ConfigurationComplete)
            connectionComplete?.complete(Unit)
            discoveryComplete?.complete(discovered)
        } else {
            peripheralContext.processEvent(
                ConnectionEvent.DiscoveryFailed(GattError("discoverServices", status))
            )
            connectionComplete?.complete(Unit)
            discoveryComplete?.completeExceptionally(
                IllegalStateException("Service discovery failed: $status")
            )
        }
    }

    private suspend fun resubscribeObservations() {
        val toResubscribe = observationManager.getObservationsToResubscribe()
        for (key in toResubscribe) {
            val char = findCharacteristic(key.serviceUuid, key.charUuid)
            if (char != null) {
                enableNotifications(char)
            } else {
                observationManager.completeObservation(key)
            }
        }
    }

    private suspend fun handleMtuChanged(event: GattCallbackEvent.MtuChanged) {
        if (event.status.toGattStatus().isSuccess()) {
            peripheralContext.updateMtu(event.mtu)
        }
        pendingOps.mtuRequest?.complete(event.mtu)
        pendingOps.mtuRequest = null
    }

    // --- Service parsing ---

    private fun android.bluetooth.BluetoothGattService.toDiscoveredService(): DiscoveredService {
        val svcUuid = Uuid.parse(uuid.toString())
        return DiscoveredService(
            uuid = svcUuid,
            characteristics = characteristics.map { nativeChar ->
                val char = nativeChar.toCharacteristic(svcUuid)
                nativeCharMap[char] = nativeChar
                char.descriptors.forEachIndexed { i, desc ->
                    if (i < nativeChar.descriptors.size) {
                        nativeDescMap[desc] = nativeChar.descriptors[i]
                    }
                }
                char
            },
        )
    }

    private fun BluetoothGattCharacteristic.toCharacteristic(serviceUuid: Uuid): Characteristic {
        val charUuid = Uuid.parse(uuid.toString())
        val props = Characteristic.Properties(
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

    private fun requireNativeChar(characteristic: Characteristic): BluetoothGattCharacteristic {
        return nativeCharMap[characteristic]
            ?: throw IllegalArgumentException("Characteristic not found in current GATT profile. Re-acquire from services after connect.")
    }

    private fun requireNativeDesc(descriptor: Descriptor): BluetoothGattDescriptor {
        return nativeDescMap[descriptor]
            ?: throw IllegalArgumentException("Descriptor not found in current GATT profile.")
    }

    // --- GATT Operations ---

    override suspend fun read(characteristic: Characteristic): ByteArray {
        checkNotClosed()
        return peripheralContext.gattQueue.enqueue {
            val native = requireNativeChar(characteristic)
            val deferred = CompletableDeferred<GattResult>()
            pendingOps.characteristicRead = deferred
            if (!bridge.readCharacteristic(native)) {
                pendingOps.characteristicRead = null
                throw IllegalStateException("readCharacteristic initiation failed")
            }
            val result = deferred.await()
            if (!result.status.isSuccess()) throw Exception("Read failed: ${result.status}")
            result.value
        }
    }

    override suspend fun write(characteristic: Characteristic, data: ByteArray, writeType: WriteType) {
        checkNotClosed()
        LargeWriteHandler.validateForWriteType(data, maximumWriteValueLength.value, writeType)

        val native = requireNativeChar(characteristic)
        val androidWriteType = when (writeType) {
            WriteType.WithResponse -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            WriteType.WithoutResponse -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            WriteType.Signed -> BluetoothGattCharacteristic.WRITE_TYPE_SIGNED
        }

        val chunks = LargeWriteHandler.chunk(data, maximumWriteValueLength.value)
        peripheralContext.gattQueue.enqueue {
            for (chunk in chunks) {
                val deferred = CompletableDeferred<com.atruedev.kmpble.error.GattStatus>()
                pendingOps.characteristicWrite = deferred
                if (!bridge.writeCharacteristic(native, chunk, androidWriteType)) {
                    pendingOps.characteristicWrite = null
                    throw IllegalStateException("writeCharacteristic initiation failed")
                }
                val status = deferred.await()
                if (!status.isSuccess()) throw Exception("Write failed: $status")
            }
        }
    }

    override fun observe(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy,
    ): Flow<Observation> {
        checkNotClosed()
        val serviceUuid = characteristic.serviceUuid
        val charUuid = characteristic.uuid

        return kotlinx.coroutines.flow.flow {
            val eventFlow = observationManager.subscribe(serviceUuid, charUuid, backpressure)
            eventFlow.collect { event ->
                when (event) {
                    is ObservationEvent.Value -> emit(Observation.Value(event.data))
                    is ObservationEvent.Disconnected -> emit(Observation.Disconnected)
                    is ObservationEvent.PermanentlyDisconnected -> emit(Observation.Disconnected)
                }
            }
        }
            .onStart {
                if (peripheralContext.state.value is State.Connected.Ready) {
                    enableNotifications(characteristic)
                }
            }
            .applyBackpressure(backpressure)
            .onCompletion {
                val wasLastCollector = observationManager.unsubscribe(serviceUuid, charUuid)
                if (wasLastCollector) {
                    disableNotifications(characteristic)
                }
            }
    }

    override fun observeValues(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy,
    ): Flow<ByteArray> {
        checkNotClosed()
        val serviceUuid = characteristic.serviceUuid
        val charUuid = characteristic.uuid

        return kotlinx.coroutines.flow.flow {
            val eventFlow = observationManager.subscribe(serviceUuid, charUuid, backpressure)
            eventFlow.collect { event ->
                when (event) {
                    is ObservationEvent.Value -> emit(event.data)
                    is ObservationEvent.Disconnected -> {
                        // Transparent reconnection — no emission during disconnect
                    }
                    is ObservationEvent.PermanentlyDisconnected -> {
                        // Flow completes normally, no emission (transformWhile ends the flow)
                    }
                }
            }
        }
            .onStart {
                if (peripheralContext.state.value is State.Connected.Ready) {
                    enableNotifications(characteristic)
                }
            }
            .applyBackpressure(backpressure)
            .onCompletion {
                val wasLastCollector = observationManager.unsubscribe(serviceUuid, charUuid)
                if (wasLastCollector) {
                    disableNotifications(characteristic)
                }
            }
    }

    private fun enableNotifications(characteristic: Characteristic) {
        val native = requireNativeChar(characteristic)
        bridge.setCharacteristicNotification(native, true)
        val cccd = native.getDescriptor(java.util.UUID.fromString(CCCD_UUID.toString()))
        if (cccd != null) {
            val value = if (characteristic.properties.indicate) ENABLE_INDICATION_VALUE else ENABLE_NOTIFICATION_VALUE
            peripheralContext.scope.launch {
                peripheralContext.gattQueue.enqueue {
                    val deferred = CompletableDeferred<com.atruedev.kmpble.error.GattStatus>()
                    pendingOps.descriptorWrite = deferred
                    bridge.writeDescriptor(cccd, value)
                    deferred.await()
                }
            }
        }
    }

    private fun disableNotifications(characteristic: Characteristic) {
        if (peripheralContext.state.value !is State.Connected) return
        val native = nativeCharMap[characteristic] ?: return
        bridge.setCharacteristicNotification(native, false)
        val cccd = native.getDescriptor(java.util.UUID.fromString(CCCD_UUID.toString())) ?: return
        peripheralContext.scope.launch {
            try {
                peripheralContext.gattQueue.enqueue {
                    val deferred = CompletableDeferred<com.atruedev.kmpble.error.GattStatus>()
                    pendingOps.descriptorWrite = deferred
                    bridge.writeDescriptor(cccd, DISABLE_NOTIFICATION_VALUE)
                    deferred.await()
                }
            } catch (_: Throwable) {
                // Best-effort — don't fail the flow completion
            }
        }
    }

    override suspend fun readDescriptor(descriptor: Descriptor): ByteArray {
        checkNotClosed()
        return peripheralContext.gattQueue.enqueue {
            val native = requireNativeDesc(descriptor)
            val deferred = CompletableDeferred<GattResult>()
            pendingOps.descriptorRead = deferred
            if (!bridge.readDescriptor(native)) {
                pendingOps.descriptorRead = null
                throw IllegalStateException("readDescriptor initiation failed")
            }
            val result = deferred.await()
            if (!result.status.isSuccess()) throw Exception("Descriptor read failed: ${result.status}")
            result.value
        }
    }

    override suspend fun writeDescriptor(descriptor: Descriptor, data: ByteArray) {
        checkNotClosed()
        peripheralContext.gattQueue.enqueue {
            val native = requireNativeDesc(descriptor)
            val deferred = CompletableDeferred<com.atruedev.kmpble.error.GattStatus>()
            pendingOps.descriptorWrite = deferred
            if (!bridge.writeDescriptor(native, data)) {
                pendingOps.descriptorWrite = null
                throw IllegalStateException("writeDescriptor initiation failed")
            }
            val status = deferred.await()
            if (!status.isSuccess()) throw Exception("Descriptor write failed: $status")
        }
    }

    override suspend fun readRssi(): Int {
        checkNotClosed()
        return peripheralContext.gattQueue.enqueue {
            val deferred = CompletableDeferred<Int>()
            pendingOps.rssiRead = deferred
            if (!bridge.readRemoteRssi()) {
                pendingOps.rssiRead = null
                throw IllegalStateException("readRemoteRssi initiation failed")
            }
            deferred.await()
        }
    }

    override suspend fun requestMtu(mtu: Int): Int {
        checkNotClosed()
        return peripheralContext.gattQueue.enqueue {
            val deferred = CompletableDeferred<Int>()
            pendingOps.mtuRequest = deferred
            if (!bridge.requestMtu(mtu)) {
                pendingOps.mtuRequest = null
                throw IllegalStateException("requestMtu initiation failed")
            }
            deferred.await()
        }
    }

    // --- L2CAP ---

    override suspend fun openL2capChannel(psm: Int, secure: Boolean): L2capChannel {
        TODO("Android L2CAP implementation in next PR")
    }

    private fun onDisconnectCleanup() {
        nativeCharMap.clear()
        nativeDescMap.clear()
        observationManager.onDisconnect()
        pendingOps.cancelAll(com.atruedev.kmpble.gatt.internal.NotConnectedException())
    }

    private fun checkNotClosed() {
        check(!closed) { "Peripheral is closed" }
    }
}
