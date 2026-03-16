package io.github.garyquinn.kmpble.peripheral

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
import io.github.garyquinn.kmpble.gatt.internal.GattResult
import io.github.garyquinn.kmpble.gatt.internal.LargeWriteHandler
import io.github.garyquinn.kmpble.gatt.internal.ObservationManager
import io.github.garyquinn.kmpble.gatt.internal.PendingOperations
import io.github.garyquinn.kmpble.gatt.internal.applyBackpressure
import io.github.garyquinn.kmpble.internal.CentralManagerProvider
import io.github.garyquinn.kmpble.peripheral.internal.PeripheralContext
import io.github.garyquinn.kmpble.peripheral.internal.PeripheralRegistry
import io.github.garyquinn.kmpble.scanner.uuidFrom
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicPropertyAuthenticatedSignedWrites
import platform.CoreBluetooth.CBCharacteristicPropertyIndicate
import platform.CoreBluetooth.CBCharacteristicPropertyNotify
import platform.CoreBluetooth.CBCharacteristicPropertyRead
import platform.CoreBluetooth.CBCharacteristicPropertyWrite
import platform.CoreBluetooth.CBCharacteristicPropertyWriteWithoutResponse
import platform.CoreBluetooth.CBDescriptor
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBService
import io.github.garyquinn.kmpble.bleDataFromNSData
import platform.Foundation.NSData
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
public class IosPeripheral(
    private val cbPeripheral: CBPeripheral,
) : Peripheral {

    override val identifier: Identifier = Identifier(cbPeripheral.identifier.UUIDString)
    private val peripheralContext = PeripheralContext(identifier)
    private val bridge = ApplePeripheralBridge(cbPeripheral)
    private val centralDelegate = CentralManagerProvider.scanDelegate

    private var connectionComplete: CompletableDeferred<Unit>? = null
    private var discoveryComplete: CompletableDeferred<List<DiscoveredService>>? = null
    private var disconnectComplete: CompletableDeferred<Unit>? = null
    private val pendingOps = PendingOperations()
    private val observationManager = ObservationManager()

    // Map our Characteristic/Descriptor objects to native CBCharacteristic/CBDescriptor
    private val nativeCharMap = mutableMapOf<Characteristic, CBCharacteristic>()
    private val nativeDescMap = mutableMapOf<Descriptor, CBDescriptor>()

    override val state: StateFlow<State> get() = peripheralContext.state
    override val bondState: StateFlow<io.github.garyquinn.kmpble.bonding.BondState> get() = peripheralContext.bondState
    override val services: StateFlow<List<DiscoveredService>?> get() = peripheralContext.services
    override val maximumWriteValueLength: StateFlow<Int> get() = peripheralContext.maximumWriteValueLength

    private var closed = false
    // Safe: all callbacks dispatched to peripheralContext.scope (limitedParallelism(1))
    private var pendingCharacteristicDiscovery = 0
    private val reconnectionHandler = io.github.garyquinn.kmpble.connection.internal.ReconnectionHandler(
        scope = peripheralContext.scope,
        stateFlow = peripheralContext.state,
        connectAction = { opts -> connect(opts.copy(reconnectionStrategy = io.github.garyquinn.kmpble.connection.ReconnectionStrategy.None)) },
    )

    init {
        bridge.onEvent = { event -> handleBridgeEvent(event) }
        centralDelegate.registerConnectionCallback(identifier.value) { connected, error ->
            handleConnectionCallback(connected, error)
        }
    }

    override suspend fun connect(options: ConnectionOptions) {
        checkNotClosed()
        reconnectionHandler.start(options)
        withContext(peripheralContext.dispatcher) {
            peripheralContext.processEvent(ConnectionEvent.ConnectRequested)
            peripheralContext.gattQueue.start()

            connectionComplete = CompletableDeferred()
            bridge.connect()

            // K/N limitation: didFailToConnectPeripheral shares signature with
            // didDisconnectPeripheral — only one override possible. Poll CBPeripheral.state
            // to detect connection failure early instead of waiting for the full timeout.
            val failureDetector = peripheralContext.scope.launch {
                while (connectionComplete?.isCompleted == false) {
                    kotlinx.coroutines.delay(500)
                    if (cbPeripheral.state == platform.CoreBluetooth.CBPeripheralStateDisconnected) {
                        val deferred = connectionComplete ?: break
                        if (!deferred.isCompleted) {
                            peripheralContext.processEvent(
                                ConnectionEvent.ConnectionLost(
                                    BleError.ConnectionFailed("Connection failed (peripheral disconnected)")
                                )
                            )
                            deferred.complete(Unit)
                        }
                        break
                    }
                }
            }

            try {
                withTimeout(options.timeout) {
                    connectionComplete!!.await()
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                bridge.disconnect()
                peripheralContext.processEvent(
                    ConnectionEvent.ConnectionLost(BleError.ConnectionFailed("Connection timeout"))
                )
            } finally {
                failureDetector.cancel()
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
                peripheralContext.processEvent(
                    ConnectionEvent.ConnectionLost(BleError.OperationFailed("Disconnect timeout"))
                )
            } finally {
                disconnectComplete = null
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        reconnectionHandler.stop()
        centralDelegate.unregisterConnectionCallback(identifier.value)
        bridge.close()
        peripheralContext.close()
        PeripheralRegistry.remove(identifier)
    }

    override suspend fun refreshServices(): List<DiscoveredService> {
        checkNotClosed()
        return withContext(peripheralContext.dispatcher) {
            discoveryComplete = CompletableDeferred()
            bridge.discoverServices()
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

    // --- Central manager connection callbacks ---

    private fun handleConnectionCallback(connected: Boolean, error: platform.Foundation.NSError?) {
        peripheralContext.scope.launch {
            if (connected) {
                peripheralContext.processEvent(ConnectionEvent.LinkEstablished)
                bridge.discoverServices()
            } else {
                val bleError = if (error != null) {
                    BleError.ConnectionFailed(error.localizedDescription, error.code.toInt())
                } else {
                    BleError.ConnectionLost("Disconnected")
                }

                if (peripheralContext.state.value is State.Disconnecting.Requested) {
                    peripheralContext.processEvent(ConnectionEvent.ConnectionLost(bleError))
                    disconnectComplete?.complete(Unit)
                } else {
                    peripheralContext.processEvent(ConnectionEvent.ConnectionLost(bleError))
                }
                onDisconnectCleanup()
                connectionComplete?.complete(Unit)
            }
        }
    }

    // --- Peripheral delegate callbacks ---

    private fun handleBridgeEvent(event: AppleCallbackEvent) {
        peripheralContext.scope.launch {
            when (event) {
                is AppleCallbackEvent.DidDiscoverServices -> handleServicesDiscovered(event)
                is AppleCallbackEvent.DidDiscoverCharacteristics -> handleCharacteristicsDiscovered()
                is AppleCallbackEvent.DidUpdateValueForCharacteristic -> {
                    // K/N may route both didUpdateValue and didWriteValue here (same
                    // Kotlin type signature). The GATT queue serializes ops — at most
                    // one of read/write is pending. Check write first, then read, then notification.
                    val cbChar = event.characteristic
                    val error = event.error
                    if (pendingOps.characteristicWrite != null) {
                        pendingOps.characteristicWrite?.complete(error.toGattStatus())
                        pendingOps.characteristicWrite = null
                    } else if (pendingOps.characteristicRead != null) {
                        val status = error.toGattStatus()
                        val value = cbChar.value?.toByteArray() ?: byteArrayOf()
                        pendingOps.characteristicRead?.complete(GattResult(value, status))
                        pendingOps.characteristicRead = null
                    } else {
                        val value = cbChar.value?.toByteArray() ?: return@launch
                        val svcUuid = uuidFrom(cbChar.service?.UUID?.UUIDString ?: return@launch)
                        val charUuid = uuidFrom(cbChar.UUID.UUIDString)
                        observationManager.emitByUuid(svcUuid, charUuid, value)
                    }
                }
                is AppleCallbackEvent.DidWriteValueForCharacteristic -> {
                    pendingOps.characteristicWrite?.complete(event.error.toGattStatus())
                    pendingOps.characteristicWrite = null
                }
                is AppleCallbackEvent.DidUpdateValueForDescriptor -> {
                    val error = event.error
                    if (pendingOps.descriptorWrite != null) {
                        pendingOps.descriptorWrite?.complete(error.toGattStatus())
                        pendingOps.descriptorWrite = null
                    } else {
                        val value = (event.descriptor.value as? NSData)?.toByteArray() ?: byteArrayOf()
                        pendingOps.descriptorRead?.complete(GattResult(value, error.toGattStatus()))
                        pendingOps.descriptorRead = null
                    }
                }
                is AppleCallbackEvent.DidWriteValueForDescriptor -> {
                    pendingOps.descriptorWrite?.complete(event.error.toGattStatus())
                    pendingOps.descriptorWrite = null
                }
                is AppleCallbackEvent.DidReadRSSI -> {
                    if (event.error == null) {
                        pendingOps.rssiRead?.complete(event.rssi.intValue)
                    } else {
                        pendingOps.rssiRead?.completeExceptionally(
                            Exception("readRSSI failed: ${event.error.localizedDescription}")
                        )
                    }
                    pendingOps.rssiRead = null
                }
            }
        }
    }

    private suspend fun handleServicesDiscovered(event: AppleCallbackEvent.DidDiscoverServices) {
        if (event.error != null) {
            peripheralContext.processEvent(
                ConnectionEvent.DiscoveryFailed(
                    BleError.GattError("discoverServices", event.error.toGattStatus())
                )
            )
            connectionComplete?.complete(Unit)
            discoveryComplete?.completeExceptionally(
                IllegalStateException("Service discovery failed: ${event.error.localizedDescription}")
            )
            return
        }

        val cbServices = cbPeripheral.services?.filterIsInstance<CBService>() ?: emptyList()
        if (cbServices.isEmpty()) {
            finishDiscovery(emptyList())
            return
        }

        // Discover characteristics for each service
        pendingCharacteristicDiscovery = cbServices.size
        cbServices.forEach { bridge.discoverCharacteristics(it) }
    }

    private suspend fun handleCharacteristicsDiscovered() {
        pendingCharacteristicDiscovery--

        if (pendingCharacteristicDiscovery <= 0) {
            val cbServices = cbPeripheral.services?.filterIsInstance<CBService>() ?: emptyList()
            val discovered = cbServices.map { it.toDiscoveredService() }
            finishDiscovery(discovered)
        }
    }

    private suspend fun finishDiscovery(discovered: List<DiscoveredService>) {
        peripheralContext.processEvent(ConnectionEvent.ServicesDiscovered)
        peripheralContext.updateServices(discovered)
        peripheralContext.processEvent(ConnectionEvent.ConfigurationComplete)
        connectionComplete?.complete(Unit)
        discoveryComplete?.complete(discovered)
    }

    // --- Service parsing ---

    private fun CBService.toDiscoveredService(): DiscoveredService {
        val serviceUuid = uuidFrom(UUID.UUIDString)
        val chars = characteristics?.filterIsInstance<CBCharacteristic>()?.map { cbChar ->
            val charUuid = uuidFrom(cbChar.UUID.UUIDString)
            val props = cbChar.properties.toInt()
            val descs = mutableListOf<Descriptor>()
            val char = Characteristic(
                serviceUuid = serviceUuid,
                uuid = charUuid,
                properties = Characteristic.Properties(
                    read = (props and CBCharacteristicPropertyRead.toInt()) != 0,
                    write = (props and CBCharacteristicPropertyWrite.toInt()) != 0,
                    writeWithoutResponse = (props and CBCharacteristicPropertyWriteWithoutResponse.toInt()) != 0,
                    signedWrite = (props and CBCharacteristicPropertyAuthenticatedSignedWrites.toInt()) != 0,
                    notify = (props and CBCharacteristicPropertyNotify.toInt()) != 0,
                    indicate = (props and CBCharacteristicPropertyIndicate.toInt()) != 0,
                ),
                descriptors = descs,
            )
            nativeCharMap[char] = cbChar
            cbChar.descriptors?.filterIsInstance<CBDescriptor>()?.forEach { cbDesc ->
                val desc = Descriptor(char, uuidFrom(cbDesc.UUID.UUIDString))
                descs.add(desc)
                nativeDescMap[desc] = cbDesc
            }
            char
        } ?: emptyList()

        return DiscoveredService(uuid = serviceUuid, characteristics = chars)
    }

    // --- GATT Operations ---

    private fun requireNativeCbChar(characteristic: Characteristic): CBCharacteristic {
        return nativeCharMap[characteristic]
            ?: throw IllegalArgumentException("Characteristic not found. Re-acquire from services after connect.")
    }

    private fun requireNativeCbDesc(descriptor: Descriptor): CBDescriptor {
        return nativeDescMap[descriptor]
            ?: throw IllegalArgumentException("Descriptor not found.")
    }

    private fun ByteArray.toNSData(): NSData {
        return io.github.garyquinn.kmpble.BleData(this).nsData
    }

    private fun NSData.toByteArray(): ByteArray {
        return bleDataFromNSData(this).toByteArray()
    }

    override suspend fun read(characteristic: Characteristic): ByteArray {
        checkNotClosed()
        return peripheralContext.gattQueue.enqueue {
            val native = requireNativeCbChar(characteristic)
            val deferred = CompletableDeferred<GattResult>()
            pendingOps.characteristicRead = deferred
            bridge.readCharacteristic(native)
            val result = deferred.await()
            if (!result.status.isSuccess()) throw Exception("Read failed: ${result.status}")
            result.value
        }
    }

    override suspend fun write(characteristic: Characteristic, data: ByteArray, writeType: WriteType) {
        checkNotClosed()
        LargeWriteHandler.validateForWriteType(data, maximumWriteValueLength.value, writeType)

        val native = requireNativeCbChar(characteristic)
        val withResponse = writeType == WriteType.WithResponse || writeType == WriteType.Signed
        val chunks = LargeWriteHandler.chunk(data, maximumWriteValueLength.value)
        peripheralContext.gattQueue.enqueue {
            for (chunk in chunks) {
                if (withResponse) {
                    val deferred = CompletableDeferred<io.github.garyquinn.kmpble.error.GattStatus>()
                    pendingOps.characteristicWrite = deferred
                    bridge.writeCharacteristic(native, chunk.toNSData(), withResponse = true)
                    val status = deferred.await()
                    if (!status.isSuccess()) throw Exception("Write failed: $status")
                } else {
                    bridge.writeCharacteristic(native, chunk.toNSData(), withResponse = false)
                }
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
        val native = requireNativeCbChar(characteristic)
        bridge.setNotifyValue(true, native)
    }

    private fun disableNotifications(characteristic: Characteristic) {
        if (peripheralContext.state.value !is State.Connected) return
        val native = nativeCharMap[characteristic] ?: return
        bridge.setNotifyValue(false, native)
    }

    override suspend fun readDescriptor(descriptor: Descriptor): ByteArray {
        checkNotClosed()
        return peripheralContext.gattQueue.enqueue {
            val native = requireNativeCbDesc(descriptor)
            val deferred = CompletableDeferred<GattResult>()
            pendingOps.descriptorRead = deferred
            bridge.readDescriptor(native)
            val result = deferred.await()
            if (!result.status.isSuccess()) throw Exception("Descriptor read failed: ${result.status}")
            result.value
        }
    }

    override suspend fun writeDescriptor(descriptor: Descriptor, data: ByteArray) {
        checkNotClosed()
        peripheralContext.gattQueue.enqueue {
            val native = requireNativeCbDesc(descriptor)
            val deferred = CompletableDeferred<io.github.garyquinn.kmpble.error.GattStatus>()
            pendingOps.descriptorWrite = deferred
            bridge.writeDescriptor(native, data.toNSData())
            val status = deferred.await()
            if (!status.isSuccess()) throw Exception("Descriptor write failed: $status")
        }
    }

    override suspend fun readRssi(): Int {
        checkNotClosed()
        return peripheralContext.gattQueue.enqueue {
            val deferred = CompletableDeferred<Int>()
            pendingOps.rssiRead = deferred
            bridge.readRSSI()
            deferred.await()
        }
    }

    override suspend fun requestMtu(mtu: Int): Int {
        checkNotClosed()
        // iOS negotiates MTU automatically — no explicit request API.
        // Read the actual negotiated value from CoreBluetooth.
        val actualMtu = cbPeripheral.maximumWriteValueLengthForType(
            platform.CoreBluetooth.CBCharacteristicWriteWithResponse
        ).toInt() + 3 // maximumWriteValueLength = MTU - 3
        peripheralContext.updateMtu(actualMtu)
        return actualMtu
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
