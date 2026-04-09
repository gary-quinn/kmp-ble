package com.atruedev.kmpble.gatt.internal

import com.atruedev.kmpble.gatt.BackpressureStrategy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transformWhile
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
 * Not a data class: collectorCount is mutable session state, not structural identity.
 */
@OptIn(ExperimentalUuidApi::class)
internal class TrackedObservation(
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
internal class ObservationManager(
    dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
) {
    /**
     * Optional callback invoked when the set of active observations changes.
     * Used by iOS state restoration to persist observations (keys + backpressure)
     * to NSUserDefaults. Set by IosPeripheral when state restoration is enabled.
     *
     * For subscribe/unsubscribe: invoked after the snapshot is updated, outside
     * the serial dispatcher. For onPermanentDisconnect/completeObservation: invoked
     * inline on the caller's context (the peripheral's serialized dispatcher).
     */
    internal var onObservationsChanged: ((Set<PersistedObservation>) -> Unit)? = null

    /**
     * Serial dispatcher for mutable state access. Defaults to [Dispatchers.Unconfined]
     * because ObservationManager is always owned by a Peripheral that already provides
     * serialization via its own `limitedParallelism(1)` dispatcher. The Unconfined
     * dispatcher runs inline — no thread hop, no test dispatcher conflict.
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

    private fun updateSnapshot() {
        observationsSnapshot = observations.toMap()
    }

    /**
     * Fire the persistence callback with observation keys and their backpressure strategies.
     * Reads from the @Volatile snapshot (safe without serialization).
     */
    private fun notifyObservationsChanged() {
        onObservationsChanged?.invoke(
            observationsSnapshot.values
                .map { tracked ->
                    PersistedObservation(tracked.key, tracked.backpressure)
                }.toSet(),
        )
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

        return tracked.flow.transformWhile { event ->
            emit(event)
            event !is ObservationEvent.PermanentlyDisconnected
        }
    }

    /**
     * Unsubscribe from a characteristic. Decrements the collector count.
     * Returns true if this was the last collector (caller should disable CCCD).
     *
     * Uses [NonCancellable] because this is called from flow onCompletion handlers
     * where the coroutine may already be cancelled. Cleanup must complete regardless.
     */
    suspend fun unsubscribe(
        serviceUuid: Uuid,
        charUuid: Uuid,
    ): Boolean {
        val key = ObservationKey(serviceUuid, charUuid)
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
            observations[key]?.flow?.tryEmit(ObservationEvent.Value(value))
        }
    }

    /**
     * Emit a value to observation flow. Non-suspend version for use from GATT callbacks.
     *
     * Thread-safety: Reads from an immutable @Volatile snapshot, so no serialization needed.
     * tryEmit on MutableSharedFlow is thread-safe. Worst case during concurrent
     * subscribe/unsubscribe is a missed emit (acceptable for transient race windows).
     */
    fun emitByUuid(
        serviceUuid: Uuid,
        charUuid: Uuid,
        value: ByteArray,
    ) {
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
     * Must be called from the peripheral's serialized dispatcher.
     */
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

    /**
     * Returns list of observation keys that need CCCD re-enabled on reconnect.
     */
    suspend fun getObservationsToResubscribe(): List<ObservationKey> =
        withContext(serialDispatcher) {
            observations.keys.toList()
        }

    /**
     * Complete a specific observation (e.g., when characteristic no longer exists after reconnect).
     */
    suspend fun completeObservation(key: ObservationKey) {
        withContext(serialDispatcher) {
            observations[key]?.flow?.tryEmit(ObservationEvent.PermanentlyDisconnected)
            observations.remove(key)
            updateSnapshot()
        }
        notifyObservationsChanged()
    }

    /**
     * Check if there are any active collectors for a characteristic.
     */
    suspend fun hasCollectors(
        serviceUuid: Uuid,
        charUuid: Uuid,
    ): Boolean {
        val key = ObservationKey(serviceUuid, charUuid)
        return withContext(serialDispatcher) {
            (observations[key]?.collectorCount ?: 0) > 0
        }
    }

    /**
     * Terminal cleanup. Safe without dispatcher: called after scope cancellation
     * guarantees no concurrent [withContext] blocks are in-flight.
     */
    fun clear() {
        for (tracked in observations.values) {
            tracked.flow.tryEmit(ObservationEvent.PermanentlyDisconnected)
        }
        observations.clear()
        updateSnapshot()
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
