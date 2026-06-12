package com.atruedev.kmpble.gatt.internal

import com.atruedev.kmpble.gatt.BackpressureStrategy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal class ObservationEmitter(
    private val registry: ObservationRegistry,
    private val serialDispatcher: CoroutineDispatcher,
) {
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
    ): Flow<ObservationEvent> {
        val key = ObservationKey(serviceUuid, charUuid)
        val tracked = registry.subscribe(key, backpressure)

        return tracked.flow.transformWhile { event ->
            emit(event)
            event !is ObservationEvent.PermanentlyDisconnected
        }
    }

    /**
     * Emit a value to all collectors observing this characteristic.
     * Called from GATT callback when notification/indication is received.
     */
    suspend fun emitValue(
        serviceUuid: Uuid,
        charUuid: Uuid,
        value: ByteArray,
    ) {
        withContext(serialDispatcher) {
            val key = ObservationKey(serviceUuid, charUuid)
            registry.snapshot()[key]?.flow?.tryEmit(ObservationEvent.Value(value))
        }
    }

    /**
     * Emit a value to observation flow. Non-suspend version for use from GATT callbacks.
     *
     * Thread-safety: Reads from an immutable @Volatile snapshot from the registry,
     * so no serialization needed. tryEmit on MutableSharedFlow is thread-safe.
     * Worst case during concurrent subscribe/unsubscribe is a missed emit
     * (acceptable for transient race windows).
     */
    fun emitByUuid(
        serviceUuid: Uuid,
        charUuid: Uuid,
        value: ByteArray,
    ) {
        val key = ObservationKey(serviceUuid, charUuid)
        registry.snapshot()[key]?.flow?.tryEmit(ObservationEvent.Value(value))
    }

    /**
     * Called on disconnect. Emits [ObservationEvent.Disconnected] to all active observations.
     * Does NOT clear observations - they persist for reconnection.
     *
     * Thread-safety: Reads from an immutable @Volatile snapshot from the registry.
     */
    fun onDisconnect() {
        for (tracked in registry.snapshot().values) {
            tracked.flow.tryEmit(ObservationEvent.Disconnected)
        }
    }
}