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
 * On Android, this is a no-op.
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
