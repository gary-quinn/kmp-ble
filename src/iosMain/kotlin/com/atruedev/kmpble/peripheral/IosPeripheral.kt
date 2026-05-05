package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.bleDataFromNSData
import com.atruedev.kmpble.bonding.BondRemovalResult
import com.atruedev.kmpble.bonding.BondState
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
import com.atruedev.kmpble.gatt.internal.GattResult
import com.atruedev.kmpble.gatt.internal.LargeWriteHandler
import com.atruedev.kmpble.gatt.internal.NotConnectedException
import com.atruedev.kmpble.gatt.internal.ObservationManager
import com.atruedev.kmpble.gatt.internal.PendingOp
import com.atruedev.kmpble.gatt.internal.PendingOperations
import com.atruedev.kmpble.gatt.internal.PersistedObservation
import com.atruedev.kmpble.internal.CentralManagerProvider
import com.atruedev.kmpble.internal.StateRestorationHandler
import com.atruedev.kmpble.l2cap.DEFAULT_L2CAP_MTU
import com.atruedev.kmpble.l2cap.IosL2capChannel
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.l2cap.L2capException
import com.atruedev.kmpble.peripheral.internal.LifecycleSlots
import com.atruedev.kmpble.peripheral.internal.ObservationToBytes
import com.atruedev.kmpble.peripheral.internal.ObservationToObservation
import com.atruedev.kmpble.peripheral.internal.PeripheralContext
import com.atruedev.kmpble.peripheral.internal.PeripheralRegistry
import com.atruedev.kmpble.peripheral.internal.awaitGatt
import com.atruedev.kmpble.peripheral.internal.buildObservationFlow
import com.atruedev.kmpble.peripheral.internal.findCharacteristic
import com.atruedev.kmpble.peripheral.internal.findDescriptor
import com.atruedev.kmpble.scanner.uuidFrom
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
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
import platform.CoreBluetooth.CBCharacteristicWriteWithResponse
import platform.CoreBluetooth.CBDescriptor
import platform.CoreBluetooth.CBL2CAPChannel
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralStateConnected
import platform.CoreBluetooth.CBService
import platform.Foundation.NSData
import platform.Foundation.NSError
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.seconds
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

    private val pendingOps = PendingOperations()
    private val observationManager = ObservationManager()
    private val slots = LifecycleSlots()

    private val nativeCharMap = mutableMapOf<Characteristic, CBCharacteristic>()
    private val nativeDescMap = mutableMapOf<Descriptor, CBDescriptor>()

    private var pendingL2capChannel: CompletableDeferred<CBL2CAPChannel>? = null
    private val activeL2capChannels = MutableStateFlow<List<IosL2capChannel>>(emptyList())

    override val state: StateFlow<State> get() = peripheralContext.state
    override val bondState: StateFlow<BondState> get() = peripheralContext.bondState
    override val services: StateFlow<List<DiscoveredService>?> get() = peripheralContext.services
    override val maximumWriteValueLength: StateFlow<Int> get() = peripheralContext.maximumWriteValueLength

    @Volatile
    private var closed = false

    private var pendingCharacteristicDiscovery = 0
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
        bridge.onEvent = { event -> handleBridgeEvent(event) }
        centralDelegate.registerConnectionCallback(identifier.value) { connected, error ->
            handleConnectionCallback(connected, error)
        }
        if (CentralManagerProvider.isStateRestorationEnabled) {
            observationManager.onObservationsChanged = { observations ->
                StateRestorationHandler.default.persistObservations(identifier.value, observations)
            }
        }
    }

    override suspend fun connect(options: ConnectionOptions) {
        checkNotClosed()
        reconnectionHandler.start(options)
        withContext(peripheralContext.dispatcher) {
            peripheralContext.processEvent(ConnectionEvent.ConnectRequested)
            peripheralContext.gattQueue.start(options.gattOperationTimeout)

            val deferred = slots.armConnect()
            bridge.connect()

            try {
                withTimeout(options.timeout) { deferred.await() }
            } catch (_: TimeoutCancellationException) {
                bridge.disconnect()
                peripheralContext.processEvent(
                    ConnectionEvent.ConnectionLost(ConnectionFailed("Connection timeout")),
                )
            } finally {
                slots.clearConnect()
            }
        }
    }

    override suspend fun disconnect() {
        checkNotClosed()
        reconnectionHandler.stop()
        withContext(peripheralContext.dispatcher) {
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
            }
        }
    }

    @ExperimentalBleApi
    override fun removeBond(): BondRemovalResult =
        BondRemovalResult.NotSupported(
            "iOS does not support programmatic bond removal. Remove from Settings > Bluetooth.",
        )

    override fun close() {
        if (closed) return
        closed = true
        reconnectionHandler.stop()
        closeL2capChannels()
        centralDelegate.unregisterConnectionCallback(identifier.value)
        bridge.close()
        observationManager.onObservationsChanged = null
        observationManager.clear()
        StateRestorationHandler.default.clearPersistedObservations(identifier.value)
        peripheralContext.close()
        PeripheralRegistry.remove(identifier)
    }

    override suspend fun refreshServices(): List<DiscoveredService> {
        checkNotClosed()
        return withContext(peripheralContext.dispatcher) {
            val deferred = slots.armDiscovery()
            bridge.discoverServices()
            try {
                withTimeout(DISCOVERY_TIMEOUT) { deferred.await() }
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

    private fun handleConnectionCallback(
        connected: Boolean,
        error: NSError?,
    ) {
        peripheralContext.scope.launch {
            if (connected) {
                peripheralContext.processEvent(ConnectionEvent.LinkEstablished)
                bridge.discoverServices()
                return@launch
            }

            val bleError =
                if (error != null) {
                    ConnectionFailed(error.localizedDescription, error.code.toInt())
                } else {
                    ConnectionLost("Disconnected")
                }

            if (peripheralContext.state.value is State.Disconnecting.Requested) {
                peripheralContext.processEvent(ConnectionEvent.ConnectionLost(bleError))
                slots.completeDisconnect()
            } else {
                peripheralContext.processEvent(ConnectionEvent.ConnectionLost(bleError))
            }
            onDisconnectCleanup()
            slots.completeConnect()
        }
    }

    private fun handleBridgeEvent(event: AppleCallbackEvent) {
        peripheralContext.scope.launch {
            when (event) {
                is AppleCallbackEvent.DidDiscoverServices -> handleServicesDiscovered(event)
                is AppleCallbackEvent.DidDiscoverCharacteristics -> handleCharacteristicsDiscovered()
                is AppleCallbackEvent.DidUpdateValueForCharacteristic -> handleCharacteristicValue(event)
                is AppleCallbackEvent.DidWriteValueForCharacteristic ->
                    pendingOps.complete(PendingOp.CharacteristicWrite, event.error.toGattStatus())
                is AppleCallbackEvent.DidUpdateValueForDescriptor -> handleDescriptorValue(event)
                is AppleCallbackEvent.DidWriteValueForDescriptor ->
                    pendingOps.complete(PendingOp.DescriptorWrite, event.error.toGattStatus())
                is AppleCallbackEvent.DidReadRSSI -> handleRssi(event)
                is AppleCallbackEvent.DidOpenL2CAPChannel -> handleDidOpenL2CAPChannel(event)
            }
        }
    }

    /**
     * K/N maps both `didUpdateValue` (read response, notification) and `didWriteValue`
     * (write response) to this single signature. Disambiguate by which slot is armed:
     * the GATT queue ensures only one read/write is pending.
     */
    private fun handleCharacteristicValue(event: AppleCallbackEvent.DidUpdateValueForCharacteristic) {
        val cbChar = event.characteristic
        val error = event.error
        when {
            pendingOps.has(PendingOp.CharacteristicWrite) ->
                pendingOps.complete(PendingOp.CharacteristicWrite, error.toGattStatus())
            pendingOps.has(PendingOp.CharacteristicRead) -> {
                val value = cbChar.value?.toByteArray() ?: byteArrayOf()
                pendingOps.complete(PendingOp.CharacteristicRead, GattResult(value, error.toGattStatus()))
            }
            else -> {
                val value = cbChar.value?.toByteArray() ?: return
                val svcUuid = uuidFrom(cbChar.service?.UUID?.UUIDString ?: return)
                val charUuid = uuidFrom(cbChar.UUID.UUIDString)
                observationManager.emitByUuid(svcUuid, charUuid, value)
            }
        }
    }

    private fun handleDescriptorValue(event: AppleCallbackEvent.DidUpdateValueForDescriptor) {
        val error = event.error
        if (pendingOps.has(PendingOp.DescriptorWrite)) {
            pendingOps.complete(PendingOp.DescriptorWrite, error.toGattStatus())
        } else {
            val value = (event.descriptor.value as? NSData)?.toByteArray() ?: byteArrayOf()
            pendingOps.complete(PendingOp.DescriptorRead, GattResult(value, error.toGattStatus()))
        }
    }

    private fun handleRssi(event: AppleCallbackEvent.DidReadRSSI) {
        if (event.error == null) {
            pendingOps.complete(PendingOp.RssiRead, event.rssi.intValue)
        } else {
            pendingOps.fail(
                PendingOp.RssiRead,
                BleException(GattError("readRssi", event.error.toGattStatus())),
            )
        }
    }

    private suspend fun handleServicesDiscovered(event: AppleCallbackEvent.DidDiscoverServices) {
        if (event.error != null) {
            val status = event.error.toGattStatus()
            peripheralContext.processEvent(ConnectionEvent.DiscoveryFailed(GattError("discoverServices", status)))
            slots.completeConnect()
            slots.failDiscovery(BleException(GattError("discoverServices", status)))
            return
        }

        val cbServices = cbPeripheral.services?.filterIsInstance<CBService>().orEmpty()
        if (cbServices.isEmpty()) {
            finishDiscovery(emptyList())
            return
        }

        pendingCharacteristicDiscovery = cbServices.size
        cbServices.forEach { bridge.discoverCharacteristics(it) }
    }

    private suspend fun handleCharacteristicsDiscovered() {
        pendingCharacteristicDiscovery--
        if (pendingCharacteristicDiscovery > 0) return

        val discovered =
            cbPeripheral.services
                ?.filterIsInstance<CBService>()
                ?.map { it.toDiscoveredService() }
                .orEmpty()
        finishDiscovery(discovered)
    }

    private suspend fun finishDiscovery(discovered: List<DiscoveredService>) {
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

    private fun CBService.toDiscoveredService(): DiscoveredService {
        val serviceUuid = uuidFrom(UUID.UUIDString)
        val chars =
            characteristics
                ?.filterIsInstance<CBCharacteristic>()
                ?.map { cbChar ->
                    val charUuid = uuidFrom(cbChar.UUID.UUIDString)
                    val props = cbChar.properties.toInt()
                    val descs = mutableListOf<Descriptor>()
                    val char =
                        Characteristic(
                            serviceUuid = serviceUuid,
                            uuid = charUuid,
                            properties =
                                Characteristic.Properties(
                                    read = (props and CBCharacteristicPropertyRead.toInt()) != 0,
                                    write = (props and CBCharacteristicPropertyWrite.toInt()) != 0,
                                    writeWithoutResponse =
                                        (props and CBCharacteristicPropertyWriteWithoutResponse.toInt()) != 0,
                                    signedWrite =
                                        (props and CBCharacteristicPropertyAuthenticatedSignedWrites.toInt()) != 0,
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
                }.orEmpty()

        return DiscoveredService(uuid = serviceUuid, characteristics = chars)
    }

    private fun requireNativeCbChar(c: Characteristic): CBCharacteristic =
        nativeCharMap[c]
            ?: throw IllegalArgumentException("Characteristic not found. Re-acquire from services after connect.")

    private fun requireNativeCbDesc(d: Descriptor): CBDescriptor =
        nativeDescMap[d] ?: throw IllegalArgumentException("Descriptor not found.")

    private fun ByteArray.toNSData(): NSData = BleData(this).nsData

    private fun NSData.toByteArray(): ByteArray = bleDataFromNSData(this).toByteArray()

    override suspend fun read(characteristic: Characteristic): ByteArray {
        checkNotClosed()
        return peripheralContext.gattQueue.enqueue {
            val native = requireNativeCbChar(characteristic)
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

        val native = requireNativeCbChar(characteristic)
        val withResponse = writeType == WriteType.WithResponse || writeType == WriteType.Signed
        val chunks = LargeWriteHandler.chunk(data, maximumWriteValueLength.value)

        peripheralContext.gattQueue.enqueue {
            for (chunk in chunks) {
                if (withResponse) {
                    val status =
                        pendingOps.awaitGatt(PendingOp.CharacteristicWrite, "write") {
                            bridge.writeCharacteristic(native, chunk.toNSData(), withResponse = true)
                        }
                    if (!status.isSuccess()) throw BleException(GattError("write", status))
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
        return buildObservationFlow(
            characteristic = characteristic,
            backpressure = backpressure,
            observationManager = observationManager,
            isReady = { peripheralContext.state.value is State.Connected.Ready },
            enable = ::enableNotifications,
            disable = ::disableNotifications,
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
            disable = ::disableNotifications,
            mapper = ObservationToBytes,
        )
    }

    private fun enableNotifications(characteristic: Characteristic) {
        bridge.setNotifyValue(true, requireNativeCbChar(characteristic))
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
            val result =
                pendingOps.awaitGatt(PendingOp.DescriptorRead, "readDescriptor") {
                    bridge.readDescriptor(native)
                }
            if (!result.status.isSuccess()) throw BleException(GattError("readDescriptor", result.status))
            result.value
        }
    }

    override suspend fun writeDescriptor(
        descriptor: Descriptor,
        data: ByteArray,
    ) {
        checkNotClosed()
        peripheralContext.gattQueue.enqueue {
            val native = requireNativeCbDesc(descriptor)
            val status =
                pendingOps.awaitGatt(PendingOp.DescriptorWrite, "writeDescriptor") {
                    bridge.writeDescriptor(native, data.toNSData())
                }
            if (!status.isSuccess()) throw BleException(GattError("writeDescriptor", status))
        }
    }

    override suspend fun readRssi(): Int {
        checkNotClosed()
        return peripheralContext.gattQueue.enqueue {
            pendingOps.awaitGatt(PendingOp.RssiRead, "readRssi") { bridge.readRSSI() }
        }
    }

    override suspend fun requestMtu(mtu: Int): Int {
        checkNotClosed()
        val actualMtu =
            cbPeripheral
                .maximumWriteValueLengthForType(CBCharacteristicWriteWithResponse)
                .toInt() + ATT_HEADER_SIZE
        peripheralContext.updateMtu(actualMtu)
        return actualMtu
    }

    override suspend fun openL2capChannel(
        psm: Int,
        secure: Boolean,
        mtu: Int?,
    ): L2capChannel {
        checkNotClosed()
        if (mtu != null) require(mtu > 0) { "mtu must be positive, was $mtu" }
        if (peripheralContext.state.value !is State.Connected) {
            throw L2capException.NotConnected("Peripheral is not connected (state: ${peripheralContext.state.value})")
        }

        return withContext(peripheralContext.dispatcher) {
            if (pendingL2capChannel != null) {
                throw L2capException.OpenFailed(psm, "Another L2CAP channel open is already in progress")
            }
            val deferred = CompletableDeferred<CBL2CAPChannel>()
            pendingL2capChannel = deferred
            bridge.openL2CAPChannel(psm.toUShort())

            try {
                val cbChannel = withTimeout(L2CAP_OPEN_TIMEOUT) { deferred.await() }
                val channel = IosL2capChannel(cbChannel, peripheralContext.scope, mtu ?: DEFAULT_L2CAP_MTU)
                activeL2capChannels.update { it + channel }
                channel
            } catch (_: TimeoutCancellationException) {
                pendingL2capChannel = null
                throw L2capException.OpenFailed(psm, "Timeout waiting for L2CAP channel")
            } catch (e: L2capException) {
                pendingL2capChannel = null
                throw e
            } catch (e: Exception) {
                pendingL2capChannel = null
                throw L2capException.OpenFailed(psm, e.message ?: "Unknown error", e)
            }
        }
    }

    private fun handleDidOpenL2CAPChannel(event: AppleCallbackEvent.DidOpenL2CAPChannel) {
        val deferred = pendingL2capChannel ?: return
        pendingL2capChannel = null

        when {
            event.error != null ->
                deferred.completeExceptionally(
                    L2capException.OpenFailed(
                        psm = event.channel?.PSM?.toInt() ?: -1,
                        message = event.error.localizedDescription,
                    ),
                )
            event.channel != null -> deferred.complete(event.channel)
            else ->
                deferred.completeExceptionally(
                    L2capException.OpenFailed(psm = -1, message = "Channel is null with no error"),
                )
        }
    }

    private fun closeL2capChannels() {
        activeL2capChannels.getAndUpdate { emptyList() }.forEach { it.close() }
    }

    private fun onDisconnectCleanup() {
        nativeCharMap.clear()
        nativeDescMap.clear()
        closeL2capChannels()
        observationManager.onDisconnect()
        pendingOps.cancelAll(NotConnectedException())
    }

    /**
     * Restore this peripheral from iOS state restoration.
     *
     * Re-populates persisted observations and triggers discovery if iOS already
     * delivered the peripheral as connected.
     */
    internal suspend fun restoreFromStateRestoration(savedObservations: Set<PersistedObservation>) {
        if (closed) return

        withContext(peripheralContext.dispatcher) {
            for (obs in savedObservations) {
                observationManager.subscribe(obs.key.serviceUuid, obs.key.charUuid, obs.backpressure)
            }

            if (cbPeripheral.state == CBPeripheralStateConnected) {
                peripheralContext.processEvent(ConnectionEvent.ConnectRequested)
                peripheralContext.gattQueue.start()
                peripheralContext.processEvent(ConnectionEvent.LinkEstablished)

                val deferred = slots.armConnect()
                bridge.discoverServices()
                try {
                    withTimeout(DISCOVERY_TIMEOUT) { deferred.await() }
                } finally {
                    slots.clearConnect()
                }
            }
        }
    }

    private fun checkNotClosed() {
        check(!closed) { "Peripheral is closed" }
    }

    private companion object {
        val L2CAP_OPEN_TIMEOUT = 30.seconds
        val DISCONNECT_TIMEOUT = 5.seconds
        val DISCOVERY_TIMEOUT = 10.seconds
        const val ATT_HEADER_SIZE = 3
    }
}
