package com.atruedev.kmpble.quirks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class QuirkRegistryJvmTest {
    // === QuirkKey tests ===

    @Test
    fun `quirk key returns default when no description`() {
        val key = QuirkKey("test", 42)
        assertEquals("test", key.name)
        assertEquals(42, key.default)
    }

    @Test
    fun `quirk key describe returns null for default value`() {
        val key = QuirkKey("test", 42) { v, d -> if (v != d) "changed" else null }
        assertTrue(key.describe(42) == null)
    }

    @Test
    fun `quirk key describe returns message for non-default value`() {
        val key = QuirkKey("test", 42) { v, d -> if (v != d) "changed" else null }
        assertEquals("changed", key.describe(99))
    }

    // === BleQuirks well-known keys ===

    @Test
    fun `BleQuirks BondBeforeConnect defaults to false`() {
        assertEquals(false, BleQuirks.BondBeforeConnect.default)
    }

    @Test
    fun `BleQuirks GattRetryDelay defaults to 300ms`() {
        assertEquals(300.milliseconds, BleQuirks.GattRetryDelay.default)
    }

    @Test
    fun `BleQuirks GattRetryCount defaults to 1`() {
        assertEquals(1, BleQuirks.GattRetryCount.default)
    }

    // === DeviceInfo tests ===

    @Test
    fun `device info generates match keys in correct order`() {
        val device = DeviceInfo("samsung", "sm-g991b", "g991bxxu1aua1")
        val keys = device.matchKeys
        assertEquals("samsung:sm-g991b:g991bxxu1aua1", keys[0])
        assertEquals("samsung:sm-g991b", keys[1])
        assertEquals("samsung:sm-g99", keys[2])
        assertEquals("samsung", keys[3])
    }

    @Test
    fun `device info current returns non-null on JVM`() {
        val device = DeviceInfo.current()
        assertEquals("jvm", device.manufacturer)
        assertTrue(device.model.isNotEmpty())
    }

    // === DeviceMatch tests ===

    @Test
    fun `matchesAny finds manufacturer match`() {
        val device = DeviceInfo("samsung", "sm-g991b", "g991bxxu1aua1")
        assertTrue(DeviceMatch.matchesAny(device, setOf("samsung")))
    }

    @Test
    fun `matchesAny finds model match`() {
        val device = DeviceInfo("samsung", "sm-g991b", "g991bxxu1aua1")
        assertTrue(DeviceMatch.matchesAny(device, setOf("samsung:sm-g991b")))
    }

    @Test
    fun `matchesAny returns false for non-matching set`() {
        val device = DeviceInfo("samsung", "sm-g991b", "g991bxxu1aua1")
        assertFalse(DeviceMatch.matchesAny(device, setOf("google", "oneplus")))
    }

    @Test
    fun `matchFirst returns most specific match`() {
        val device = DeviceInfo("google", "pixel 7", "tp1a.221005.002")
        val result =
            DeviceMatch.matchFirst(
                device,
                mapOf(
                    "google" to 1,
                    "google:pixel 7" to 2,
                ),
            )
        assertEquals(2, result)
    }

    @Test
    fun `matchFirst returns null when nothing matches`() {
        val device = DeviceInfo("apple", "iphone14,7", "ios 17.0")
        val result = DeviceMatch.matchFirst(device, mapOf("samsung" to 1))
        assertEquals(null, result)
    }

    @Test
    fun `model prefix match captures series`() {
        val device = DeviceInfo("samsung", "sm-g991b", "g991bxxu1aua1")
        // Only the prefix "sm-g99" is in the set (model prefix from matchKeys)
        val result =
            DeviceMatch.matchesAny(
                device,
                setOf("samsung:sm-g99"),
            )
        assertTrue(result)
    }

    // === QuirkRegistry.Builder tests ===

    @Test
    fun `builder resolves most recent registration`() {
        val registry =
            QuirkRegistry.createForTest(DeviceInfo("samsung", "sm-g991b", "g991bxxu1aua1")) {
                register(BleQuirks.GattRetryCount, 2, "samsung")
                register(BleQuirks.GattRetryCount, 5, "samsung")
            }
        assertEquals(5, registry.resolve(BleQuirks.GattRetryCount))
    }

    @Test
    fun `builder falls back to default when no match`() {
        val registry =
            QuirkRegistry.createForTest(DeviceInfo("acme", "widget", "v1")) {
                register(BleQuirks.GattRetryCount, 3, "samsung")
            }
        assertEquals(1, registry.resolve(BleQuirks.GattRetryCount))
    }

    @Test
    fun `builder with lambda match`() {
        val registry =
            QuirkRegistry.createForTest(DeviceInfo("test", "model", "v1")) {
                register(BleQuirks.BondBeforeConnect, true) { it.manufacturer == "test" }
            }
        assertTrue(registry.resolve(BleQuirks.BondBeforeConnect))
    }

    @Test
    fun `builder with device key map`() {
        val registry =
            QuirkRegistry.createForTest(DeviceInfo("apple", "iphone14,7", "ios 17.0")) {
                register(
                    BleQuirks.ConnectionTimeout,
                    mapOf("apple" to 45.seconds, "apple:iphone14,7" to 35.seconds),
                )
            }
        // Most specific match wins
        assertEquals(35.seconds, registry.resolve(BleQuirks.ConnectionTimeout))
    }

    @Test
    fun `builder with provider interface`() {
        val provider =
            object : QuirkProvider {
                override fun contribute(builder: QuirkRegistry.Builder) {
                    builder.register(BleQuirks.GattRetryCount, 3, "test")
                }
            }
        val registry =
            QuirkRegistry.createForTest(DeviceInfo("test", "model", "v1")) {
                addProvider(provider)
            }
        assertEquals(3, registry.resolve(BleQuirks.GattRetryCount))
    }

    // === describe() tests ===

    @Test
    fun `describe includes manufacturer and model`() {
        val registry =
            QuirkRegistry.createForTest(DeviceInfo("google", "pixel 7", "tp1a.221005.002")) {
                register(BleQuirks.GattRetryCount, 3, "google")
            }
        val desc = registry.describe()
        assertTrue(desc.contains("google/pixel 7"))
        assertTrue(desc.contains("retry=3x"))
    }

    @Test
    fun `describe shows no quirks for unknown device`() {
        val registry =
            QuirkRegistry.createForTest(DeviceInfo("acme", "widget-3000", "v1.0"))
        assertTrue(registry.describe().contains("no device-specific quirks"))
    }

    // === User overrides take priority ===

    @Test
    fun `user override takes priority over provider`() {
        val provider =
            object : QuirkProvider {
                override fun contribute(builder: QuirkRegistry.Builder) {
                    builder.register(BleQuirks.GattRetryCount, 2, "test")
                }
            }
        val registry =
            QuirkRegistry.createForTest(DeviceInfo("test", "model", "v1")) {
                addProvider(provider)
                register(BleQuirks.GattRetryCount, 5) { it.manufacturer == "test" }
            }
        assertEquals(5, registry.resolve(BleQuirks.GattRetryCount))
    }

    // === getInstance tests ===

    @Test
    fun `getInstance returns a registry on JVM`() {
        val registry = QuirkRegistry.getInstance()
        assertNotNull(registry)
        // JVM has no real device, so defaults should apply
        assertEquals(300.milliseconds, registry.resolve(BleQuirks.GattRetryDelay))
    }

    @Test
    fun `createForTest is isolated from getInstance`() {
        val testRegistry =
            QuirkRegistry.createForTest(DeviceInfo("samsung", "sm-g991b", "g991bxxu1aua1")) {
                register(BleQuirks.GattRetryCount, 99, "samsung")
            }
        assertEquals(99, testRegistry.resolve(BleQuirks.GattRetryCount))

        val instanceRegistry = QuirkRegistry.getInstance()
        assertTrue(instanceRegistry !== testRegistry)
    }

    // === QuirkKey reference equality ===

    @Test
    fun `quirk keys use reference equality for Builder dedup`() {
        val registry =
            QuirkRegistry.createForTest(DeviceInfo("test", "model", "v1")) {
                register(BleQuirks.GattRetryCount, 2, "test")
                register(BleQuirks.GattRetryCount, 3, "test")
            }
        // Builder sees same key reference, last-write-wins
        assertEquals(3, registry.resolve(BleQuirks.GattRetryCount))
    }
}
