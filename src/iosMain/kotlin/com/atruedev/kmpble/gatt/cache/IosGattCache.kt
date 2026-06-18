package com.atruedev.kmpble.gatt.cache

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.gatt.DiscoveredService

/**
 * Passthrough [GattCache] for iOS.
 *
 * CoreBluetooth caches discovered services and their characteristics/descriptors
 * automatically. [CBPeripheral.discoverServices] returns cached results on
 * reconnection without a GATT round-trip, so an application-level cache is
 * unnecessary.
 *
 * [get] always returns `null` (cache miss), forcing callers to fall through
 * to [Peripheral.services] which returns CoreBluetooth's internally cached data.
 */
internal object IosGattCache : GattCache {
    override fun get(identifier: Identifier): List<DiscoveredService>? = null

    override fun put(
        identifier: Identifier,
        services: List<DiscoveredService>,
    ) {
        // CoreBluetooth handles caching natively
    }

    override fun invalidate(identifier: Identifier) {
        // CoreBluetooth handles cache invalidation on disconnect
    }

    override fun clear() {
        // CoreBluetooth cache lifecycle is managed by the OS
    }
}

public actual fun createGattCache(maxSize: Int): GattCache = IosGattCache
