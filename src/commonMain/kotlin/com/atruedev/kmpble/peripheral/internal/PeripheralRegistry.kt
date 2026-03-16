package com.atruedev.kmpble.peripheral.internal

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.peripheral.Peripheral
import kotlin.concurrent.Volatile

/**
 * Prevents duplicate [Peripheral] instances for the same physical device.
 * Uses weak-like semantics: entries are cleared when the peripheral is closed
 * or on adapter reset. Thread-safe via copy-on-write immutable map.
 */
internal object PeripheralRegistry {

    @Volatile
    private var registry = mapOf<Identifier, Peripheral>()

    internal fun getOrCreate(identifier: Identifier, factory: () -> Peripheral): Peripheral {
        registry[identifier]?.let { existing ->
            return existing
        }
        val peripheral = factory()
        registry = registry + (identifier to peripheral)
        return peripheral
    }

    internal fun remove(identifier: Identifier) {
        registry = registry - identifier
    }

    internal fun clear() {
        registry = emptyMap()
    }
}
