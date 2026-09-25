package com.atruedev.kmpble.backend.internal

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.backend.KmpBleBackendApi
import com.atruedev.kmpble.backend.PeripheralEvent
import com.atruedev.kmpble.backend.PeripheralTransport
import com.atruedev.kmpble.bonding.BondRemovalResult
import com.atruedev.kmpble.bonding.BondState
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.ConnectionParameterUpdateResult
import com.atruedev.kmpble.connection.ConnectionParameters
import com.atruedev.kmpble.connection.ConnectionPriority
import com.atruedev.kmpble.connection.ConnectionSubratingParameters
import com.atruedev.kmpble.connection.ConnectionSubratingResult
import com.atruedev.kmpble.connection.DataLengthParameters
import com.atruedev.kmpble.connection.EncryptionLevel
import com.atruedev.kmpble.connection.OperationTimeouts
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.connection.PhyUpdate
import com.atruedev.kmpble.connection.ReconnectionStrategy
import com.atruedev.kmpble.connection.internal.ReconnectionHandler
import com.atruedev.kmpble.direction.DirectionFindingParameters
import com.atruedev.kmpble.direction.DirectionFindingResult
import com.atruedev.kmpble.error.BleError
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.gatt.internal.ObservationManager
import com.atruedev.kmpble.isochronous.IsochronousChannel
import com.atruedev.kmpble.isochronous.IsochronousException
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.periodic.PastException
import com.atruedev.kmpble.periodic.PeriodicAdvertisingSync
import com.atruedev.kmpble.peripheral.Peripheral
import com.atruedev.kmpble.peripheral.PhyResult
import com.atruedev.kmpble.peripheral.internal.PeripheralContext
import com.atruedev.kmpble.peripheral.internal.PeripheralRegistry
import com.atruedev.kmpble.peripheral.internal.findCharacteristic
import com.atruedev.kmpble.peripheral.internal.findDescriptor
import com.atruedev.kmpble.peripheral.internal.requirePeripheralOpen
import com.atruedev.kmpble.peripheral.state.State
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * [Peripheral] shared by every JVM backend. Lifecycle, GATT queueing, observation, and
 * reconnection come from the common internals; the backend only supplies [transport].
 *
 * Everything marked "confined" is read and written only on [PeripheralContext.dispatcher]:
 * transport callbacks are funnelled through [events] and handled there.
 */
