package com.atruedev.kmpble.gatt.internal

import kotlin.uuid.ExperimentalUuidApi

/**
 * Android no-op implementation. Android handles BLE background differently
 * (foreground services, not state restoration).
 */
@OptIn(ExperimentalUuidApi::class)
internal actual class ObservationPersistence actual constructor() {
    actual fun save(keys: Set<ObservationKey>) { /* no-op on Android */ }
    actual fun restore(): Set<ObservationKey> = emptySet()
    actual fun clear() { /* no-op on Android */ }
}
