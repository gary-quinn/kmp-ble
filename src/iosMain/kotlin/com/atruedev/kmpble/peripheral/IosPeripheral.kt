package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.bonding.BondRemovalResult
import com.atruedev.kmpble.bonding.BondState
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.ConnectionPriority
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.connection.PhyUpdate
import com.atruedev.kmpble.connection.ReconnectionStrategy
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.connection.internal.ConnectionEvent
import com.atruedev.kmpble.connection.internal.ReconnectionHandler
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.ConnectionFailed
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.OperationFailed
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.gatt.internal.LargeWriteHandler
import com.atruedev.kmpble.gatt.internal.ObservationManager
import com.atruedev.kmpble.gatt.internal.PendingOp
import com.atruedev.kmpble.gatt.internal.PendingOperations
import com.atruedev.kmpble.gatt.internal.PersistedObservation
import com.atruedev.kmpble.internal.CentralManagerProvider
import com.atruedev.kmpble.internal.StateRestorationHandler
import com.atruedev.kmpble.l2cap.IosL2capChannel
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.peripheral.internal.LifecycleSlots
import com.atruedev.kmpble.peripheral.internal.ObservationToBytes
import com.atruedev.kmpble.peripheral.internal.ObservationToObservation
import com.atruedev.kmpble.peripheral.internal.PeripheralContext
import com.atruedev.kmpble.peripheral.internal.PeripheralRegistry
import com.atruedev.kmpble.peripheral.internal.awaitGatt
import com.atruedev.kmpble.peripheral.internal.buildObservationFlow
import com.atruedev.kmpble.peripheral.internal.findCharacteristic
import com.atruedev.kmpble.peripheral.internal.findDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicWriteWithResponse
import platform.CoreBluetooth.CBDescriptor
import platform.CoreBluetooth.CBL2CAPChannel
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralStateConnected
import kotlin.concurrent.Volatile
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
public class IosPeripheral(
    internal val cbPeripheral: CBPeripheral,
) : Peripheral {
    override val identifier: Identifier = Identifier(cbPeripheral.identifier.UUIDString)
    internal val peripheralContext = PeripheralContext(identifier)
    internal val bridge = ApplePeripheralBridge(cbPeripheral)
    private val centralDelegate = CentralManagerProvider.scanDelegate

    internal val pendingOps = PendingOperations()
    internal val observationManager = ObservationManager(peripheralContext.dispatcher)
    internal val slots = LifecycleSlots()

    internal val nativeCharMap = mutableMapOf<Characteristic, CBCharacteristic>()
    internal val nativeDescMap = mutableMapOf<Descriptor, CBDescriptor>()

    internal var pendingL2capChannel: CompletableDeferred<CBL2CAPChannel>? = null
    internal val activeL2capChannels = MutableStateFlow<List<IosL2capChannel>>(emptyList())

    override val state: StateFlow<State> get() = peripheralContext.state
    override val bondState: StateFlow<BondState> get() = peripheralContext.bondState
    override val services: StateFlow<List<DiscoveredService>?> get() = peripheralContext.services
    override val maximumWriteValueLength: StateFlow<Int> get() = peripheralContext.maximumWriteValueLength

    @Volatile
    internal var closed = false

    /** Discovery generation counter - increments on each new discovery cycle to detect stale callbacks. */
    @Volatile
    internal var discoveryGeneration = 0

    /** Current discovery cycle state, confined to peripheralContext.dispatcher. */
    internal var currentDiscovery: DiscoveryCycle? = null

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

        // Invalidate in-flight discovery cycle callbacks before teardown.
        discoveryGeneration++
        currentDiscovery = null
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
            // New discovery cycle: increment generation to invalidate stale callbacks
            discoveryGeneration++
            // Clear stale native handle mappings from previous cycle
            nativeCharMap.clear()
            nativeDescMap.clear()
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

    internal fun enableNotifications(characteristic: Characteristic) {
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

    @ExperimentalBleApi
    override suspend fun requestConnectionPriority(priority: ConnectionPriority): Boolean {
        checkNotClosed()
        return false
    }

    @ExperimentalBleApi
    override suspend fun setPreferredPhy(
        tx: Phy,
        rx: Phy,
    ): PhyResult? {
        checkNotClosed()
        return null
    }

    @ExperimentalBleApi
    override suspend fun readPhy(): PhyResult? {
        checkNotClosed()
        return null
    }

    @ExperimentalBleApi
    override val phyUpdate: Flow<PhyUpdate> = emptyFlow()

    override suspend fun openL2capChannel(
        psm: Int,
        secure: Boolean,
        mtu: Int?,
    ): L2capChannel = openL2capChannelInternal(psm, secure, mtu)

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
}
