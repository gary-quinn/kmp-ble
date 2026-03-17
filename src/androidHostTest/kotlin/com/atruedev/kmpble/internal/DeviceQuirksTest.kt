package com.atruedev.kmpble.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DeviceQuirksTest {

    // =========================================================================
    // Bond before connect
    // =========================================================================

    @Test
    fun `samsung galaxy s21 should bond before connect`() {
        val quirks = DeviceQuirks(DeviceInfo("samsung", "sm-g991b", "g991bxxu1aua1"))
        assertTrue(quirks.shouldBondBeforeConnect())
    }

    @Test
    fun `samsung galaxy s20 should bond before connect`() {
        val quirks = DeviceQuirks(DeviceInfo("samsung", "sm-g980f", "g980fxxu1aua1"))
        assertTrue(quirks.shouldBondBeforeConnect())
    }

    @Test
    fun `samsung galaxy note 20 should bond before connect`() {
        val quirks = DeviceQuirks(DeviceInfo("samsung", "sm-n981b", "n981bxxu1aua1"))
        assertTrue(quirks.shouldBondBeforeConnect())
    }

    @Test
    fun `samsung galaxy a53 should bond before connect`() {
        val quirks = DeviceQuirks(DeviceInfo("samsung", "sm-a536b", "a536bxxu1aua1"))
        assertTrue(quirks.shouldBondBeforeConnect())
    }

    @Test
    fun `google pixel should not bond before connect`() {
        val quirks = DeviceQuirks(DeviceInfo("google", "pixel 7", "tp1a.221005.002"))
        assertFalse(quirks.shouldBondBeforeConnect())
    }

    @Test
    fun `unknown device should not bond before connect`() {
        val quirks = DeviceQuirks(DeviceInfo("unknown", "unknown", "unknown"))
        assertFalse(quirks.shouldBondBeforeConnect())
    }

    // =========================================================================
    // GATT retry delay
    // =========================================================================

    @Test
    fun `google pixel 7 should have 1500ms retry delay`() {
        val quirks = DeviceQuirks(DeviceInfo("google", "pixel 7", "tp1a.221005.002"))
        assertEquals(1500.milliseconds, quirks.gattConnectionRetryDelay())
    }

    @Test
    fun `google pixel 8 should have 1500ms retry delay`() {
        val quirks = DeviceQuirks(DeviceInfo("google", "pixel 8", "ud1a.230803.022"))
        assertEquals(1500.milliseconds, quirks.gattConnectionRetryDelay())
    }

    @Test
    fun `google pixel 6 should have 1500ms retry delay`() {
        val quirks = DeviceQuirks(DeviceInfo("google", "pixel 6", "sd1a.210817.015.a4"))
        assertEquals(1500.milliseconds, quirks.gattConnectionRetryDelay())
    }

    @Test
    fun `google generic model falls back to manufacturer match`() {
        val quirks = DeviceQuirks(DeviceInfo("google", "pixel 5", "rq3a.211001.001"))
        assertEquals(1.seconds, quirks.gattConnectionRetryDelay())
    }

    @Test
    fun `samsung should have 500ms retry delay`() {
        val quirks = DeviceQuirks(DeviceInfo("samsung", "sm-g991b", "g991bxxu1aua1"))
        assertEquals(500.milliseconds, quirks.gattConnectionRetryDelay())
    }

    @Test
    fun `unknown device uses default retry delay`() {
        val quirks = DeviceQuirks(DeviceInfo("unknown", "unknown", "unknown"))
        assertEquals(300.milliseconds, quirks.gattConnectionRetryDelay())
    }

    // =========================================================================
    // GATT retry count
    // =========================================================================

    @Test
    fun `google pixel should retry 3 times`() {
        val quirks = DeviceQuirks(DeviceInfo("google", "pixel 7", "tp1a.221005.002"))
        assertEquals(3, quirks.connectGattRetryCount())
    }

    @Test
    fun `samsung should retry 2 times`() {
        val quirks = DeviceQuirks(DeviceInfo("samsung", "sm-g991b", "g991bxxu1aua1"))
        assertEquals(2, quirks.connectGattRetryCount())
    }

    @Test
    fun `xiaomi should retry 2 times`() {
        val quirks = DeviceQuirks(DeviceInfo("xiaomi", "22071219cg", "v14.0.2.0"))
        assertEquals(2, quirks.connectGattRetryCount())
    }

    @Test
    fun `unknown device retries 1 time`() {
        val quirks = DeviceQuirks(DeviceInfo("unknown", "unknown", "unknown"))
        assertEquals(1, quirks.connectGattRetryCount())
    }

    // =========================================================================
    // Refresh services on bond
    // =========================================================================

    @Test
    fun `xiaomi should refresh services on bond`() {
        val quirks = DeviceQuirks(DeviceInfo("xiaomi", "22071219cg", "v14.0.2.0"))
        assertTrue(quirks.shouldRefreshServicesOnBond())
    }

    @Test
    fun `oneplus should refresh services on bond`() {
        val quirks = DeviceQuirks(DeviceInfo("oneplus", "le2115", "skq1.211006.001"))
        assertTrue(quirks.shouldRefreshServicesOnBond())
    }

    @Test
    fun `redmi should refresh services on bond`() {
        val quirks = DeviceQuirks(DeviceInfo("redmi", "note 11 pro", "v14.0.1.0"))
        assertTrue(quirks.shouldRefreshServicesOnBond())
    }

    @Test
    fun `poco should refresh services on bond`() {
        val quirks = DeviceQuirks(DeviceInfo("poco", "m2012k11ag", "v14.0.1.0"))
        assertTrue(quirks.shouldRefreshServicesOnBond())
    }

    @Test
    fun `oppo should refresh services on bond`() {
        val quirks = DeviceQuirks(DeviceInfo("oppo", "cph2399", "rp1a.200720.011"))
        assertTrue(quirks.shouldRefreshServicesOnBond())
    }

    @Test
    fun `samsung should not refresh services on bond`() {
        val quirks = DeviceQuirks(DeviceInfo("samsung", "sm-g991b", "g991bxxu1aua1"))
        assertFalse(quirks.shouldRefreshServicesOnBond())
    }

    @Test
    fun `unknown device should not refresh services on bond`() {
        val quirks = DeviceQuirks(DeviceInfo("unknown", "unknown", "unknown"))
        assertFalse(quirks.shouldRefreshServicesOnBond())
    }

    // =========================================================================
    // Bond state change timeout
    // =========================================================================

    @Test
    fun `xiaomi should have 15s bond state timeout`() {
        val quirks = DeviceQuirks(DeviceInfo("xiaomi", "22071219cg", "v14.0.2.0"))
        assertEquals(15.seconds, quirks.bondStateChangeTimeout())
    }

    @Test
    fun `huawei should have 10s bond state timeout`() {
        val quirks = DeviceQuirks(DeviceInfo("huawei", "els-nx9", "huaweiels-nx9"))
        assertEquals(10.seconds, quirks.bondStateChangeTimeout())
    }

    @Test
    fun `honor should have 10s bond state timeout`() {
        val quirks = DeviceQuirks(DeviceInfo("honor", "nth-nx9", "honornth-nx9"))
        assertEquals(10.seconds, quirks.bondStateChangeTimeout())
    }

    @Test
    fun `unknown device uses default bond state timeout`() {
        val quirks = DeviceQuirks(DeviceInfo("unknown", "unknown", "unknown"))
        assertEquals(10.seconds, quirks.bondStateChangeTimeout())
    }

    // =========================================================================
    // Connection timeout
    // =========================================================================

    @Test
    fun `samsung should have 30s connection timeout`() {
        val quirks = DeviceQuirks(DeviceInfo("samsung", "sm-g991b", "g991bxxu1aua1"))
        assertEquals(30.seconds, quirks.connectionTimeout())
    }

    @Test
    fun `huawei should have 35s connection timeout`() {
        val quirks = DeviceQuirks(DeviceInfo("huawei", "els-nx9", "huaweiels-nx9"))
        assertEquals(35.seconds, quirks.connectionTimeout())
    }

    @Test
    fun `unknown device uses default connection timeout`() {
        val quirks = DeviceQuirks(DeviceInfo("unknown", "unknown", "unknown"))
        assertEquals(30.seconds, quirks.connectionTimeout())
    }

    // =========================================================================
    // Matching priority (hierarchical)
    // =========================================================================

    @Test
    fun `manufacturer match when model not in registry`() {
        val quirks = DeviceQuirks(DeviceInfo("samsung", "sm-z999z", "unknown"))
        assertEquals(500.milliseconds, quirks.gattConnectionRetryDelay())
        assertEquals(2, quirks.connectGattRetryCount())
    }

    @Test
    fun `model prefix match for series`() {
        val quirks = DeviceQuirks(DeviceInfo("samsung", "sm-g991b", "g991bxxu1aua1"))
        assertTrue(quirks.shouldBondBeforeConnect())
        val quirks2 = DeviceQuirks(DeviceInfo("samsung", "sm-g990b", "g990bxxu1aua1"))
        assertTrue(quirks2.shouldBondBeforeConnect())
    }

    @Test
    fun `specific model match takes priority over manufacturer`() {
        val quirks = DeviceQuirks(DeviceInfo("google", "pixel 7", "tp1a.221005.002"))
        assertEquals(1500.milliseconds, quirks.gattConnectionRetryDelay())
    }

    @Test
    fun `all defaults for completely unknown device`() {
        val quirks = DeviceQuirks(DeviceInfo("acme", "widget-3000", "v1.0"))
        assertFalse(quirks.shouldBondBeforeConnect())
        assertEquals(300.milliseconds, quirks.gattConnectionRetryDelay())
        assertEquals(1, quirks.connectGattRetryCount())
        assertFalse(quirks.shouldRefreshServicesOnBond())
        assertEquals(10.seconds, quirks.bondStateChangeTimeout())
        assertEquals(30.seconds, quirks.connectionTimeout())
    }

    // =========================================================================
    // MODEL_PREFIX_LENGTH constant
    // =========================================================================

    @Test
    fun `model prefix length matches samsung naming convention`() {
        assertEquals(6, DeviceQuirks.MODEL_PREFIX_LENGTH)
        // "sm-g991b".take(6) = "sm-g99" which is the Galaxy S21 series key
        assertEquals("sm-g99", "sm-g991b".take(DeviceQuirks.MODEL_PREFIX_LENGTH))
        assertEquals("sm-g99", "sm-g990b".take(DeviceQuirks.MODEL_PREFIX_LENGTH))
        assertEquals("sm-g99", "sm-g996u".take(DeviceQuirks.MODEL_PREFIX_LENGTH))
    }

    // =========================================================================
    // describe() — observability
    // =========================================================================

    @Test
    fun `describe includes active quirks for samsung`() {
        val quirks = DeviceQuirks(DeviceInfo("samsung", "sm-g991b", "g991bxxu1aua1"))
        val desc = quirks.describe()
        assertTrue(desc.contains("samsung/sm-g991b"), "should contain device id")
        assertTrue(desc.contains("bond-before-connect"), "samsung s21 should have bond-before-connect")
        assertTrue(desc.contains("retry="), "samsung should have retry quirk")
    }

    @Test
    fun `describe includes active quirks for xiaomi`() {
        val quirks = DeviceQuirks(DeviceInfo("xiaomi", "22071219cg", "v14.0.2.0"))
        val desc = quirks.describe()
        assertTrue(desc.contains("xiaomi/22071219cg"))
        assertTrue(desc.contains("refresh-services-on-bond"))
        assertTrue(desc.contains("bond-timeout="))
    }

    @Test
    fun `describe shows no quirks for unknown device`() {
        val quirks = DeviceQuirks(DeviceInfo("acme", "widget-3000", "v1.0"))
        val desc = quirks.describe()
        assertTrue(desc.contains("no device-specific quirks"))
    }

    @Test
    fun `describe includes active quirks for google pixel`() {
        val quirks = DeviceQuirks(DeviceInfo("google", "pixel 7", "tp1a.221005.002"))
        val desc = quirks.describe()
        assertTrue(desc.contains("google/pixel 7"))
        assertTrue(desc.contains("retry=3x"))
    }

    // Note: DeviceQuirks.forCurrentDevice() is not testable in JVM host tests
    // because Build.MANUFACTURER/MODEL/DISPLAY are null outside Android runtime.
    // It is exercised in instrumentation tests and production code.
}
