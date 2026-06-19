package com.atruedev.kmpble.gatt.cache

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.scanner.uuidFrom
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for the LRU eviction behavior of [AndroidGattCache].
 *
 * Uses a standalone LRU implementation that mirrors the Android production code.
 * The LRU eviction cannot be tested through [createGattCache] on JVM because
 * the JVM factory returns [JvmGattCache] (a stub).
 */
public class AndroidGattCacheTest {
    private fun buildCache(maxSize: Int = 64): GattCache = LruGattCache(maxSize)

    private val identifierA = Identifier("AA:BB:CC:DD:EE:FF")
    private val identifierB = Identifier("11:22:33:44:55:66")
    private val identifierC = Identifier("AA:BB:CC:DD:EE:00")

    private fun buildServices(uuid: String = "180d"): List<DiscoveredService> =
        listOf(
            DiscoveredService(
                uuid = uuidFrom(uuid),
                characteristics =
                    listOf(
                        Characteristic(
                            serviceUuid = uuidFrom(uuid),
                            uuid = uuidFrom("2a37"),
                            properties =
                                Characteristic.Properties(
                                    notify = true,
                                ),
                        ),
                    ),
            ),
        )

    @Test
    fun `put and get round-trip`() = runBlocking {
        val cache = buildCache()
        cache.put(identifierA, buildServices())
        val cached = cache.get(identifierA)
        assertNotNull(cached)
        assertEquals(1, cached.size)
    }

    @Test
    fun `invalidate removes entry`() = runBlocking {
        val cache = buildCache()
        cache.put(identifierA, buildServices())
        cache.invalidate(identifierA)
        assertNull(cache.get(identifierA))
    }

    @Test
    fun `clear removes all entries`() = runBlocking {
        val cache = buildCache()
        cache.put(identifierA, buildServices())
        cache.put(identifierB, buildServices("180a"))
        cache.clear()
        assertNull(cache.get(identifierA))
        assertNull(cache.get(identifierB))
    }

    @Test
    fun `LRU evicts least recently used when capacity exceeded`() = runBlocking {
        val cache = buildCache(maxSize = 2)
        cache.put(identifierA, buildServices("180d"))
        cache.put(identifierB, buildServices("180a"))
        // Access A to make B the least-recently-used
        cache.get(identifierA)
        // Insert C -- B should be evicted
        cache.put(identifierC, buildServices("180f"))
        assertNotNull(cache.get(identifierA))
        assertNotNull(cache.get(identifierC))
        assertNull(cache.get(identifierB))
    }

    @Test
    fun `LRU access order promotes entry`() = runBlocking {
        val cache = buildCache(maxSize = 2)
        cache.put(identifierA, buildServices("180d"))
        cache.put(identifierB, buildServices("180a"))
        // Access A multiple times
        cache.get(identifierA)
        cache.get(identifierA)
        // Insert C -- B should be evicted (A was accessed most recently)
        cache.put(identifierC, buildServices("180f"))
        assertNotNull(cache.get(identifierA))
        assertNotNull(cache.get(identifierC))
        assertNull(cache.get(identifierB))
    }

    @Test
    fun `put replaces existing entry`() = runBlocking {
        val cache = buildCache()
        cache.put(identifierA, buildServices("180d"))
        cache.put(identifierA, buildServices("180a"))
        val cached = cache.get(identifierA)
        assertNotNull(cached)
        assertEquals(uuidFrom("180a"), cached[0].uuid)
    }

    @Test
    fun `multiple identifiers independent`() = runBlocking {
        val cache = buildCache()
        cache.put(identifierA, buildServices("180d"))
        cache.put(identifierB, buildServices("180a"))
        assertEquals(uuidFrom("180d"), cache.get(identifierA)!![0].uuid)
        assertEquals(uuidFrom("180a"), cache.get(identifierB)!![0].uuid)
    }
}

/**
 * Standalone LRU [GattCache] for testing -- mirrors [AndroidGattCache].
 *
 * Uses [Mutex] for structured concurrency instead of [synchronized].
 */
internal class LruGattCache(
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

    override suspend fun get(identifier: Identifier): List<DiscoveredService>? =
        mutex.withLock { cache[identifier] }

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
