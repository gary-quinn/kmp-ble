package com.atruedev.kmpble.peripheral.internal

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.peripheral.Peripheral
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Prevents duplicate [Peripheral] instances for the same physical device.
 * Lock-free via [AtomicReference] CAS - no blocking, no suspend, no TOCTOU.
 */
@OptIn(ExperimentalAtomicApi::class)
internal object PeripheralRegistry {
    private val registry = AtomicReference(mapOf<Identifier, Peripheral>())

    internal fun getOrCreate(
        identifier: Identifier,
        factory: () -> Peripheral,
    ): Peripheral {
        registry.load()[identifier]?.let { return it }

        val peripheral = factory()
        while (true) {
            val current = registry.load()
            current[identifier]?.let {
                try {
                    peripheral.close()
                } catch (_: Exception) {
                }
                return it
            }
            if (registry.compareAndSet(current, current + (identifier to peripheral))) {
                return peripheral
            }
        }
    }

    internal fun remove(identifier: Identifier) {
        while (true) {
            val current = registry.load()
            val updated = current - identifier
            if (registry.compareAndSet(current, updated)) return
        }
    }

    internal fun identifiers(): Set<String> =
        registry
            .load()
            .keys
            .mapTo(mutableSetOf()) { it.value }

    internal fun clear() {
        registry.store(emptyMap())
    }
}
