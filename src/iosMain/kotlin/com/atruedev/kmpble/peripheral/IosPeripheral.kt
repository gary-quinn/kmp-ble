package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.bonding.BondRemovalResult
import com.atruedev.kmpble.bonding.BondState
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.ConnectionParameterUpdateResult
import com.atruedev.kmpble.connection.ConnectionParameters
import com.atruedev.kmpble.connection.ConnectionPriority
import com.atruedev.kmpble.connection.ConnectionSubratingParameters
import com.atruedev.kmpble.connection.ConnectionSubratingResult
import com.atruedev.kmpble.connection.OperationTimeouts
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.connection.PhyUpdate
import com.atruedev.kmpble.connection.ReconnectionStrategy
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.connection.internal.ConnectionEvent
import com.atruedev.kmpble.connection.internal.ReconnectionHandler
import com.atruedev.kmpble.error.ConnectionFailed
import com.atruedev.kmpble.error.OperationFailed
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.gatt.internal.ObservationManager
import com.atruedev.kmpble.gatt.internal.PendingOperations
import com.atruedev.kmpble.gatt.internal.PersistedObservation
import com.atruedev.kmpble.internal.CentralManagerProvider
import com.atruedev.kmpble.internal.StateRestorationHandler
import com.atruedev.kmpble.isochronous.IsochronousChannel
import com.atruedev.kmpble.isochronous.IsochronousException
import com.atruedev.kmpble.l2cap.IosL2capChannel
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.periodic.PastException
import com.atruedev.kmpble.periodic.PeriodicAdvertisingSync
import com.atruedev.kmpble.peripheral.internal.LifecycleSlots
import com.atruedev.kmpble.peripheral.internal.PeripheralContext
import com.atruedev.kmpble.peripheral.internal.PeripheralRegistry
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
import platform.CoreBluetooth.CBDescriptor
import platform.CoreBluetooth.CBL2CAPChannel
import platform.CoreBluetooth.CBPeripheral
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

    @ExperimentalBleApi
    internal val pairingRequestHandler = IosPairingRequestHandler(identifier)

    override val state: StateFlow<State> get() = peripheralContext.state
    override val bondState: StateFlow<BondState> get() = peripheralContext.bondState
    internal val bondManager = IosBondManager(peripheralContext)
    override val services: StateFlow<List<DiscoveredService>?> get() = peripheralContext.services
    override val maximumWriteValueLength: StateFlow<Int> get() = peripheralContext.maximumWriteValueLength
    override val mtu: StateFlow<Int> get() = peripheralContext.mtu

    @Volatile
    internal var closed = false

    /** Stored during [connect] for GATT ops to reference per-operation timeouts. */
    internal var currentTimeouts: OperationTimeouts = OperationTimeouts()

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
        currentTimeouts = options.timeouts
        pairingRequestHandler.setHandler(options.pairingHandler)
        reconnectionHandler.start(options)
        bondManager.start()
        withContext(peripheralContext.dispatcher) {
            peripheralContext.processEvent(ConnectionEvent.ConnectRequested)
            peripheralContext.gattQueue.start(options.gattOperationTimeout)

            val deferred = slots.armConnect()
            bridge.connect()

            try {
                withTimeout(options.timeouts.connect) { deferred.await() }
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
        bondManager.stop()
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
    override fun removeBond(): BondRemovalResult = bondManager.removeBond()

    override fun close() {
        if (closed) return
        closed = true
        reconnectionHandler.stop()
        bondManager.stop()
        pairingRequestHandler.closeSync()
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
                withTimeout(currentTimeouts.serviceDiscovery) { deferred.await() }
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

    override suspend fun read(characteristic: Characteristic): ByteArray = readGatt(characteristic)

    override suspend fun write(
        characteristic: Characteristic,
        data: ByteArray,
        writeType: WriteType,
    ) {
        writeGatt(characteristic, data, writeType)
    }

    override fun observe(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy,
    ): Flow<Observation> = observeGatt(characteristic, backpressure)

    override fun observeValues(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy,
    ): Flow<ByteArray> = observeValuesGatt(characteristic, backpressure)

    internal fun enableNotifications(characteristic: Characteristic) {
        bridge.setNotifyValue(true, requireNativeCbChar(characteristic))
    }

    internal fun disableNotifications(characteristic: Characteristic) {
        if (peripheralContext.state.value !is State.Connected) return
        val native = nativeCharMap[characteristic] ?: return
        bridge.setNotifyValue(false, native)
    }

    override suspend fun readDescriptor(descriptor: Descriptor): ByteArray = readDescriptorGatt(descriptor)

    override suspend fun writeDescriptor(
        descriptor: Descriptor,
        data: ByteArray,
    ) {
        writeDescriptorGatt(descriptor, data)
    }

    override suspend fun readRssi(): Int = readRssiGatt()

    override suspend fun requestMtu(mtu: Int): Int = requestMtuGatt(mtu)

    @ExperimentalBleApi
    override suspend fun requestConnectionPriority(priority: ConnectionPriority): Boolean =
        requestConnectionPriorityExt(priority)

    @ExperimentalBleApi
    override suspend fun requestConnectionParameterUpdate(
        params: ConnectionParameters,
    ): ConnectionParameterUpdateResult? = requestConnectionParameterUpdateExt(params)

    @ExperimentalBleApi
    override suspend fun setPreferredPhy(
        tx: Phy,
        rx: Phy,
    ): PhyResult? = setPreferredPhyExt(tx, rx)

    @ExperimentalBleApi
    override suspend fun readPhy(): PhyResult? = readPhyExt()

    @ExperimentalBleApi
    override val phyUpdate: Flow<PhyUpdate> = emptyFlow()

    @ExperimentalBleApi
    override suspend fun requestConnectionSubrating(
        parameters: ConnectionSubratingParameters,
    ): ConnectionSubratingResult = ConnectionSubratingResult.NotSupported

    override suspend fun openL2capChannel(
        psm: Int,
        secure: Boolean,
        mtu: Int?,
    ): L2capChannel = openL2capChannelInternal(psm, secure, mtu)

    override suspend fun openIsochronousChannel(): IsochronousChannel =
        throw IsochronousException.NotSupported(
            "CoreBluetooth does not expose LE Audio isochronous channels",
        )

    override suspend fun receivePastSync(): PeriodicAdvertisingSync =
        throw PastException.NotSupported(
            "CoreBluetooth does not expose PAST",
        )

    /**
     * Restore this peripheral from iOS state restoration.
     *
     * Re-populates persisted observations and triggers discovery if iOS already
     * delivered the peripheral as connected.
     */
    internal suspend fun restoreFromStateRestoration(savedObservations: Set<PersistedObservation>) {
        restoreFromStateRestorationExt(savedObservations)
    }
}
