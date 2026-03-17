package com.atruedev.kmpble.gatt.internal

import com.atruedev.kmpble.gatt.BackpressureStrategy
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Internal event type for observation flows. Richer than public Observation to support
 * reconnection lifecycle (distinguishing temporary disconnect from permanent disconnect).
 */
internal sealed interface ObservationEvent {
    data class Value(val data: ByteArray) : ObservationEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Value) return false
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    data object Disconnected : ObservationEvent
    data object PermanentlyDisconnected : ObservationEvent
}

/**
 * Key for tracking observations by UUID pair (not object reference).
 * Required because Characteristic objects are session-scoped and replaced on reconnect.
 */
@OptIn(ExperimentalUuidApi::class)
internal data class ObservationKey(val serviceUuid: Uuid, val charUuid: Uuid)

/**
 * Tracks an active observation with its flow and metadata.
 */
@OptIn(ExperimentalUuidApi::class)
internal data class TrackedObservation(
    val key: ObservationKey,
    val backpressure: BackpressureStrategy,
    val flow: MutableSharedFlow<ObservationEvent>,
    var collectorCount: Int = 0,
)

/**
 * Manages observation flows for BLE characteristic notifications/indications.
 * Supports reconnection resilience: observations persist across disconnects and
 * auto-resubscribe when the connection is restored.
 */
@OptIn(ExperimentalUuidApi::class)
internal class ObservationManager {

    private val mutex = Mutex()
    private val observations = mutableMapOf<ObservationKey, TrackedObservation>()

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
        val tracked = mutex.withLock {
            observations.getOrPut(key) {
                TrackedObservation(
                    key = key,
                    backpressure = backpressure,
                    flow = MutableSharedFlow(extraBufferCapacity = 64),
                )
            }.also { it.collectorCount++ }
        }

        // Use transformWhile to complete the flow when PermanentlyDisconnected is emitted
        return tracked.flow.transformWhile { event ->
            emit(event)
            event !is ObservationEvent.PermanentlyDisconnected
        }
    }

    /**
     * Unsubscribe from a characteristic. Decrements the collector count.
     * Returns true if this was the last collector (caller should disable CCCD).
     */
    suspend fun unsubscribe(serviceUuid: Uuid, charUuid: Uuid): Boolean {
        val key = ObservationKey(serviceUuid, charUuid)
        return mutex.withLock {
            val tracked = observations[key] ?: return@withLock false
            tracked.collectorCount--
            if (tracked.collectorCount <= 0) {
                observations.remove(key)
                true
            } else {
                false
            }
        }
    }

    /**
     * Emit a value to all collectors observing this characteristic.
     * Called from GATT callback when notification/indication is received.
     */
    suspend fun emitValue(serviceUuid: Uuid, charUuid: Uuid, value: ByteArray) {
        val key = ObservationKey(serviceUuid, charUuid)
        mutex.withLock {
            observations[key]?.flow?.tryEmit(ObservationEvent.Value(value))
        }
    }

    /**
     * Emit a value to observation flow. Non-suspend version for use from GATT callbacks.
     *
     * Thread-safety: This method reads from the observations map without locking.
     * It is safe to call from any thread because:
     * 1. Map reads are atomic for reference types
     * 2. tryEmit on MutableSharedFlow is thread-safe
     * 3. The worst case is a missed emit during concurrent modification (acceptable)
     *
     * @param serviceUuid Service UUID
     * @param charUuid Characteristic UUID
     * @param value The value to emit
     */
    fun emitByUuid(serviceUuid: Uuid, charUuid: Uuid, value: ByteArray) {
        val key = ObservationKey(serviceUuid, charUuid)
        observations[key]?.flow?.tryEmit(ObservationEvent.Value(value))
    }

    /**
     * Called on disconnect. Emits [ObservationEvent.Disconnected] to all active observations.
     * Does NOT clear observations — they persist for reconnection.
     *
     * Non-suspend version: Uses tryEmit which is non-blocking. Safe to call from
     * any context including scopes that may be cancelled (e.g., during close()).
     */
    fun onDisconnect() {
        for (tracked in observations.values) {
            tracked.flow.tryEmit(ObservationEvent.Disconnected)
        }
    }

    /**
     * Called when reconnection exhausts max attempts (permanent disconnect).
     * Emits [ObservationEvent.PermanentlyDisconnected] to all observations, then clears them.
     *
     * Non-suspend version: Uses tryEmit which is non-blocking.
     */
    fun onPermanentDisconnect() {
        for (tracked in observations.values) {
            tracked.flow.tryEmit(ObservationEvent.PermanentlyDisconnected)
        }
        observations.clear()
    }

    /**
     * Returns list of observation keys that need CCCD re-enabled on reconnect.
     */
    suspend fun getObservationsToResubscribe(): List<ObservationKey> {
        return mutex.withLock {
            observations.keys.toList()
        }
    }

    /**
     * Complete a specific observation (e.g., when characteristic no longer exists after reconnect).
     */
    suspend fun completeObservation(key: ObservationKey) {
        mutex.withLock {
            observations[key]?.flow?.tryEmit(ObservationEvent.PermanentlyDisconnected)
            observations.remove(key)
        }
    }

    /**
     * Check if there are any active collectors for a characteristic.
     */
    suspend fun hasCollectors(serviceUuid: Uuid, charUuid: Uuid): Boolean {
        val key = ObservationKey(serviceUuid, charUuid)
        return mutex.withLock {
            (observations[key]?.collectorCount ?: 0) > 0
        }
    }

    /**
     * Terminal cleanup — clear all observations.
     * Called on Peripheral.close().
     */
    fun clear() {
        observations.values.forEach { tracked ->
            tracked.flow.tryEmit(ObservationEvent.PermanentlyDisconnected)
        }
        observations.clear()
    }
}

internal fun <T> Flow<T>.applyBackpressure(strategy: BackpressureStrategy): Flow<T> =
    when (strategy) {
        is BackpressureStrategy.Latest -> conflate()
        is BackpressureStrategy.Buffer -> buffer(strategy.capacity, BufferOverflow.DROP_OLDEST)
        is BackpressureStrategy.Unbounded -> buffer(Channel.UNLIMITED)
    }
