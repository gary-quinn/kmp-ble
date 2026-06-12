package com.atruedev.kmpble.testing

import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.connection.internal.ConnectionEvent
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
    /** Drives the state machine with a [ConnectionEvent]. */
    internal suspend fun simulateEvent(event: ConnectionEvent): State {
        checkNotClosed()
        return context.processEvent(event)
    }

    /**
     * Drives the state machine from [State.Connected.Ready] to
     * [State.Connected.BondingChange].
     */
    public suspend fun simulateBondStateChange() {
        checkNotClosed()
        context.processEvent(ConnectionEvent.BondStateChanged)
    }

    /**
     * Drives the state machine from [State.Connected.Ready] to
     * [State.Connected.ServiceChanged]. Services remain populated (now stale)
     * until rediscovery completes.
     */
    public suspend fun simulateServiceChangedIndication() {
        checkNotClosed()
        context.processEvent(ConnectionEvent.ServiceChangedIndication)
    }

    /**
     * Drives the state machine from [State.Connected.ServiceChanged] back to
     * [State.Connected.Ready].
     */
    public suspend fun simulateRediscoverySucceeded() {
        checkNotClosed()
        context.processEvent(ConnectionEvent.RediscoverySucceeded)
    }

    /**
     * Simulate a disconnect event. This will emit [com.atruedev.kmpble.gatt.Observation.Disconnected]
     * to all active observations but NOT complete them - they persist for reconnection.
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
            ConnectionEvent.ConnectionLost(ConnectionLost("Max attempts exhausted")),
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
