package com.atruedev.kmpble.gatt.internal

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Persists observation keys so they survive app termination during iOS state restoration.
 * On Android, this is a no-op.
 *
 * Observation keys (service UUID + characteristic UUID pairs) are stored encrypted
 * using the platform keychain/keystore. These UUIDs can reveal what health data
 * the app monitors, so encryption provides defense in depth for medical apps.
 */
@OptIn(ExperimentalUuidApi::class)
internal expect class ObservationPersistence() {
    /**
     * Persist the current set of active observation keys.
     * Called whenever observations change (subscribe/unsubscribe).
     */
    fun save(keys: Set<ObservationKey>)

    /**
     * Restore previously persisted observation keys.
     * Returns empty set if no persisted state exists or decryption fails.
     */
    fun restore(): Set<ObservationKey>

    /**
     * Clear all persisted observation state.
     * Called on Peripheral.close() or when state restoration is disabled.
     */
    fun clear()
}
