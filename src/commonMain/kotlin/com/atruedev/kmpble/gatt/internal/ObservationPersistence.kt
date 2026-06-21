package com.atruedev.kmpble.gatt.internal

import com.atruedev.kmpble.gatt.BackpressureStrategy
import kotlin.uuid.ExperimentalUuidApi

/**
 * An observation entry persisted for state restoration.
 * Bundles the UUID pair with the user's original backpressure choice
 * so restored observations match the original subscription behavior.
 */
@OptIn(ExperimentalUuidApi::class)
internal data class PersistedObservation(
    val key: ObservationKey,
    val backpressure: BackpressureStrategy,
)

/**
 * Persists observation entries so they survive app termination during iOS state restoration.
 * On Android, observations are persisted via SharedPreferences.
 *
 * On iOS, entries (UUID pairs + backpressure strategy) are stored in NSUserDefaults as JSON.
 * The data is low-sensitivity metadata (standard BLE UUIDs), not user credentials.
 * See ObservationPersistence.ios.kt for rationale.
 */
@OptIn(ExperimentalUuidApi::class)
internal expect class ObservationPersistence() {
    /**
     * Persist the current set of active observations for a specific peripheral.
     * Called whenever observations change (subscribe/unsubscribe).
     */
    fun save(
        peripheralId: String,
        observations: Set<PersistedObservation>,
    )

    /**
     * Restore previously persisted observations for a specific peripheral.
     * Returns empty set if no persisted state exists or deserialization fails.
     */
    fun restore(peripheralId: String): Set<PersistedObservation>

    /**
     * Clear persisted observation state for a specific peripheral.
     * Called on Peripheral.close() or when state restoration is disabled.
     */
    fun clear(peripheralId: String)
}

/**
 * Serialize a [BackpressureStrategy] to a string for persistent storage.
 * Used by Android (SharedPreferences) and iOS (NSUserDefaults) platform implementations.
 *
 * Encoding:
 *   - [BackpressureStrategy.Latest] -> "latest"
 *   - [BackpressureStrategy.Buffer] -> "buffer:<capacity>"
 *   - [BackpressureStrategy.Unbounded] -> "unbounded"
 */
internal fun serializeBackpressure(strategy: BackpressureStrategy): String =
    when (strategy) {
        is BackpressureStrategy.Latest -> "latest"
        is BackpressureStrategy.Buffer -> "buffer:${strategy.capacity}"
        is BackpressureStrategy.Unbounded -> "unbounded"
    }

/**
 * Deserialize a string back into a [BackpressureStrategy].
 * Returns [BackpressureStrategy.Latest] as the safe default for unrecognized or null input.
 */
internal fun deserializeBackpressure(value: String?): BackpressureStrategy =
    when {
        value == null -> BackpressureStrategy.Latest
        value == "latest" -> BackpressureStrategy.Latest
        value.startsWith("buffer:") -> {
            val capacity = value.removePrefix("buffer:").toIntOrNull() ?: 64
            BackpressureStrategy.Buffer(capacity)
        }
        value == "unbounded" -> BackpressureStrategy.Unbounded
        else -> BackpressureStrategy.Latest
    }
