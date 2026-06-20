package com.atruedev.kmpble.gatt.cache

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.gatt.DiscoveredService
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext

/**
 * In-memory LRU cache for discovered GATT services.
 *
 * Uses [LinkedHashMap] with access-order eviction. When the cache exceeds
 * [maxSize], the least-recently-accessed entry is removed.
 *
 * Thread-safe: all public methods are confined to a single-threaded
 * [newSingleThreadContext] dispatcher, serializing access to the
 * non-thread-safe [LinkedHashMap] without locks or atomics.
 */
internal class AndroidGattCache(
    private val maxSize: Int,
) : GattCache {
    private val cacheContext = newSingleThreadContext("gatt-cache")

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

    override suspend fun get(identifier: Identifier): List<DiscoveredService>? =
        withContext(cacheContext) { cache[identifier] }

    override suspend fun put(
        identifier: Identifier,
        services: List<DiscoveredService>,
    ) {
        withContext(cacheContext) { cache[identifier] = services }
    }

    override suspend fun invalidate(identifier: Identifier) {
        withContext(cacheContext) { cache.remove(identifier) }
    }

    override suspend fun clear() {
        withContext(cacheContext) { cache.clear() }
    }
}

public actual fun createGattCache(maxSize: Int): GattCache = AndroidGattCache(maxSize)
