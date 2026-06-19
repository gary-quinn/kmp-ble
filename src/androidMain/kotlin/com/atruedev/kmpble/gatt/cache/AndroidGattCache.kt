package com.atruedev.kmpble.gatt.cache

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.gatt.DiscoveredService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory LRU cache for discovered GATT services.
 *
 * Uses [LinkedHashMap] with access-order eviction. When the cache exceeds
 * [maxSize], the least-recently-accessed entry is removed.
 *
 * Thread-safe: all public methods use [Mutex] for structured concurrency
 * instead of [synchronized].
 */
internal class AndroidGattCache(
    private val maxSize: Int,
) : GattCache {
    private val mutex = Mutex()

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

    override suspend fun get(identifier: Identifier): List<DiscoveredService>? = mutex.withLock { cache[identifier] }

    override suspend fun put(
        identifier: Identifier,
        services: List<DiscoveredService>,
    ) {
        mutex.withLock { cache[identifier] = services }
    }

    override suspend fun invalidate(identifier: Identifier) {
        mutex.withLock { cache.remove(identifier) }
    }

    override suspend fun clear() {
        mutex.withLock { cache.clear() }
    }
}

public actual fun createGattCache(maxSize: Int): GattCache = AndroidGattCache(maxSize)
