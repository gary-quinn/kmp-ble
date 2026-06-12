package com.atruedev.kmpble.testing

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.ConnectionPriority
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.connection.internal.ConnectionEvent
import com.atruedev.kmpble.error.BleError
import com.atruedev.kmpble.error.ConnectionFailed
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.error.OperationFailed
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.gatt.internal.ObservationManager
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.peripheral.Peripheral
import com.atruedev.kmpble.peripheral.PhyResult
import com.atruedev.kmpble.peripheral.internal.PeripheralContext
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
    private val observationDispatcher: kotlinx.coroutines.CoroutineDispatcher =
        Dispatchers.Default.limitedParallelism(
            1,
        ),
) : Peripheral {
    private val context = PeripheralContext(identifier)
    private val observationManager = ObservationManager(observationDispatcher)
    private var closed = false
    private val cccdWritesState = MutableStateFlow<List<CccdWrite>>(emptyList())

    private val connectionSimulator =
        FakeConnectionSimulator(
            context = context,
            observationManager = observationManager,
            fakeServices = fakeServices,
            cccdWritesState = cccdWritesState,
            closedFlag = { closed },
        )

    private val gattResponder =
        FakeGattResponder(
            context = context,
            observationManager = observationManager,
            characteristicConfigs = characteristicConfigs,
            onL2capHandler = onL2capHandler,
            cccdWritesState = cccdWritesState,
            closedFlag = { closed },
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

    internal suspend fun simulateEvent(event: ConnectionEvent): State = connectionSimulator.simulateEvent(event)

    /**
     * Drives the state machine from [State.Connected.Ready] to
     * [State.Connected.BondingChange].
     */
    public suspend fun simulateBondStateChange() {
        connectionSimulator.simulateBondStateChange()
    }

    /**
     * Drives the state machine from [State.Connected.Ready] to
     * [State.Connected.ServiceChanged]. Services remain populated (now stale)
     * until rediscovery completes.
     */
    public suspend fun simulateServiceChangedIndication() {
        connectionSimulator.simulateServiceChangedIndication()
    }

    /**
     * Drives the state machine from [State.Connected.ServiceChanged] back to
     * [State.Connected.Ready].
     */
    public suspend fun simulateRediscoverySucceeded() {
        connectionSimulator.simulateRediscoverySucceeded()
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

    override suspend fun readRssi(): Int = gattResponder.readRssi()

    override suspend fun requestMtu(mtu: Int): Int = gattResponder.requestMtu(mtu)

    @com.atruedev.kmpble.ExperimentalBleApi
    override suspend fun requestConnectionPriority(priority: ConnectionPriority): Boolean =
        gattResponder.requestConnectionPriority(priority)

    @com.atruedev.kmpble.ExperimentalBleApi
    override suspend fun setPreferredPhy(
        tx: Phy,
        rx: Phy,
    ): PhyResult? = gattResponder.setPreferredPhy(tx, rx)

    // --- Test Simulation Methods (delegated to FakeConnectionSimulator) ---

    /**
     * Simulate a disconnect event. This will emit [Observation.Disconnected] to all active
     * observations but NOT complete them - they persist for reconnection.
     */
    public suspend fun simulateDisconnect(error: BleError = ConnectionLost("Simulated disconnect")) {
        connectionSimulator.simulateDisconnect(error)
    }

    /**
     * Simulate a reconnection. This will:
     * 1. Transition through connecting states
     * 2. Re-discover services
     * 3. Re-enable CCCD for all active observations
     */
    public suspend fun simulateReconnect(newServices: List<DiscoveredService>? = null) {
        connectionSimulator.simulateReconnect(newServices)
    }

    /**
     * Simulate permanent disconnect (max reconnection attempts exhausted).
     * This will complete all observation flows.
     */
    public suspend fun simulatePermanentDisconnect() {
        connectionSimulator.simulatePermanentDisconnect()
    }

    /** Simulate a characteristic notification by emitting [value] to active observers. */
    public suspend fun emitObservationValue(
        serviceUuid: Uuid,
        charUuid: Uuid,
        value: ByteArray,
    ) {
        connectionSimulator.emitObservationValue(serviceUuid, charUuid, value)
    }

    /** Convenience overload accepting short or full UUID strings. */
    public suspend fun emitObservationValue(
        serviceUuid: String,
        charUuid: String,
        value: ByteArray,
    ) {
        connectionSimulator.emitObservationValue(serviceUuid, charUuid, value)
    }

    /** Returns all CCCD writes recorded during observation setup/teardown. */
    public fun getCccdWrites(): List<CccdWrite> = cccdWritesState.value

    /** Clears recorded CCCD writes. */
    public fun clearCccdWrites() {
        cccdWritesState.value = emptyList()
    }

    /** Returns `true` if there are active collectors for the given characteristic. */
    public suspend fun hasCollectors(
        serviceUuid: Uuid,
        charUuid: Uuid,
    ): Boolean = connectionSimulator.hasCollectors(serviceUuid, charUuid)

    private fun checkNotClosed() {
        check(!closed) { "Peripheral is closed" }
    }
}
