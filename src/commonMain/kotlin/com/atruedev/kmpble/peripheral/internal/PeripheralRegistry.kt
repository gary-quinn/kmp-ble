package com.atruedev.kmpble.peripheral.internal

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.peripheral.Peripheral
import kotlin.concurrent.Volatile

/**
 * Prevents duplicate [Peripheral] instances for the same physical device.
 * Uses weak-like semantics: entries are cleared when the peripheral is closed
 * or on adapter reset.
 *
 * Thread-safety: Uses @Volatile copy-on-write immutable map. The read-check-write
 * in [getOrCreate] has a narrow TOCTOU window: if two threads call with the same
 * identifier simultaneously, both may invoke [factory]. The second write wins and
 * the extra Peripheral is GC'd immediately. This is acceptable because:
 * 1. The race requires near-simultaneous scan results for the same device
 * 2. The consequence is a single wasted allocation (no resource leak)
 * 3. Platform-level synchronization (Mutex/synchronized) would require either
 *    expect/actual declarations or making this function suspend
 */
internal object PeripheralRegistry {

    @Volatile
    private var registry = mapOf<Identifier, Peripheral>()

    internal fun getOrCreate(identifier: Identifier, factory: () -> Peripheral): Peripheral {
        registry[identifier]?.let { return it }
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
