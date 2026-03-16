@file:SuppressLint("MissingPermission")

package io.github.garyquinn.kmpble.peripheral

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import io.github.garyquinn.kmpble.Identifier
import io.github.garyquinn.kmpble.connection.ConnectionOptions
import io.github.garyquinn.kmpble.connection.State
import io.github.garyquinn.kmpble.connection.internal.ConnectionEvent
import io.github.garyquinn.kmpble.error.BleError
import io.github.garyquinn.kmpble.gatt.BackpressureStrategy
import io.github.garyquinn.kmpble.gatt.Characteristic
import io.github.garyquinn.kmpble.gatt.Descriptor
import io.github.garyquinn.kmpble.gatt.DiscoveredService
import io.github.garyquinn.kmpble.gatt.Observation
import io.github.garyquinn.kmpble.gatt.WriteType
import io.github.garyquinn.kmpble.gatt.internal.CCCD_UUID
import io.github.garyquinn.kmpble.gatt.internal.DISABLE_NOTIFICATION_VALUE
import io.github.garyquinn.kmpble.gatt.internal.ENABLE_INDICATION_VALUE
import io.github.garyquinn.kmpble.gatt.internal.ENABLE_NOTIFICATION_VALUE
import io.github.garyquinn.kmpble.gatt.internal.GattResult
import io.github.garyquinn.kmpble.gatt.internal.LargeWriteHandler
import io.github.garyquinn.kmpble.gatt.internal.ObservationManager
import io.github.garyquinn.kmpble.gatt.internal.PendingOperations
import io.github.garyquinn.kmpble.gatt.internal.applyBackpressure
import io.github.garyquinn.kmpble.peripheral.internal.PeripheralContext
import io.github.garyquinn.kmpble.peripheral.internal.PeripheralRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
public class AndroidPeripheral(
    device: BluetoothDevice,
    context: Context,
) : Peripheral {

    override val identifier: Identifier = Identifier(device.address)
    private val peripheralContext = PeripheralContext(identifier)
    private val bridge = AndroidGattBridge(device, context)

    private var connectionComplete: CompletableDeferred<Unit>? = null
    private var discoveryComplete: CompletableDeferred<List<DiscoveredService>>? = null
    private var disconnectComplete: CompletableDeferred<Unit>? = null
    private val pendingOps = PendingOperations()
    private val observationManager = ObservationManager()

    // Map our Characteristic objects back to native BluetoothGattCharacteristic
    private val nativeCharMap = mutableMapOf<Characteristic, BluetoothGattCharacteristic>()
    private val nativeDescMap = mutableMapOf<Descriptor, BluetoothGattDescriptor>()

    private val bondManager = AndroidBondManager(device, context, peripheralContext)

    override val state: StateFlow<State> get() = peripheralContext.state
    override val bondState: StateFlow<io.github.garyquinn.kmpble.bonding.BondState> get() = bondManager.bondState
    override val services: StateFlow<List<DiscoveredService>?> get() = peripheralContext.services
    override val maximumWriteValueLength: StateFlow<Int> get() = peripheralContext.maximumWriteValueLength

    private var closed = false
    private val reconnectionHandler = io.github.garyquinn.kmpble.connection.internal.ReconnectionHandler(
        scope = peripheralContext.scope,
        stateFlow = peripheralContext.state,
        connectAction = { opts -> connect(opts.copy(reconnectionStrategy = io.github.garyquinn.kmpble.connection.ReconnectionStrategy.None)) },
    )

    init {
        bridge.onEvent = { event -> handleGattEvent(event) }
    }

    override suspend fun connect(options: ConnectionOptions) {
        checkNotClosed()
        reconnectionHandler.start(options)
        bondManager.start()
        withContext(peripheralContext.dispatcher) {
            peripheralContext.processEvent(ConnectionEvent.ConnectRequested)
            peripheralContext.gattQueue.start()

            connectionComplete = CompletableDeferred()

            val gatt = bridge.connect(options)
            if (gatt == null) {
                peripheralContext.processEvent(
                    ConnectionEvent.ConnectionLost(BleError.ConnectionFailed("connectGatt returned null"))
                )
                return@withContext
            }

            try {
                withTimeout(options.timeout) {
                    connectionComplete!!.await()
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                bridge.disconnect()
                bridge.releaseGatt()
                peripheralContext.processEvent(
                    ConnectionEvent.ConnectionLost(BleError.ConnectionFailed("Connection timeout"))
                )
            } finally {
                connectionComplete = null
            }
        }
    }

    override suspend fun disconnect() {
        checkNotClosed()
        reconnectionHandler.stop()
        withContext(peripheralContext.dispatcher) {
            if (peripheralContext.state.value is State.Disconnected) return@withContext
            peripheralContext.processEvent(ConnectionEvent.DisconnectRequested)
            disconnectComplete = CompletableDeferred()
            bridge.disconnect()

            try {
                withTimeout(5_000) { disconnectComplete!!.await() }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                // Force transition if OS didn't confirm disconnect
                peripheralContext.processEvent(
                    ConnectionEvent.ConnectionLost(BleError.OperationFailed("Disconnect timeout"))
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
        bridge.close()
        peripheralContext.close()
        PeripheralRegistry.remove(identifier)
    }

    @io.github.garyquinn.kmpble.ExperimentalBleApi
    public suspend fun removeBond(): io.github.garyquinn.kmpble.bonding.BondRemovalResult {
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
                    bridge.discoverServices()
                } else {
                    peripheralContext.processEvent(
                        ConnectionEvent.ConnectionLost(BleError.ConnectionFailed("GATT status: $status", event.status))
                    )
                    connectionComplete?.complete(Unit)
                }
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                if (peripheralContext.state.value is State.Disconnecting.Requested) {
                    peripheralContext.processEvent(
                        ConnectionEvent.ConnectionLost(BleError.OperationFailed("disconnect"))
                    )
                    disconnectComplete?.complete(Unit)
                } else {
                    peripheralContext.processEvent(
                        ConnectionEvent.ConnectionLost(BleError.ConnectionLost("Remote disconnect", event.status))
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

            // MTU negotiation or skip to ready
            peripheralContext.processEvent(ConnectionEvent.ConfigurationComplete)
            connectionComplete?.complete(Unit)
            discoveryComplete?.complete(discovered)
        } else {
            peripheralContext.processEvent(
                ConnectionEvent.DiscoveryFailed(BleError.GattError("discoverServices", status))
            )
            connectionComplete?.complete(Unit)
            discoveryComplete?.completeExceptionally(
                IllegalStateException("Service discovery failed: $status")
            )
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
                // Map descriptors
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
        // Single queue entry for all chunks — prevents interleaving with other ops
        peripheralContext.gattQueue.enqueue {
            for (chunk in chunks) {
                val deferred = CompletableDeferred<io.github.garyquinn.kmpble.error.GattStatus>()
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
        check(peripheralContext.state.value is State.Connected) { "Peripheral is not connected" }
        val dataFlow = observationManager.getOrCreateFlow(characteristic)
        enableNotifications(characteristic)
        return dataFlow
            .map<ByteArray, Observation> { Observation.Value(it) }
            .applyBackpressure(backpressure)
            .onCompletion { disableNotifications(characteristic) }
    }

    override fun observeValues(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy,
    ): Flow<ByteArray> {
        checkNotClosed()
        check(peripheralContext.state.value is State.Connected) { "Peripheral is not connected" }
        val dataFlow = observationManager.getOrCreateFlow(characteristic)
        enableNotifications(characteristic)
        return dataFlow
            .applyBackpressure(backpressure)
            .onCompletion { disableNotifications(characteristic) }
    }

    private fun enableNotifications(characteristic: Characteristic) {
        val native = requireNativeChar(characteristic)
        bridge.setCharacteristicNotification(native, true)
        // Write CCCD
        val cccd = native.getDescriptor(java.util.UUID.fromString(CCCD_UUID.toString()))
        if (cccd != null) {
            val value = if (characteristic.properties.indicate) ENABLE_INDICATION_VALUE else ENABLE_NOTIFICATION_VALUE
            peripheralContext.scope.launch {
                peripheralContext.gattQueue.enqueue {
                    val deferred = CompletableDeferred<io.github.garyquinn.kmpble.error.GattStatus>()
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
                    val deferred = CompletableDeferred<io.github.garyquinn.kmpble.error.GattStatus>()
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
            val deferred = CompletableDeferred<io.github.garyquinn.kmpble.error.GattStatus>()
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

    private fun onDisconnectCleanup() {
        nativeCharMap.clear()
        nativeDescMap.clear()
        observationManager.clear()
        pendingOps.cancelAll(io.github.garyquinn.kmpble.gatt.internal.NotConnectedException())
    }

    private fun checkNotClosed() {
        check(!closed) { "Peripheral is closed" }
    }
}
