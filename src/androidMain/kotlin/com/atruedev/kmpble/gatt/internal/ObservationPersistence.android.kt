package com.atruedev.kmpble.gatt.internal

import kotlin.uuid.ExperimentalUuidApi

/**
 * Android no-op implementation. Android handles BLE background differently
 * (foreground services, not state restoration).
 */
@OptIn(ExperimentalUuidApi::class)
internal actual class ObservationPersistence actual constructor() {
    actual fun save(peripheralId: String, observations: Set<PersistedObservation>) { /* no-op on Android */ }
    actual fun restore(peripheralId: String): Set<PersistedObservation> = emptySet()
    actual fun clear(peripheralId: String) { /* no-op on Android */ }
}