@OptIn(KmpBleBackendApi::class, ExperimentalUuidApi::class)
internal class BackendPeripheral(
    override val identifier: Identifier,
    internal val transport: PeripheralTransport,
) : Peripheral {
    internal val context = PeripheralContext(identifier)
    internal val observationManager = ObservationManager(context.dispatcher)
    internal val events = Channel<PeripheralEvent>(Channel.UNLIMITED)
    internal val l2capChannels = CopyOnWriteArrayList<BackendL2capChannel>()
    internal val pendingDisables: MutableSet<Job> = ConcurrentHashMap.newKeySet()
    private val closed = AtomicBoolean(false)

    internal val charHandles = mutableMapOf<Characteristic, Long>()
    internal val charsByHandle = mutableMapOf<Long, Characteristic>()
    internal val descHandles = mutableMapOf<Descriptor, Long>()

    @Volatile internal var timeouts = OperationTimeouts()

    internal var linkUp = false
    internal var linkOwnedByConnect = false
    internal var linkLossDuringConnect: BleError? = null
    internal var servicesChangedWhileConnecting = false

    @Volatile
    private var _lastConnectionOptions: ConnectionOptions? = null

    internal val reconnectionHandler =
        ReconnectionHandler(
            scope = context.scope,
            stateFlow = context.state,
            connectAction = { options -> connect(options.copy(reconnectionStrategy = ReconnectionStrategy.None)) },
            onMaxAttemptsExhausted = { observationManager.onPermanentDisconnect() },
        )

    init {
        transport.setEventListener { event -> events.trySend(event) }
        context.scope.launch {
            for (event in events) handleEvent(event)
        }
    }

    internal val isClosed: Boolean get() = closed.get()

    internal fun checkOpen() {
        requirePeripheralOpen(closed.get())
    }

    override val lastConnectionOptions: ConnectionOptions? get() = _lastConnectionOptions
    override val state: StateFlow<State> get() = context.state
    override val bondState: StateFlow<BondState> get() = context.bondState
    override val encryptionLevel: StateFlow<EncryptionLevel> get() = context.encryptionLevel
    override val services: StateFlow<List<DiscoveredService>?> get() = context.services
    override val maximumWriteValueLength: StateFlow<Int> get() = context.maximumWriteValueLength
    override val supportsReliableWrite: Boolean get() = transport.features.supportsReliableWrite
    override val mtu: StateFlow<Int> get() = context.mtu
    override val phyUpdate: Flow<PhyUpdate> = emptyFlow()
    override val dataLengthParameters: StateFlow<DataLengthParameters?> get() = context.dataLengthParameters

    override suspend fun connect(options: ConnectionOptions) {
        checkOpen()
        _lastConnectionOptions = options
        transport.setPairingHandler(options.pairingHandler)
        reconnectionHandler.start(options)
        connectInternal(options)
    }

    override suspend fun disconnect() {
        checkOpen()
        reconnectionHandler.stop()
        disconnectInternal()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        reconnectionHandler.stop()
        l2capChannels.forEach { it.close() }
        l2capChannels.clear()
        transport.setEventListener(null)
        runCatching { transport.close() }
        events.close()
        observationManager.clear()
        context.close()
        PeripheralRegistry.remove(identifier)
    }

    override fun removeBond(): BondRemovalResult {
        checkOpen()
        val result = transport.removeBond()
        if (result is BondRemovalResult.Success) {
            context.scope.launch { context.updateBondState(BondState.NotBonded) }
        }
        return result
    }

    override suspend fun refreshServices(): List<DiscoveredService> = refreshServicesInternal()

    override fun findCharacteristic(
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
    ): Characteristic? = services.value.findCharacteristic(serviceUuid, characteristicUuid)

    override fun findDescriptor(
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
        descriptorUuid: Uuid,
    ): Descriptor? = services.value.findDescriptor(serviceUuid, characteristicUuid, descriptorUuid)

    override suspend fun read(characteristic: Characteristic): ByteArray = readInternal(characteristic)

    override suspend fun write(
        characteristic: Characteristic,
        data: ByteArray,
        writeType: WriteType,
    ) {
        writeInternal(characteristic, data, writeType)
    }

    override suspend fun writeReliable(
        characteristic: Characteristic,
        data: ByteArray,
    ) {
        writeReliableInternal(characteristic, data)
    }

    override fun observe(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy,
    ): Flow<Observation> = observeInternal(characteristic, backpressure)

    override fun observeValues(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy,
    ): Flow<ByteArray> = observeValuesInternal(characteristic, backpressure)

    override suspend fun readDescriptor(descriptor: Descriptor): ByteArray = readDescriptorInternal(descriptor)

    override suspend fun writeDescriptor(
        descriptor: Descriptor,
        data: ByteArray,
    ) {
        writeDescriptorInternal(descriptor, data)
    }

    override suspend fun readRssi(): Int = readRssiInternal()

    override suspend fun requestMtu(mtu: Int): Int = requestMtuInternal(mtu)

    override suspend fun requestConnectionPriority(priority: ConnectionPriority): Boolean {
        checkOpen()
        return false
    }

    @ExperimentalBleApi
    override suspend fun requestConnectionParameterUpdate(
        params: ConnectionParameters,
    ): ConnectionParameterUpdateResult? {
        checkOpen()
        return null
    }

    override suspend fun setPreferredPhy(
        tx: Phy,
        rx: Phy,
    ): PhyResult? {
        checkOpen()
        return null
    }

    override suspend fun readPhy(): PhyResult? {
        checkOpen()
        return null
    }

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
        throw IsochronousException.NotSupported("Isochronous channels are not available on JVM backends")

    @ExperimentalBleApi
    override suspend fun receivePastSync(): PeriodicAdvertisingSync =
        throw PastException.NotSupported("PAST is not available on JVM backends")

    @ExperimentalBleApi
    override suspend fun requestDirectionFinding(parameters: DirectionFindingParameters): DirectionFindingResult =
        DirectionFindingResult.NotSupported
}
