package com.atruedev.kmpble.gatt.internal

import kotlin.uuid.ExperimentalUuidApi

/**
 * JVM implementation of ObservationPersistence using an in-memory map.
 *
 * For testing and JVM-only environments. Data does not survive JVM
 * restarts. Use shared-mutable-state patterns for cross-test persistence.
 *
 * On Android and iOS, the platform actual implementations provide durable
 * storage (SharedPreferences and NSUserDefaults respectively).
 */
@OptIn(ExperimentalUuidApi::class)
internal actual class ObservationPersistence actual constructor() {
    /**
     * Shared mutable map for all ObservationPersistence instances.
     * All instances share the same map to simulate cross-session persistence
     * in tests (e.g., creating a new Peripheral restores saved observations).
     */
    private val store get() = sharedStore

    actual fun save(
        peripheralId: String,
        observations: Set<PersistedObservation>,
    ) {
        if (observations.isEmpty()) {
            clear(peripheralId)
            return
        }
        store[peripheralId] = observations.toSet()
    }

    actual fun restore(peripheralId: String): Set<PersistedObservation> = store[peripheralId] ?: emptySet()

    actual fun clear(peripheralId: String) {
        store.remove(peripheralId)
    }

    companion object {
        private val sharedStore = mutableMapOf<String, Set<PersistedObservation>>()

        /** Clear all persisted state. Useful for test cleanup. */
        fun resetAll() {
            sharedStore.clear()
        }
    }
}
