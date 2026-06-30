package com.atruedev.kmpble.testing

import com.atruedev.kmpble.peripheral.state.ConnectionState
import com.atruedev.kmpble.peripheral.state.StateTransitionEvent
import com.atruedev.kmpble.error.BleError
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.internal.ObservationManager
import com.atruedev.kmpble.peripheral.internal.PeripheralContext
import com.atruedev.kmpble.scanner.uuidFrom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal class FakeConnectionSimulator(
    private val context: PeripheralContext,
    private val observationManager: ObservationManager,
    private var fakeServices: List<DiscoveredService>,
    private val cccdWritesState: MutableStateFlow<List<FakePeripheral.CccdWrite>>,
    private val closedFlag: () -> Boolean,
) {
    /** Drives the state machine with a [StateTransitionEvent]. */
    internal suspend fun simulateEvent(event: StateTransitionEvent): ConnectionState {
        checkNotClosed()
        return context.processEvent(event)
    }

    /**
     * Drives the state machine from [ConnectionState.Connected.Ready] to
     * [ConnectionState.Connected.BondingChange].
     */
    public suspend fun simulateBondStateChange() {
        checkNotClosed()
        context.processEvent(StateTransitionEvent.BondStateChanged)
    }

    /**
     * Drives the state machine from [ConnectionState.Connected.Ready] to
     * [ConnectionState.Connected.ServiceChanged]. Services remain populated (now stale)
     * until rediscovery completes.
     */
    public suspend fun simulateServiceChangedIndication() {
        checkNotClosed()
        context.processEvent(StateTransitionEvent.ServiceChangedIndication)
    }

    /**
     * Drives the state machine from [ConnectionState.Connected.ServiceChanged] back to
     * [ConnectionState.Connected.Ready].
     */
    public suspend fun simulateRediscoverySucceeded() {
        checkNotClosed()
        context.processEvent(StateTransitionEvent.RediscoverySucceeded)
    }

    /**
     * Simulate a disconnect event. This will emit [com.atruedev.kmpble.gatt.Observation.Disconnected]
     * to all active observations but NOT complete them - they persist for reconnection.
     */
    public suspend fun simulateDisconnect(error: BleError = ConnectionLost("Simulated disconnect")) {
        checkNotClosed()
        context.processEvent(StateTransitionEvent.ConnectionLost(error))
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

        context.processEvent(StateTransitionEvent.ConnectRequested)
        context.gattQueue.start()
        context.processEvent(StateTransitionEvent.LinkEstablished)
        context.processEvent(StateTransitionEvent.ServicesDiscovered)
        context.updateServices(fakeServices)

        resubscribeObservations()

        context.processEvent(StateTransitionEvent.ConfigurationComplete)
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

    private fun findCharacteristic(
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
    ): com.atruedev.kmpble.gatt.Characteristic? =
        context.services.value
            ?.firstOrNull { it.uuid == serviceUuid }
            ?.characteristics
            ?.firstOrNull { it.uuid == characteristicUuid }

    /**
     * Simulate permanent disconnect (max reconnection attempts exhausted).
     * This will complete all observation flows.
     */
    public suspend fun simulatePermanentDisconnect() {
        checkNotClosed()
        context.processEvent(
            StateTransitionEvent.ConnectionLost(ConnectionLost("Max attempts exhausted")),
        )
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
            uuidFrom(serviceUuid),
            uuidFrom(charUuid),
            value,
        )
    }

    /** Returns `true` if there are active collectors for the given characteristic. */
    public suspend fun hasCollectors(
        serviceUuid: Uuid,
        charUuid: Uuid,
    ): Boolean = observationManager.hasCollectors(serviceUuid, charUuid)

    private fun recordCccdWrite(
        serviceUuid: Uuid,
        charUuid: Uuid,
        enabled: Boolean,
    ) {
        cccdWritesState.update { it + FakePeripheral.CccdWrite(serviceUuid, charUuid, enabled) }
    }

    private fun checkNotClosed() {
        check(!closedFlag()) { "Peripheral is closed" }
    }
}
