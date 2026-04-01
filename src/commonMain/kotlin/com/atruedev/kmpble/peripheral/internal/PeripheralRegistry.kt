package com.atruedev.kmpble.peripheral.internal

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.peripheral.Peripheral
import kotlin.concurrent.Volatile

/**
 * Prevents duplicate [Peripheral] instances for the same physical device.
 * Entries are cleared when the peripheral is closed or on adapter reset.
 *
 * Thread-safety: @Volatile copy-on-write immutable map. [getOrCreate] has a TOCTOU
 * window where concurrent calls for the same identifier may both invoke [factory].
 * The duplicate is closed immediately to prevent CoroutineScope leaks. The registry
 * may briefly hold a Peripheral that differs from what a racing thread received —
 * this is acceptable because the race requires near-simultaneous scan results for the
 * same device, and both Peripheral instances are valid (they wrap the same hardware).
 *
 * A fully linearizable implementation would require either `synchronized` (JVM-only)
 * or making [getOrCreate] suspend (breaking the public `toPeripheral()` API).
 * Neither trade-off is justified given the narrow race window.
 */
internal object PeripheralRegistry {
    @Volatile
    private var registry = mapOf<Identifier, Peripheral>()

    internal fun getOrCreate(
        identifier: Identifier,
        factory: () -> Peripheral,
    ): Peripheral {
        registry[identifier]?.let { return it }
        val peripheral = factory()
        // Re-check after factory() — another thread may have written first.
        // If so, close our duplicate and return the winner.
        val snapshot = registry
        val existing = snapshot[identifier]
        if (existing != null) {
            try {
                peripheral.close()
            } catch (_: Throwable) {
            }
            return existing
        }
        registry = snapshot + (identifier to peripheral)
        return peripheral
    }

    internal fun remove(identifier: Identifier) {
        registry = registry - identifier
    }

    internal fun identifiers(): Set<String> = registry.keys.map { it.value }.toSet()

    internal fun clear() {
        registry = emptyMap()
    }
}
