package com.atruedev.kmpble.gatt.internal

import com.atruedev.kmpble.gatt.BackpressureStrategy
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transformWhile
import kotlin.concurrent.Volatile
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
     * Snapshot of observations for lock-free reads from non-suspend contexts.
     * Updated under [mutex] whenever [observations] changes. Reads are safe from
     * any thread because the reference is @Volatile and the Map is immutable.
     */
    @Volatile
    private var observationsSnapshot = mapOf<ObservationKey, TrackedObservation>()

    private fun updateSnapshot() {
        observationsSnapshot = observations.toMap()
    }

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
                    flow = MutableSharedFlow(extraBufferCapacity = OBSERVATION_BUFFER_CAPACITY),
                )
            }.also {
                it.collectorCount++
                updateSnapshot()
            }
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
                updateSnapshot()
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
     * Thread-safety: Reads from an immutable @Volatile snapshot, so no locking is needed.
     * tryEmit on MutableSharedFlow is thread-safe. Worst case during concurrent
     * subscribe/unsubscribe is a missed emit (acceptable for transient race windows).
     */
    fun emitByUuid(serviceUuid: Uuid, charUuid: Uuid, value: ByteArray) {
        val key = ObservationKey(serviceUuid, charUuid)
        observationsSnapshot[key]?.flow?.tryEmit(ObservationEvent.Value(value))
    }

    /**
     * Called on disconnect. Emits [ObservationEvent.Disconnected] to all active observations.
     * Does NOT clear observations — they persist for reconnection.
     *
     * Thread-safety: Reads from an immutable @Volatile snapshot.
     */
    fun onDisconnect() {
        for (tracked in observationsSnapshot.values) {
            tracked.flow.tryEmit(ObservationEvent.Disconnected)
        }
    }

    /**
     * Called when reconnection exhausts max attempts (permanent disconnect).
     * Emits [ObservationEvent.PermanentlyDisconnected] to all observations, then clears them.
     *
     * Thread-safety: Reads snapshot, then clears both map and snapshot atomically
     * (single-writer assumption — only called from the peripheral's serialized context).
     */
    fun onPermanentDisconnect() {
        val snapshot = observationsSnapshot
        for (tracked in snapshot.values) {
            tracked.flow.tryEmit(ObservationEvent.PermanentlyDisconnected)
        }
        observations.clear()
        observationsSnapshot = emptyMap()
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
            updateSnapshot()
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
     *
     * Thread-safety: Reads snapshot for iteration, then clears both map and snapshot.
     */
    fun clear() {
        val snapshot = observationsSnapshot
        for (tracked in snapshot.values) {
            tracked.flow.tryEmit(ObservationEvent.PermanentlyDisconnected)
        }
        observations.clear()
        observationsSnapshot = emptyMap()
    }

    private companion object {
        const val OBSERVATION_BUFFER_CAPACITY = 64
    }
}

internal fun <T> Flow<T>.applyBackpressure(strategy: BackpressureStrategy): Flow<T> =
    when (strategy) {
        is BackpressureStrategy.Latest -> conflate()
        is BackpressureStrategy.Buffer -> buffer(strategy.capacity, BufferOverflow.DROP_OLDEST)
        is BackpressureStrategy.Unbounded -> buffer(Channel.UNLIMITED)
    }
