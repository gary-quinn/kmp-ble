package com.atruedev.kmpble.testing

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.ConnectionParameterUpdateResult
import com.atruedev.kmpble.connection.ConnectionParameters
import com.atruedev.kmpble.connection.ConnectionPriority
import com.atruedev.kmpble.connection.ConnectionSubratingParameters
import com.atruedev.kmpble.connection.ConnectionSubratingResult
import com.atruedev.kmpble.connection.DataLengthParameters
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.connection.PhyUpdate
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.connection.internal.ConnectionEvent
import com.atruedev.kmpble.direction.DirectionFindingParameters
import com.atruedev.kmpble.direction.DirectionFindingResult
import com.atruedev.kmpble.error.ConnectionFailed
import com.atruedev.kmpble.error.OperationFailed
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.gatt.internal.ObservationManager
import com.atruedev.kmpble.isochronous.IsochronousChannel
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.periodic.PeriodicAdvertisingSync
import com.atruedev.kmpble.peripheral.Peripheral
import com.atruedev.kmpble.peripheral.PhyResult
import com.atruedev.kmpble.peripheral.internal.PeripheralContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
public class FakePeripheral internal constructor(
    override val identifier: Identifier,
    private var fakeServices: List<DiscoveredService>,
    private val characteristicConfigs: List<FakeCharacteristicConfig>,
    private val onConnectHandler: suspend () -> Result<Unit>,
    private val onDisconnectHandler: suspend () -> Result<Unit>,
    private val onL2capHandler: L2capHandler?,
    private val onIsoHandler: IsochronousHandler?,
    private val onPastSyncHandler: PastSyncHandler?,
    private val onConnectionParameterUpdateHandler: (
        suspend (ConnectionParameters) -> ConnectionParameterUpdateResult?
    )? = null,
    private val onDirectionFindingHandler: (
        suspend (DirectionFindingParameters) -> DirectionFindingResult
    )? = null,
    private val observationDispatcher: CoroutineDispatcher =
        Dispatchers.Default.limitedParallelism(
            1,
        ),
) : Peripheral {
    private val context = PeripheralContext(identifier)
    internal val peripheralContext: PeripheralContext get() = context
    internal val observationManager = ObservationManager(observationDispatcher)
    private var closed = false
    internal val cccdWritesState = MutableStateFlow<List<CccdWrite>>(emptyList())

    internal val connectionSimulator =
        FakeConnectionSimulator(
            context = context,
            observationManager = observationManager,
            fakeServices = fakeServices,
            cccdWritesState = cccdWritesState,
            closedFlag = { closed },
        )

    internal val gattResponder =
        FakeGattResponder(
            context = context,
            observationManager = observationManager,
            characteristicConfigs = characteristicConfigs,
            onL2capHandler = onL2capHandler,
            onIsoHandler = onIsoHandler,
            onPastSyncHandler = onPastSyncHandler,
            cccdWritesState = cccdWritesState,
            closedFlag = { closed },
            onDirectionFindingHandler = onDirectionFindingHandler,
        )

    public data class CccdWrite(
        val serviceUuid: Uuid,
        val charUuid: Uuid,
        val enabled: Boolean,
    )

    override val state: StateFlow<State> get() = context.state
    override val bondState: StateFlow<com.atruedev.kmpble.bonding.BondState> get() = context.bondState
    override val services: StateFlow<List<DiscoveredService>?> get() = context.services
    override val maximumWriteValueLength: StateFlow<Int> get() = context.maximumWriteValueLength
    override val mtu: StateFlow<Int> get() = context.mtu

    override suspend fun connect(options: ConnectionOptions) {
        checkNotClosed()
        context.processEvent(ConnectionEvent.ConnectRequested)
        context.gattQueue.start()

        val result = onConnectHandler()
        if (result.isSuccess) {
            context.processEvent(ConnectionEvent.LinkEstablished)
            context.processEvent(ConnectionEvent.ServicesDiscovered)
            context.updateServices(fakeServices)
            context.processEvent(ConnectionEvent.ConfigurationComplete)
        } else {
            val error =
                ConnectionFailed(
                    result.exceptionOrNull()?.message ?: "Connection failed",
                )
            context.processEvent(ConnectionEvent.ConnectionLost(error))
        }
    }

    override suspend fun disconnect() {
        checkNotClosed()
        if (context.state.value is State.Disconnected) return
        context.processEvent(ConnectionEvent.DisconnectRequested)
        onDisconnectHandler()
        context.processEvent(ConnectionEvent.ConnectionLost(OperationFailed("disconnect")))
    }

    @com.atruedev.kmpble.ExperimentalBleApi
    override fun removeBond(): com.atruedev.kmpble.bonding.BondRemovalResult =
        com.atruedev.kmpble.bonding.BondRemovalResult
            .NotSupported("FakePeripheral")

    override fun close() {
        if (closed) return
        closed = true
        observationManager.clear()
        context.close()
    }

    override suspend fun refreshServices(): List<DiscoveredService> {
        checkNotClosed()
        context.updateServices(fakeServices)
        return fakeServices
    }

    override fun findCharacteristic(
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
    ): Characteristic? =
        services.value
            ?.firstOrNull { it.uuid == serviceUuid }
            ?.characteristics
            ?.firstOrNull { it.uuid == characteristicUuid }

    override fun findDescriptor(
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
        descriptorUuid: Uuid,
    ): Descriptor? {
        val char = findCharacteristic(serviceUuid, characteristicUuid) ?: return null
        return char.descriptors.firstOrNull { it.uuid == descriptorUuid }
    }

    // --- GATT Operations (delegated to FakeGattResponder) ---

    override suspend fun read(characteristic: Characteristic): ByteArray = gattResponder.read(characteristic)

    override suspend fun write(
        characteristic: Characteristic,
        data: ByteArray,
        writeType: WriteType,
    ): Unit = gattResponder.write(characteristic, data, writeType)

    override fun observe(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy,
    ): Flow<Observation> = gattResponder.observe(characteristic, backpressure)

    override fun observeValues(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy,
    ): Flow<ByteArray> = gattResponder.observeValues(characteristic, backpressure)

    override suspend fun readDescriptor(descriptor: Descriptor): ByteArray = gattResponder.readDescriptor(descriptor)

    override suspend fun writeDescriptor(
        descriptor: Descriptor,
        data: ByteArray,
    ): Unit = gattResponder.writeDescriptor(descriptor, data)

    override suspend fun openL2capChannel(
        psm: Int,
        secure: Boolean,
        mtu: Int?,
    ): L2capChannel = gattResponder.openL2capChannel(psm, secure, mtu)

    override suspend fun openIsochronousChannel(): IsochronousChannel = gattResponder.openIsochronousChannel()

    override suspend fun receivePastSync(): PeriodicAdvertisingSync = gattResponder.receivePastSync()

    @com.atruedev.kmpble.ExperimentalBleApi
    override suspend fun requestDirectionFinding(parameters: DirectionFindingParameters): DirectionFindingResult =
        gattResponder.requestDirectionFinding(parameters)

    override suspend fun readRssi(): Int = gattResponder.readRssi()

    override suspend fun requestMtu(mtu: Int): Int = gattResponder.requestMtu(mtu)

    @com.atruedev.kmpble.ExperimentalBleApi
    override suspend fun requestConnectionPriority(priority: ConnectionPriority): Boolean =
        gattResponder.requestConnectionPriority(priority)

    @com.atruedev.kmpble.ExperimentalBleApi
    override suspend fun requestConnectionParameterUpdate(
        params: ConnectionParameters,
    ): ConnectionParameterUpdateResult? =
        gattResponder.requestConnectionParameterUpdate(params, onConnectionParameterUpdateHandler)

    @com.atruedev.kmpble.ExperimentalBleApi
    override suspend fun requestConnectionSubrating(
        parameters: ConnectionSubratingParameters,
    ): ConnectionSubratingResult = gattResponder.requestConnectionSubrating(parameters)

    @com.atruedev.kmpble.ExperimentalBleApi
    override suspend fun setPreferredPhy(
        tx: Phy,
        rx: Phy,
    ): PhyResult? = gattResponder.setPreferredPhy(tx, rx)

    @com.atruedev.kmpble.ExperimentalBleApi
    override suspend fun readPhy(): PhyResult? = gattResponder.readPhy()

    @com.atruedev.kmpble.ExperimentalBleApi
    override val phyUpdate: Flow<PhyUpdate> = gattResponder.phyUpdate
    override val dataLengthParameters: StateFlow<DataLengthParameters?> get() = gattResponder.dataLengthParameters

    private fun checkNotClosed() {
        check(!closed) { "Peripheral is closed" }
    }
}
