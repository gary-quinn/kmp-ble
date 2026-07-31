package com.atruedev.kmpble.peripheral.internal

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.peripheral.Peripheral
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Prevents duplicate [Peripheral] instances for the same physical device.
 * Lock-free via [AtomicReference] CAS - no blocking, no suspend, no TOCTOU.
 *
 * The map holds a [Lazy] per identifier rather than a realized [Peripheral]. [factory]'s
 * side effects (assigning the native peripheral's delegate, registering into
 * CentralDelegateState's shared connection-callback map) must run at most once per
 * identifier: previously `factory()` ran eagerly for every racing caller, so two callers
 * could each construct a full instance before the CAS resolved, and the loser's later
 * close() (or simply its constructor clobbering shared native state after the winner had
 * already started using it) could corrupt the survivor's CoreBluetooth wiring. Wrapping
 * the candidate in a SYNCHRONIZED [Lazy] means only the caller that wins the identifier's
 * slot ever evaluates [factory] - a losing candidate is discarded unevaluated, so its
 * factory() body never runs, and a caller that hits the existing-entry fast path safely
 * blocks on the same evaluation instead of racing a second one.
 */
@OptIn(ExperimentalAtomicApi::class)
internal object PeripheralRegistry {
    private val registry = AtomicReference(mapOf<Identifier, Lazy<Peripheral>>())

    internal fun getOrCreate(
        identifier: Identifier,
        factory: () -> Peripheral,
    ): Peripheral {
        registry.load()[identifier]?.let { return it.value }

        val candidate = lazy(LazyThreadSafetyMode.SYNCHRONIZED, factory)
        while (true) {
            val current = registry.load()
            current[identifier]?.let { return it.value }
            if (registry.compareAndSet(current, current + (identifier to candidate))) {
                return candidate.value
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
