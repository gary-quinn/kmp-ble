package com.atruedev.kmpble.gatt.internal

import com.atruedev.kmpble.gatt.BackpressureStrategy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Internal event type for observation flows. Richer than public Observation to support
 * reconnection lifecycle (distinguishing temporary disconnect from permanent disconnect).
 */
internal sealed interface ObservationEvent {
    data class Value(
        val data: ByteArray,
    ) : ObservationEvent {
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
internal data class ObservationKey(
    val serviceUuid: Uuid,
    val charUuid: Uuid,
)

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
 *
 * Serialization: Mutable state is accessed exclusively via [serialDispatcher]
 * (`limitedParallelism(1)`), consistent with the rest of the codebase.
 * The @Volatile [observationsSnapshot] provides lock-free reads from non-suspend
 * contexts (platform callback threads).
 */
@OptIn(ExperimentalUuidApi::class)
internal class ObservationRegistry(
    dispatcher: CoroutineDispatcher,
) {
    /**
     * Serial dispatcher for mutable state access. Defaults to the provided dispatcher
     * which should be a `limitedParallelism(1)` dispatcher from the owning Peripheral.
     */
    private val serialDispatcher: CoroutineDispatcher = dispatcher
    private val observations = mutableMapOf<ObservationKey, TrackedObservation>()

    /**
     * Snapshot of observations for lock-free reads from non-suspend contexts.
     * Updated on [serialDispatcher] whenever [observations] changes. Reads are safe
     * from any thread because the reference is @Volatile and the Map is immutable.
     */
    @Volatile
    private var observationsSnapshot = mapOf<ObservationKey, TrackedObservation>()

    /** Optional callback invoked when the set of active observations changes. */
    internal var onObservationsChanged: ((Set<PersistedObservation>) -> Unit)? = null

    private fun updateSnapshot() {
        observationsSnapshot = observations.toMap()
    }

    private fun notifyObservationsChanged() {
        onObservationsChanged?.invoke(
            observationsSnapshot.values
                .map { tracked ->
                    PersistedObservation(tracked.key, tracked.backpressure)
                }.toSet(),
        )
    }

    suspend fun subscribe(
        key: ObservationKey,
        backpressure: BackpressureStrategy,
    ): TrackedObservation {
        var keyAdded = false
        val tracked =
            withContext(serialDispatcher) {
                val sizeBefore = observations.size
                observations
                    .getOrPut(key) {
                        TrackedObservation(
                            key = key,
                            backpressure = backpressure,
                            flow = MutableSharedFlow(extraBufferCapacity = OBSERVATION_BUFFER_CAPACITY),
                        )
                    }.also {
                        it.collectorCount++
                        keyAdded = observations.size > sizeBefore
                        if (keyAdded) updateSnapshot()
                    }
            }
        if (keyAdded) notifyObservationsChanged()
        return tracked
    }

    /**
     * Unsubscribe from a characteristic. Decrements the collector count.
     * Returns true if this was the last collector (caller should disable CCCD).
     *
     * Uses [NonCancellable] because this is called from flow onCompletion handlers
     * where the coroutine may already be cancelled. Cleanup must complete regardless.
     */
    suspend fun unsubscribe(key: ObservationKey): Boolean {
        val result =
            withContext(NonCancellable + serialDispatcher) {
                val tracked = observations[key] ?: return@withContext false
                tracked.collectorCount--
                if (tracked.collectorCount <= 0) {
                    observations.remove(key)
                    updateSnapshot()
                    true
                } else {
                    false
                }
            }
        if (result) notifyObservationsChanged()
        return result
    }

    /** Called on disconnect. Does NOT clear observations - they persist for reconnection. */
    fun onDisconnect() {
        // No-op for registry; emitter handles the Disconnected emission
    }

    /** Called when reconnection exhausts max attempts (permanent disconnect). */
    suspend fun onPermanentDisconnect() {
        withContext(serialDispatcher) {
            for (tracked in observations.values) {
                tracked.flow.tryEmit(ObservationEvent.PermanentlyDisconnected)
            }
            observations.clear()
            updateSnapshot()
        }
        notifyObservationsChanged()
    }

    /** Returns list of observation keys that need CCCD re-enabled on reconnect. */
    suspend fun getObservationsToResubscribe(): List<ObservationKey> =
        withContext(serialDispatcher) {
            observations.keys.toList()
        }

    /** Complete a specific observation (e.g., when characteristic no longer exists after reconnect). */
    suspend fun completeObservation(key: ObservationKey) {
        withContext(serialDispatcher) {
            observations[key]?.flow?.tryEmit(ObservationEvent.PermanentlyDisconnected)
            observations.remove(key)
            updateSnapshot()
        }
        notifyObservationsChanged()
    }

    /** Check if there are any active collectors for a characteristic. */
    suspend fun hasCollectors(key: ObservationKey): Boolean =
        withContext(serialDispatcher) {
            (observations[key]?.collectorCount ?: 0) > 0
        }

    /** Terminal cleanup. Safe without dispatcher: called after scope cancellation guarantees no concurrent [withContext] blocks are in-flight. */
    fun clear() {
        for (tracked in observations.values) {
            tracked.flow.tryEmit(ObservationEvent.PermanentlyDisconnected)
        }
        observations.clear()
        updateSnapshot()
    }

    /** Provides read-only access to the immutable snapshot for non-suspend contexts. */
    fun snapshot(): Map<ObservationKey, TrackedObservation> = observationsSnapshot

    /** Serial dispatcher for mutable state access. */
    internal val dispatcher: CoroutineDispatcher = serialDispatcher

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
