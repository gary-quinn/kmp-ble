package com.atruedev.kmpble.gatt.cache

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.scanner.uuidFrom
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Shared conformance test suite for [GattCache] implementations.
 *
 * Subclass in platform-specific test source sets (jvmTest, iosTest)
 * to verify behavioral consistency across platforms.
 *
 * Platforms that do not support application-level caching (iOS/JVM)
 * return `null` from [GattCache.get] for all identifiers. The conformance
 * tests validate this contract.
 */
public abstract class GattCacheConformanceTest {
    /** Create a cache instance under test. */
    protected abstract fun buildCache(maxSize: Int = 64): GattCache

    /**
     * Whether this platform's cache stores entries.
     *
     * - `true`: Android (LRU cache stores and returns entries)
     * - `false`: iOS (CoreBluetooth handles caching), JVM (no Bluetooth stack)
     */
    protected open val supportsCaching: Boolean = false

    private val identifierA = Identifier("AA:BB:CC:DD:EE:FF")
    private val identifierB = Identifier("11:22:33:44:55:66")

    private fun buildServices(): List<DiscoveredService> =
        listOf(
            DiscoveredService(
                uuid = uuidFrom("180d"),
                characteristics =
                    listOf(
                        Characteristic(
                            serviceUuid = uuidFrom("180d"),
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
    fun `get returns null for unknown identifier`() = runBlocking {
        val cache = buildCache()
        assertNull(cache.get(identifierA))
    }

    @Test
    fun `invalidate unknown identifier is no-op`() = runBlocking {
        val cache = buildCache()
        cache.invalidate(identifierA)
        // No exception thrown
    }

    @Test
    fun `clear empty cache is no-op`() = runBlocking {
        val cache = buildCache()
        cache.clear()
        // No exception thrown
    }

    @Test
    fun `put and get round-trip`() = runBlocking {
        if (!supportsCaching) return@runBlocking
        val cache = buildCache()
        val services = buildServices()
        cache.put(identifierA, services)
        val cached = cache.get(identifierA)
        assertNotNull(cached)
        assertEquals(1, cached.size)
        assertEquals(uuidFrom("180d"), cached[0].uuid)
    }

    @Test
    fun `put replaces existing entry`() = runBlocking {
        if (!supportsCaching) return@runBlocking
        val cache = buildCache()
        val services1 = buildServices()
        val services2 =
            listOf(
                DiscoveredService(
                    uuid = uuidFrom("180a"),
                    characteristics = emptyList(),
                ),
            )
        cache.put(identifierA, services1)
        cache.put(identifierA, services2)
        val cached = cache.get(identifierA)
        assertNotNull(cached)
        assertEquals(1, cached.size)
        assertEquals(uuidFrom("180a"), cached[0].uuid)
    }

    @Test
    fun `invalidate removes entry`() = runBlocking {
        if (!supportsCaching) return@runBlocking
        val cache = buildCache()
        cache.put(identifierA, buildServices())
        cache.invalidate(identifierA)
        assertNull(cache.get(identifierA))
    }

    @Test
    fun `clear removes all entries`() = runBlocking {
        if (!supportsCaching) return@runBlocking
        val cache = buildCache()
        cache.put(identifierA, buildServices())
        cache.put(identifierB, buildServices())
        cache.clear()
        assertNull(cache.get(identifierA))
        assertNull(cache.get(identifierB))
    }

    @Test
    fun `multiple identifiers independent`() = runBlocking {
        if (!supportsCaching) return@runBlocking
        val cache = buildCache()
        val servicesA = buildServices()
        val servicesB =
            listOf(
                DiscoveredService(
                    uuid = uuidFrom("180f"),
                    characteristics = emptyList(),
                ),
            )
        cache.put(identifierA, servicesA)
        cache.put(identifierB, servicesB)
        assertEquals(uuidFrom("180d"), cache.get(identifierA)!![0].uuid)
        assertEquals(uuidFrom("180f"), cache.get(identifierB)!![0].uuid)
    }
}
