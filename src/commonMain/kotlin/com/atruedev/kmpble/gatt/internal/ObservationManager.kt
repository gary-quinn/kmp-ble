package com.atruedev.kmpble.gatt.internal

import com.atruedev.kmpble.gatt.BackpressureStrategy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal class ObservationManager(
    dispatcher: CoroutineDispatcher,
) {
    private val registry = ObservationRegistry(dispatcher)
    private val emitter = ObservationEmitter(registry, registry.dispatcher)

    /** Optional callback invoked when the set of active observations changes. */
    internal var onObservationsChanged: ((Set<PersistedObservation>) -> Unit)?
        get() = registry.onObservationsChanged
        set(value) { registry.onObservationsChanged = value }

    /**
     * Subscribe to a characteristic's notifications/indications.
     * Returns a Flow that emits [ObservationEvent]s.
     *
     * The flow survives disconnects:
     * - On disconnect: emits [ObservationEvent.Disconnected]
     * - On reconnect: resumes emitting [ObservationEvent.Value]
     * - On permanent disconnect: emits [ObservationEvent.PermanentlyDisconnected] and completes
     */
    suspend fun subscribe(
        serviceUuid: Uuid,
        charUuid: Uuid,
        backpressure: BackpressureStrategy,
    ): Flow<ObservationEvent> = emitter.subscribe(serviceUuid, charUuid, backpressure)

    /**
     * Unsubscribe from a characteristic. Decrements the collector count.
     * Returns true if this was the last collector (caller should disable CCCD).
     */
    suspend fun unsubscribe(
        serviceUuid: Uuid,
        charUuid: Uuid,
    ): Boolean = registry.unsubscribe(ObservationKey(serviceUuid, charUuid))

    /**
     * Emit a value to all collectors observing this characteristic.
     * Called from GATT callback when notification/indication is received.
     */
    suspend fun emitValue(
        serviceUuid: Uuid,
        charUuid: Uuid,
        value: ByteArray,
    ) = emitter.emitValue(serviceUuid, charUuid, value)

    /**
     * Emit a value to observation flow. Non-suspend version for use from GATT callbacks.
     */
    fun emitByUuid(
        serviceUuid: Uuid,
        charUuid: Uuid,
        value: ByteArray,
    ) = emitter.emitByUuid(serviceUuid, charUuid, value)

    /**
     * Called on disconnect. Emits [ObservationEvent.Disconnected] to all active observations.
     * Does NOT clear observations - they persist for reconnection.
     */
    fun onDisconnect() = emitter.onDisconnect()

    /**
     * Called when reconnection exhausts max attempts (permanent disconnect).
     * Emits [ObservationEvent.PermanentlyDisconnected] to all observations, then clears them.
     */
    suspend fun onPermanentDisconnect() = registry.onPermanentDisconnect()

    /** Returns list of observation keys that need CCCD re-enabled on reconnect. */
    suspend fun getObservationsToResubscribe(): List<ObservationKey> =
        registry.getObservationsToResubscribe()

    /** Complete a specific observation (e.g., when characteristic no longer exists after reconnect). */
    suspend fun completeObservation(key: ObservationKey) =
        registry.completeObservation(key)

    /** Check if there are any active collectors for a characteristic. */
    suspend fun hasCollectors(
        serviceUuid: Uuid,
        charUuid: Uuid,
    ): Boolean = registry.hasCollectors(ObservationKey(serviceUuid, charUuid))

    /** Terminal cleanup. */
    fun clear() = registry.clear()
}