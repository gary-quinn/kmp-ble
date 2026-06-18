package com.atruedev.kmpble.gatt.cache

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.gatt.DiscoveredService

/**
 * Stub [GattCache] for JVM (no Bluetooth stack).
 *
 * Always returns `null` from [get]; all other methods are no-ops.
 * JVM consumers that need a real cache (e.g., desktop BLE dongles via
 * D-Bus or serial) should provide their own [GattCache] implementation.
 */
internal object JvmGattCache : GattCache {
    override fun get(identifier: Identifier): List<DiscoveredService>? = null

    override fun put(
        identifier: Identifier,
        services: List<DiscoveredService>,
    ) {
        // No Bluetooth stack available on JVM
    }

    override fun invalidate(identifier: Identifier) {
        // No-op on JVM
    }

    override fun clear() {
        // No-op on JVM
    }
}

public actual fun createGattCache(maxSize: Int): GattCache = JvmGattCache
