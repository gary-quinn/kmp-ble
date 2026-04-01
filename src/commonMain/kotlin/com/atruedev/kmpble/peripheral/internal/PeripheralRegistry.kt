package com.atruedev.kmpble.peripheral.internal

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * Prevents duplicate [Peripheral] instances for the same physical device.
 * Entries are cleared when the peripheral is closed or on adapter reset.
 *
 * Thread-safety: Copy-on-write with @Volatile. [getOrCreate] has a narrow TOCTOU window
 * where two threads may invoke [factory] for the same identifier. The second write wins;
 * the loser's Peripheral is explicitly closed to prevent scope leaks.
 */
internal object PeripheralRegistry {
    @Volatile
    private var registry = mapOf<Identifier, Peripheral>()

    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    internal fun getOrCreate(
        identifier: Identifier,
        factory: () -> Peripheral,
    ): Peripheral {
        registry[identifier]?.let { return it }
        val peripheral = factory()
        val existing = registry[identifier]
        if (existing != null) {
            // Lost the race — close the duplicate to prevent scope leak
            cleanupScope.launch { peripheral.close() }
            return existing
        }
        registry = registry + (identifier to peripheral)
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
