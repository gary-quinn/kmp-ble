package com.atruedev.kmpble.gatt.cache

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.gatt.DiscoveredService

/**
 * In-memory LRU cache for discovered GATT services.
 *
 * Uses [LinkedHashMap] with access-order eviction. When the cache exceeds
 * [maxSize], the least-recently-accessed entry is removed.
 *
 * Thread-safe: all public methods synchronize on the internal map.
 */
internal class AndroidGattCache(
    private val maxSize: Int,
) : GattCache {
    private val lock = Any()

    @Suppress("UNCHECKED_CAST")
    private val cache =
        object : LinkedHashMap<Identifier, List<DiscoveredService>>(
            16,
            0.75f,
            true,
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<Identifier, List<DiscoveredService>>?,
            ): Boolean = size > maxSize
        }

    override fun get(identifier: Identifier): List<DiscoveredService>? = synchronized(lock) { cache[identifier] }

    override fun put(
        identifier: Identifier,
        services: List<DiscoveredService>,
    ) {
        synchronized(lock) { cache[identifier] = services }
    }

    override fun invalidate(identifier: Identifier) {
        synchronized(lock) { cache.remove(identifier) }
    }

    override fun clear() {
        synchronized(lock) { cache.clear() }
    }
}

public actual fun createGattCache(maxSize: Int): GattCache = AndroidGattCache(maxSize)
