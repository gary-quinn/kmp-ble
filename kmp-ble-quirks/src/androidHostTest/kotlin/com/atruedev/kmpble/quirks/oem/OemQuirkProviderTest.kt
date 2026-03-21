package com.atruedev.kmpble.quirks.oem

import com.atruedev.kmpble.quirks.BleQuirks
import com.atruedev.kmpble.quirks.DeviceInfo
import com.atruedev.kmpble.quirks.QuirkRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class OemQuirkProviderTest {

    private fun registryFor(manufacturer: String, model: String, display: String): QuirkRegistry =
        QuirkRegistry.createForTest(DeviceInfo(manufacturer, model, display)) {
            addProvider(OemQuirkProvider())
        }

    // === Bond before connect ===

    @Test
    fun `samsung galaxy s21 should bond before connect`() {
        val registry = registryFor("samsung", "sm-g991b", "g991bxxu1aua1")
        assertTrue(registry.resolve(BleQuirks.BondBeforeConnect))
    }

    @Test
    fun `samsung galaxy s20 should bond before connect`() {
        val registry = registryFor("samsung", "sm-g980f", "g980fxxu1aua1")
        assertTrue(registry.resolve(BleQuirks.BondBeforeConnect))
    }

    @Test
    fun `samsung galaxy note 20 should bond before connect`() {
        val registry = registryFor("samsung", "sm-n981b", "n981bxxu1aua1")
        assertTrue(registry.resolve(BleQuirks.BondBeforeConnect))
    }

    @Test
    fun `samsung galaxy a53 should bond before connect`() {
        val registry = registryFor("samsung", "sm-a536b", "a536bxxu1aua1")
        assertTrue(registry.resolve(BleQuirks.BondBeforeConnect))
    }

    @Test
    fun `google pixel should not bond before connect`() {
        val registry = registryFor("google", "pixel 7", "tp1a.221005.002")
        assertFalse(registry.resolve(BleQuirks.BondBeforeConnect))
    }

    @Test
    fun `unknown device should not bond before connect`() {
        val registry = registryFor("unknown", "unknown", "unknown")
        assertFalse(registry.resolve(BleQuirks.BondBeforeConnect))
    }

    // === GATT retry delay ===

    @Test
    fun `google pixel 7 should have 1500ms retry delay`() {
        val registry = registryFor("google", "pixel 7", "tp1a.221005.002")
        assertEquals(1500.milliseconds, registry.resolve(BleQuirks.GattRetryDelay))
    }

    @Test
    fun `google pixel 8 should have 1500ms retry delay`() {
        val registry = registryFor("google", "pixel 8", "ud1a.230803.022")
        assertEquals(1500.milliseconds, registry.resolve(BleQuirks.GattRetryDelay))
    }

    @Test
    fun `google pixel 6 should have 1500ms retry delay`() {
        val registry = registryFor("google", "pixel 6", "sd1a.210817.015.a4")
        assertEquals(1500.milliseconds, registry.resolve(BleQuirks.GattRetryDelay))
    }

    @Test
    fun `google generic model falls back to manufacturer match`() {
        val registry = registryFor("google", "pixel 5", "rq3a.211001.001")
        assertEquals(1.seconds, registry.resolve(BleQuirks.GattRetryDelay))
    }

    @Test
    fun `samsung should have 500ms retry delay`() {
        val registry = registryFor("samsung", "sm-g991b", "g991bxxu1aua1")
        assertEquals(500.milliseconds, registry.resolve(BleQuirks.GattRetryDelay))
    }

    @Test
    fun `unknown device uses default retry delay`() {
        val registry = registryFor("unknown", "unknown", "unknown")
        assertEquals(300.milliseconds, registry.resolve(BleQuirks.GattRetryDelay))
    }

    // === GATT retry count ===

    @Test
    fun `google pixel should retry 3 times`() {
        val registry = registryFor("google", "pixel 7", "tp1a.221005.002")
        assertEquals(3, registry.resolve(BleQuirks.GattRetryCount))
    }

    @Test
    fun `samsung should retry 2 times`() {
        val registry = registryFor("samsung", "sm-g991b", "g991bxxu1aua1")
        assertEquals(2, registry.resolve(BleQuirks.GattRetryCount))
    }

    @Test
    fun `xiaomi should retry 2 times`() {
        val registry = registryFor("xiaomi", "22071219cg", "v14.0.2.0")
        assertEquals(2, registry.resolve(BleQuirks.GattRetryCount))
    }

    @Test
    fun `unknown device retries 1 time`() {
        val registry = registryFor("unknown", "unknown", "unknown")
        assertEquals(1, registry.resolve(BleQuirks.GattRetryCount))
    }

    // === Refresh services on bond ===

    @Test
    fun `xiaomi should refresh services on bond`() {
        val registry = registryFor("xiaomi", "22071219cg", "v14.0.2.0")
        assertTrue(registry.resolve(BleQuirks.RefreshServicesOnBond))
    }

    @Test
    fun `oneplus should refresh services on bond`() {
        val registry = registryFor("oneplus", "le2115", "skq1.211006.001")
        assertTrue(registry.resolve(BleQuirks.RefreshServicesOnBond))
    }

    @Test
    fun `redmi should refresh services on bond`() {
        val registry = registryFor("redmi", "note 11 pro", "v14.0.1.0")
        assertTrue(registry.resolve(BleQuirks.RefreshServicesOnBond))
    }

    @Test
    fun `poco should refresh services on bond`() {
        val registry = registryFor("poco", "m2012k11ag", "v14.0.1.0")
        assertTrue(registry.resolve(BleQuirks.RefreshServicesOnBond))
    }

    @Test
    fun `oppo should refresh services on bond`() {
        val registry = registryFor("oppo", "cph2399", "rp1a.200720.011")
        assertTrue(registry.resolve(BleQuirks.RefreshServicesOnBond))
    }

    @Test
    fun `samsung should not refresh services on bond`() {
        val registry = registryFor("samsung", "sm-g991b", "g991bxxu1aua1")
        assertFalse(registry.resolve(BleQuirks.RefreshServicesOnBond))
    }

    @Test
    fun `unknown device should not refresh services on bond`() {
        val registry = registryFor("unknown", "unknown", "unknown")
        assertFalse(registry.resolve(BleQuirks.RefreshServicesOnBond))
    }

    // === Bond state change timeout ===

    @Test
    fun `xiaomi should have 15s bond state timeout`() {
        val registry = registryFor("xiaomi", "22071219cg", "v14.0.2.0")
        assertEquals(15.seconds, registry.resolve(BleQuirks.BondStateTimeout))
    }

    @Test
    fun `huawei should have 10s bond state timeout`() {
        val registry = registryFor("huawei", "els-nx9", "huaweiels-nx9")
        assertEquals(10.seconds, registry.resolve(BleQuirks.BondStateTimeout))
    }

    @Test
    fun `honor should have 10s bond state timeout`() {
        val registry = registryFor("honor", "nth-nx9", "honornth-nx9")
        assertEquals(10.seconds, registry.resolve(BleQuirks.BondStateTimeout))
    }

    @Test
    fun `unknown device uses default bond state timeout`() {
        val registry = registryFor("unknown", "unknown", "unknown")
        assertEquals(10.seconds, registry.resolve(BleQuirks.BondStateTimeout))
    }

    // === Connection timeout ===

    @Test
    fun `samsung should have 30s connection timeout`() {
        val registry = registryFor("samsung", "sm-g991b", "g991bxxu1aua1")
        assertEquals(30.seconds, registry.resolve(BleQuirks.ConnectionTimeout))
    }

    @Test
    fun `huawei should have 35s connection timeout`() {
        val registry = registryFor("huawei", "els-nx9", "huaweiels-nx9")
        assertEquals(35.seconds, registry.resolve(BleQuirks.ConnectionTimeout))
    }

    @Test
    fun `unknown device uses default connection timeout`() {
        val registry = registryFor("unknown", "unknown", "unknown")
        assertEquals(30.seconds, registry.resolve(BleQuirks.ConnectionTimeout))
    }

    // === Matching priority ===

    @Test
    fun `manufacturer match when model not in registry`() {
        val registry = registryFor("samsung", "sm-z999z", "unknown")
        assertEquals(500.milliseconds, registry.resolve(BleQuirks.GattRetryDelay))
        assertEquals(2, registry.resolve(BleQuirks.GattRetryCount))
    }

    @Test
    fun `model prefix match for series`() {
        val r1 = registryFor("samsung", "sm-g991b", "g991bxxu1aua1")
        assertTrue(r1.resolve(BleQuirks.BondBeforeConnect))
        val r2 = registryFor("samsung", "sm-g990b", "g990bxxu1aua1")
        assertTrue(r2.resolve(BleQuirks.BondBeforeConnect))
    }

    @Test
    fun `specific model match takes priority over manufacturer`() {
        val registry = registryFor("google", "pixel 7", "tp1a.221005.002")
        assertEquals(1500.milliseconds, registry.resolve(BleQuirks.GattRetryDelay))
    }

    @Test
    fun `all defaults for completely unknown device`() {
        val registry = registryFor("acme", "widget-3000", "v1.0")
        assertFalse(registry.resolve(BleQuirks.BondBeforeConnect))
        assertEquals(300.milliseconds, registry.resolve(BleQuirks.GattRetryDelay))
        assertEquals(1, registry.resolve(BleQuirks.GattRetryCount))
        assertFalse(registry.resolve(BleQuirks.RefreshServicesOnBond))
        assertEquals(10.seconds, registry.resolve(BleQuirks.BondStateTimeout))
        assertEquals(30.seconds, registry.resolve(BleQuirks.ConnectionTimeout))
    }

    // === describe() ===

    @Test
    fun `describe includes active quirks for samsung`() {
        val registry = registryFor("samsung", "sm-g991b", "g991bxxu1aua1")
        val desc = registry.describe()
        assertTrue(desc.contains("samsung/sm-g991b"))
        assertTrue(desc.contains("bond-before-connect"))
        assertTrue(desc.contains("retry="))
    }

    @Test
    fun `describe includes active quirks for xiaomi`() {
        val registry = registryFor("xiaomi", "22071219cg", "v14.0.2.0")
        val desc = registry.describe()
        assertTrue(desc.contains("xiaomi/22071219cg"))
        assertTrue(desc.contains("refresh-services-on-bond"))
        assertTrue(desc.contains("bond-timeout="))
    }

    @Test
    fun `describe shows no quirks for unknown device`() {
        val registry = registryFor("acme", "widget-3000", "v1.0")
        assertTrue(registry.describe().contains("no device-specific quirks"))
    }

    @Test
    fun `describe includes active quirks for google pixel`() {
        val registry = registryFor("google", "pixel 7", "tp1a.221005.002")
        val desc = registry.describe()
        assertTrue(desc.contains("google/pixel 7"))
        assertTrue(desc.contains("retry=3x"))
    }

    // === User overrides ===

    @Test
    fun `user override takes priority over OEM provider`() {
        val registry = QuirkRegistry.createForTest(DeviceInfo("samsung", "sm-g991b", "g991bxxu1aua1")) {
            addProvider(OemQuirkProvider())
            register(BleQuirks.GattRetryCount, 5) { it.manufacturer == "samsung" }
        }
        assertEquals(5, registry.resolve(BleQuirks.GattRetryCount))
    }

    @Test
    fun `user override with device key string`() {
        val registry = QuirkRegistry.createForTest(DeviceInfo("acme", "widget-3000", "v1.0")) {
            register(BleQuirks.BondBeforeConnect, true, "acme")
        }
        assertTrue(registry.resolve(BleQuirks.BondBeforeConnect))
    }
}
