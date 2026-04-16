package com.atruedev.kmpble.testing

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.connection.internal.ConnectionEvent
import com.atruedev.kmpble.error.BleError
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.ConnectionFailed
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.error.OperationFailed
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.gatt.internal.ObservationEvent
import com.atruedev.kmpble.gatt.internal.ObservationManager
import com.atruedev.kmpble.gatt.internal.applyBackpressure
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.l2cap.L2capException
import com.atruedev.kmpble.peripheral.Peripheral
import com.atruedev.kmpble.peripheral.internal.PeripheralContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlin.time.Duration
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
) : Peripheral {
    private val context = PeripheralContext(identifier)
    private val observationManager = ObservationManager()
    private var closed = false
    private val cccdWritesState = MutableStateFlow(emptyList<CccdWrite>())

    override val state: StateFlow<State> get() = context.state
    override val bondState: StateFlow<com.atruedev.kmpble.bonding.BondState> get() = context.bondState
    override val services: StateFlow<List<DiscoveredService>?> get() = context.services
    override val maximumWriteValueLength: StateFlow<Int> get() = context.maximumWriteValueLength

    public data class CccdWrite(
        val serviceUuid: Uuid,
        val charUuid: Uuid,
        val enabled: Boolean,
    )

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

    internal suspend fun simulateEvent(event: ConnectionEvent): State {
        checkNotClosed()
        return context.processEvent(event)
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

    // --- GATT Operations ---

    override suspend fun read(characteristic: Characteristic): ByteArray {
        checkNotClosed()
        checkConnected()
        val config = findConfig(characteristic)
        applyDelay(config)
        checkFailWith(config)
        val handler =
            config?.readHandler
                ?: throw UnsupportedOperationException("No onRead handler for ${characteristic.uuid}")
        return handler()
    }

    override suspend fun write(
        characteristic: Characteristic,
        data: ByteArray,
        writeType: WriteType,
    ) {
        checkNotClosed()
        checkConnected()
        val config = findConfig(characteristic)
        applyDelay(config)
        checkFailWith(config)
        val handler = config?.writeHandler
        if (handler != null) {
            handler(data, writeType)
        } else {
            val hasWriteProperty =
                characteristic.properties.write ||
                    characteristic.properties.writeWithoutResponse ||
                    characteristic.properties.signedWrite
            if (!hasWriteProperty) {
                throw BleException(
                    GattError("write", GattStatus.WriteNotPermitted),
                )
            }
        }
    }

    override fun observe(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy,
    ): Flow<Observation> {
        checkNotClosed()
        val config = findConfig(characteristic)
        failWithFlow<Observation>(config)?.let { return it }
        val handler = config?.observeHandler
        return if (handler != null) {
            handler()
                .map<ByteArray, Observation> { Observation.Value(it) }
                .applyBackpressure(backpressure)
        } else {
            observeInternal(characteristic, backpressure) { event ->
                when (event) {
                    is ObservationEvent.Value -> emit(Observation.Value(event.data))
                    is ObservationEvent.Disconnected -> emit(Observation.Disconnected)
                    is ObservationEvent.PermanentlyDisconnected -> emit(Observation.Disconnected)
                }
            }
        }
    }

    override fun observeValues(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy,
    ): Flow<ByteArray> {
        checkNotClosed()
        val config = findConfig(characteristic)
        failWithFlow<ByteArray>(config)?.let { return it }
        val handler = config?.observeHandler
        return if (handler != null) {
            handler().applyBackpressure(backpressure)
        } else {
            observeInternal(characteristic, backpressure) { event ->
                when (event) {
                    is ObservationEvent.Value -> emit(event.data)
                    // Transparent reconnection — ObservationManager re-subscribes on reconnect
                    is ObservationEvent.Disconnected -> Unit
                    is ObservationEvent.PermanentlyDisconnected -> Unit
                }
            }
        }
    }

    private fun <T> observeInternal(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy,
        mapper: suspend FlowCollector<T>.(ObservationEvent) -> Unit,
    ): Flow<T> {
        val serviceUuid = characteristic.serviceUuid
        val charUuid = characteristic.uuid

        return flow {
            val eventFlow = observationManager.subscribe(serviceUuid, charUuid, backpressure)
            eventFlow.collect { event -> mapper(event) }
        }.onStart {
            if (context.state.value is State.Connected.Ready) {
                recordCccdWrite(serviceUuid, charUuid, enabled = true)
            }
        }.applyBackpressure(backpressure)
            .onCompletion {
                val wasLastCollector = observationManager.unsubscribe(serviceUuid, charUuid)
                if (wasLastCollector && context.state.value is State.Connected) {
                    recordCccdWrite(serviceUuid, charUuid, enabled = false)
                }
            }
    }

    override suspend fun readDescriptor(descriptor: Descriptor): ByteArray {
        checkNotClosed()
        checkConnected()
        return byteArrayOf()
    }

    override suspend fun writeDescriptor(
        descriptor: Descriptor,
        data: ByteArray,
    ) {
        checkNotClosed()
        checkConnected()
    }

    override suspend fun openL2capChannel(
        psm: Int,
        secure: Boolean,
        mtu: Int?,
    ): L2capChannel {
        checkNotClosed()
        if (context.state.value !is State.Connected) {
            throw L2capException.NotConnected("Peripheral is not connected (state: ${context.state.value})")
        }
        val handler =
            onL2capHandler
                ?: throw L2capException.NotSupported("No onOpenL2capChannel handler configured")
        return handler(psm)
    }

    override suspend fun readRssi(): Int {
        checkNotClosed()
        checkConnected()
        return -50
    }

    override suspend fun requestMtu(mtu: Int): Int {
        checkNotClosed()
        checkConnected()
        context.updateMtu(mtu)
        return mtu
    }

    // --- Internal ---

    private suspend fun applyDelay(config: FakeCharacteristicConfig?) {
        val duration = config?.respondAfterDuration ?: return
        if (duration > Duration.ZERO) {
            delay(duration)
        }
    }

    private fun checkFailWith(config: FakeCharacteristicConfig?) {
        val error = config?.failWithError ?: return
        throw BleException(error)
    }

    private fun <T> failWithFlow(config: FakeCharacteristicConfig?): Flow<T>? {
        val error = config?.failWithError ?: return null
        return flow { throw BleException(error) }
    }

    private fun findConfig(characteristic: Characteristic): FakeCharacteristicConfig? =
        characteristicConfigs.firstOrNull {
            it.characteristic.serviceUuid == characteristic.serviceUuid &&
                it.characteristic.uuid == characteristic.uuid
        }

    private fun recordCccdWrite(
        serviceUuid: Uuid,
        charUuid: Uuid,
        enabled: Boolean,
    ) {
        cccdWritesState.update { it + CccdWrite(serviceUuid, charUuid, enabled) }
    }

    private fun checkNotClosed() {
        check(!closed) { "Peripheral is closed" }
    }

    private fun checkConnected() {
        check(context.state.value is State.Connected) {
            "Peripheral is not connected (state: ${context.state.value})"
        }
    }

    // --- Test Simulation Methods ---

    /**
     * Simulate a disconnect event. This will emit [Observation.Disconnected] to all active
     * observations but NOT complete them — they persist for reconnection.
     */
    public suspend fun simulateDisconnect(error: BleError = ConnectionLost("Simulated disconnect")) {
        checkNotClosed()
        context.processEvent(ConnectionEvent.ConnectionLost(error))
        observationManager.onDisconnect()
    }

    /**
     * Simulate a reconnection. This will:
     * 1. Transition through connecting states
     * 2. Re-discover services
     * 3. Re-enable CCCD for all active observations
     */
    public suspend fun simulateReconnect(newServices: List<DiscoveredService>? = null) {
        checkNotClosed()
        if (newServices != null) {
            fakeServices = newServices
        }

        context.processEvent(ConnectionEvent.ConnectRequested)
        context.gattQueue.start()
        context.processEvent(ConnectionEvent.LinkEstablished)
        context.processEvent(ConnectionEvent.ServicesDiscovered)
        context.updateServices(fakeServices)

        resubscribeObservations()

        context.processEvent(ConnectionEvent.ConfigurationComplete)
    }

    private suspend fun resubscribeObservations() {
        val toResubscribe = observationManager.getObservationsToResubscribe()
        for (key in toResubscribe) {
            val char = findCharacteristic(key.serviceUuid, key.charUuid)
            if (char != null) {
                recordCccdWrite(key.serviceUuid, key.charUuid, enabled = true)
            } else {
                observationManager.completeObservation(key)
            }
        }
    }

    /**
     * Simulate permanent disconnect (max reconnection attempts exhausted).
     * This will complete all observation flows.
     */
    public suspend fun simulatePermanentDisconnect() {
        checkNotClosed()
        context.processEvent(ConnectionEvent.ConnectionLost(ConnectionLost("Max attempts exhausted")))
        observationManager.onPermanentDisconnect()
    }

    /** Simulate a characteristic notification by emitting [value] to active observers. */
    public suspend fun emitObservationValue(
        serviceUuid: Uuid,
        charUuid: Uuid,
        value: ByteArray,
    ) {
        checkNotClosed()
        observationManager.emitValue(serviceUuid, charUuid, value)
    }

    /** Convenience overload accepting short or full UUID strings. */
    public suspend fun emitObservationValue(
        serviceUuid: String,
        charUuid: String,
        value: ByteArray,
    ) {
        emitObservationValue(
            com.atruedev.kmpble.scanner
                .uuidFrom(serviceUuid),
            com.atruedev.kmpble.scanner
                .uuidFrom(charUuid),
            value,
        )
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
    ): Boolean = observationManager.hasCollectors(serviceUuid, charUuid)
}
