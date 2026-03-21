package com.atruedev.kmpble.gatt.internal

import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
internal actual class ObservationPersistence actual constructor() {
    actual fun save(peripheralId: String, observations: Set<PersistedObservation>) {}
    actual fun restore(peripheralId: String): Set<PersistedObservation> = emptySet()
    actual fun clear(peripheralId: String) {}
}
