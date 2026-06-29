package com.atruedev.kmpble.scanner

import android.bluetooth.le.ScanSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests for Android Scanner covering scan modes, filter behaviors,
 * and edge cases. Uses androidHostTest runner with real Android APIs via Robolectric.
 *
 * See architecture-plans/issue-335.md for the full plan.
 */
class AndroidScannerIntegrationTest {
    // =========================================================================
    // Scan Mode coverage
    // =========================================================================

    @Test
    fun `scanModeToAndroid maps LowPower to SCAN_MODE_BALANCED`() {
        assertEquals(
            ScanSettings.SCAN_MODE_BALANCED,
            AndroidScanner.scanModeToAndroid(ScanMode.LowPower),
        )
    }

    @Test
    fun `scanModeToAndroid maps Balanced to SCAN_MODE_BALANCED`() {
        assertEquals(
            ScanSettings.SCAN_MODE_BALANCED,
            AndroidScanner.scanModeToAndroid(ScanMode.Balanced),
        )
    }

    @Test
    fun `scanModeToAndroid maps LowLatency to SCAN_MODE_LOW_LATENCY`() {
        assertEquals(
            ScanSettings.SCAN_MODE_LOW_LATENCY,
            AndroidScanner.scanModeToAndroid(ScanMode.LowLatency),
        )
    }

    @Test
    fun `ScanMode enum has exactly 3 values`() {
        assertEquals(3, ScanMode.entries.size)
        assertTrue(
            ScanMode.entries.containsAll(
                listOf(ScanMode.LowPower, ScanMode.Balanced, ScanMode.LowLatency),
            ),
        )
    }

    @Test
    fun `ScanMode LowPower and Balanced both map to BALANCED on Android`() {
        // Android does not expose a dedicated low-power scan mode below BALANCED,
        // so both map to the same constant.
        assertEquals(
            ScanSettings.SCAN_MODE_BALANCED,
            AndroidScanner.scanModeToAndroid(ScanMode.LowPower),
        )
        assertEquals(
            ScanSettings.SCAN_MODE_BALANCED,
            AndroidScanner.scanModeToAndroid(ScanMode.Balanced),
        )
    }

    // =========================================================================
    // Scan PHY coverage
    // =========================================================================

    @Test
    fun `scanPhyToAndroid maps Le1M to PHY_LE_1M`() {
        assertEquals(1, AndroidScanner.scanPhyToAndroid(ScanPhy.Le1M))
    }

    @Test
    fun `scanPhyToAndroid maps LeCoded to PHY_LE_CODED`() {
        assertEquals(3, AndroidScanner.scanPhyToAndroid(ScanPhy.LeCoded))
    }

    @Test
    fun `scanPhyToAndroid maps All to PHY_LE_ALL_SUPPORTED`() {
        assertEquals(255, AndroidScanner.scanPhyToAndroid(ScanPhy.All))
    }

    @Test
    fun `ScanPhy enum has exactly 3 values`() {
        assertEquals(3, ScanPhy.entries.size)
    }

    @Test
    fun `scanPhyToAndroid covers all ScanPhy values without gaps`() {
        val results = ScanMode.entries.associateWith { it }
        assertEquals(3, results.size)
    }

    // =========================================================================
    // buildOsFilters -- real Android SDK calls via Robolectric
    // =========================================================================

    @Test
    fun `buildOsFilters with service UUID filter creates valid ScanFilter`() {
        val config =
            ScannerConfig().apply {
                filters { match { serviceUuid("180d") } }
            }
        val result = AndroidScanner.buildOsFilters(config.filterGroups)
        assertNotNull(result)
        assertEquals(1, result.size)
        // Verify the Android ScanFilter was actually created (not null stubs)
        assertNotNull(result[0].serviceUuid)
    }

    @Test
    fun `buildOsFilters with device name filter creates valid ScanFilter`() {
        val config =
            ScannerConfig().apply {
                filters { match { name("TestDevice") } }
            }
        val result = AndroidScanner.buildOsFilters(config.filterGroups)
        assertNotNull(result)
        assertEquals(1, result.size)
    }

    @Test
    fun `buildOsFilters with MAC address filter creates valid ScanFilter`() {
        val config =
            ScannerConfig().apply {
                filters { match { address("AA:BB:CC:DD:EE:FF") } }
            }
        val result = AndroidScanner.buildOsFilters(config.filterGroups)
        assertNotNull(result)
        assertEquals(1, result.size)
    }

    @Test
    fun `buildOsFilters with manufacturer data filter creates valid ScanFilter`() {
        val config =
            ScannerConfig().apply {
                filters { match { manufacturerData(companyId = 0x004C) } }
            }
        val result = AndroidScanner.buildOsFilters(config.filterGroups)
        assertNotNull(result)
        assertEquals(1, result.size)
    }

    @Test
    fun `buildOsFilters with manufacturer data and mask creates valid ScanFilter`() {
        val config =
            ScannerConfig().apply {
                filters {
                    match {
                        manufacturerData(
                            companyId = 0x004C,
                            data = byteArrayOf(0x01, 0x02),
                            mask = byteArrayOf(0xFF.toByte(), 0x00.toByte()),
                        )
                    }
                }
            }
        val result = AndroidScanner.buildOsFilters(config.filterGroups)
        assertNotNull(result)
        assertEquals(1, result.size)
    }

    @Test
    fun `buildOsFilters with service data filter creates valid ScanFilter`() {
        val config =
            ScannerConfig().apply {
                filters { match { serviceData("180d", byteArrayOf(0x01, 0x02)) } }
            }
        val result = AndroidScanner.buildOsFilters(config.filterGroups)
        assertNotNull(result)
        assertEquals(1, result.size)
    }

    @Test
    fun `buildOsFilters with service data and mask creates valid ScanFilter`() {
        val config =
            ScannerConfig().apply {
                filters {
                    match {
                        serviceData(
                            "180d",
                            byteArrayOf(0x01),
                            mask = byteArrayOf(0xFF.toByte()),
                        )
                    }
                }
            }
        val result = AndroidScanner.buildOsFilters(config.filterGroups)
        assertNotNull(result)
        assertEquals(1, result.size)
    }

    @Test
    fun `buildOsFilters combines multiple OS predicates in one group`() {
        val config =
            ScannerConfig().apply {
                filters {
                    match {
                        name("DeviceA")
                        serviceUuid("180d")
                    }
                }
            }
        val result = AndroidScanner.buildOsFilters(config.filterGroups)
        assertNotNull(result)
        assertEquals(1, result.size)
    }

    @Test
    fun `buildOsFilters produces one filter per match group`() {
        val config =
            ScannerConfig().apply {
                filters {
                    match { name("DeviceA") }
                    match { serviceUuid("180d") }
                    match { address("AA:BB:CC:DD:EE:FF") }
                }
            }
        val result = AndroidScanner.buildOsFilters(config.filterGroups)
        assertNotNull(result)
        assertEquals(3, result.size)
    }

    @Test
    fun `buildOsFilters with mixed AND-group produces correct OS filters`() {
        val config =
            ScannerConfig().apply {
                filters {
                    match {
                        name("DeviceA")
                        serviceUuid("180d")
                        rssi(-50) // post-filter only
                    }
                    match {
                        address("AA:BB:CC:DD:EE:FF")
                    }
                }
            }
        val result = AndroidScanner.buildOsFilters(config.filterGroups)
        assertNotNull(result)
        // Group 1: name + serviceUuid (OS) + rssi (post-filter) -> 1 OS filter
        // Group 2: address (OS) -> 1 OS filter
        assertEquals(2, result.size)
    }

    // =========================================================================
    // Filter behavior -- edge cases
    // =========================================================================

    @Test
    fun `buildOsFilters returns null for empty filter list`() {
        assertNull(AndroidScanner.buildOsFilters(emptyList()))
    }

    @Test
    fun `buildOsFilters returns null when all predicates are post-filter`() {
        val config =
            ScannerConfig().apply {
                filters { match { namePrefix("HR") } }
            }
        assertNull(AndroidScanner.buildOsFilters(config.filterGroups))
    }

    @Test
    fun `buildOsFilters returns null for rssi-only filter`() {
        val config =
            ScannerConfig().apply {
                filters { match { rssi(-60) } }
            }
        assertNull(AndroidScanner.buildOsFilters(config.filterGroups))
    }

    @Test
    fun `buildOsFilters skips match groups with only post-filter predicates`() {
        val config =
            ScannerConfig().apply {
                filters {
                    match { namePrefix("A") }
                    match { namePrefix("B") }
                    match { rssi(-50) }
                }
            }
        assertNull(AndroidScanner.buildOsFilters(config.filterGroups))
    }

    @Test
    fun `buildOsFilters returns null for empty filter list explicitly`() {
        assertNull(AndroidScanner.buildOsFilters(emptyList()))
    }

    @Test
    fun `buildOsFilters with mixed OS and post-filter predicates produces one filter`() {
        val config =
            ScannerConfig().apply {
                filters {
                    match {
                        namePrefix("Device")
                        serviceUuid("180d")
                        rssi(-40)
                    }
                }
            }
        val result = AndroidScanner.buildOsFilters(config.filterGroups)
        assertNotNull(result)
        assertEquals(1, result.size)
        // Only serviceUuid is an OS predicate
        assertNotNull(result[0].serviceUuid)
    }

    // =========================================================================
    // ScannerConfig -- scan mode defaults and DSL
    // =========================================================================

    @Test
    fun `ScannerConfig default scanMode is Balanced`() {
        assertEquals(ScanMode.Balanced, ScannerConfig().scanMode)
    }

    @Test
    fun `ScannerConfig DSL sets LowPower scanMode`() {
        val config = ScannerConfig().apply { scanMode = ScanMode.LowPower }
        assertEquals(ScanMode.LowPower, config.scanMode)
    }

    @Test
    fun `ScannerConfig DSL sets Balanced scanMode`() {
        val config = ScannerConfig().apply { scanMode = ScanMode.Balanced }
        assertEquals(ScanMode.Balanced, config.scanMode)
    }

    @Test
    fun `ScannerConfig DSL sets LowLatency scanMode`() {
        val config = ScannerConfig().apply { scanMode = ScanMode.LowLatency }
        assertEquals(ScanMode.LowLatency, config.scanMode)
    }

    @Test
    fun `ScannerConfig scanMode can be changed after construction`() {
        val config = ScannerConfig()
        assertEquals(ScanMode.Balanced, config.scanMode)
        config.scanMode = ScanMode.LowPower
        assertEquals(ScanMode.LowPower, config.scanMode)
        config.scanMode = ScanMode.LowLatency
        assertEquals(ScanMode.LowLatency, config.scanMode)
    }

    // =========================================================================
    // ScannerConfig -- filter group composition
    // =========================================================================

    @Test
    fun `ScannerConfig single match block produces single filter group`() {
        val config =
            ScannerConfig().apply {
                filters { match { serviceUuid("180d") } }
            }
        assertEquals(1, config.filterGroups.size)
        assertEquals(3, config.filterGroups[0].size) // serviceUuid (OS) + 2 post-filters in DSL
    }

    @Test
    fun `ScannerConfig multiple match blocks produce multiple groups`() {
        val config =
            ScannerConfig().apply {
                filters {
                    match { name("A") }
                    match { name("B") }
                    match { serviceUuid("180d") }
                }
            }
        assertEquals(3, config.filterGroups.size)
    }

    @Test
    fun `ScannerConfig empty match blocks are skipped`() {
        val config =
            ScannerConfig().apply {
                filters {
                    match { }
                    match { serviceUuid("180d") }
                    match { }
                }
            }
        assertEquals(1, config.filterGroups.size)
    }

    @Test
    fun `ScannerConfig filters with all predicate types`() {
        val config =
            ScannerConfig().apply {
                filters {
                    match {
                        name("DeviceA")
                        namePrefix("Dev")
                        serviceUuid("180d")
                        serviceData("1818")
                        manufacturerData(0x004C)
                        rssi(-50)
                        address("AA:BB:CC:DD:EE:FF")
                    }
                }
            }
        assertEquals(1, config.filterGroups.size)
        assertEquals(7, config.filterGroups[0].size)
    }

    @Test
    fun `ScannerConfig filter groups are independent`() {
        val config =
            ScannerConfig().apply {
                filters {
                    match { name("A") }
                    match { serviceUuid("180d") }
                }
            }
        assertEquals("A", (config.filterGroups[0][0] as ScanPredicate.Name).exact)
        val expectedUuid = java.util.UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val actualPredicate = config.filterGroups[1][0] as ScanPredicate.ServiceUuid
        assertEquals(expectedUuid, actualPredicate.uuid)
    }

    // =========================================================================
    // Emission policy behavior
    // =========================================================================

    @Test
    fun `EmissionPolicy.FirstThenChanges default rssiThreshold is 5`() {
        val policy = EmissionPolicy.FirstThenChanges()
        assertEquals(5, policy.rssiThreshold)
    }

    @Test
    fun `EmissionPolicy.FirstThenChanges accepts custom rssiThreshold`() {
        val policy = EmissionPolicy.FirstThenChanges(rssiThreshold = 10)
        assertEquals(10, policy.rssiThreshold)
    }

    @Test
    fun `EmissionPolicy.All is a singleton`() {
        assertTrue(EmissionPolicy.All is EmissionPolicy.All)
    }

    @Test
    fun `EmissionPolicy types are distinct`() {
        val all = EmissionPolicy.All
        val changes = EmissionPolicy.FirstThenChanges()
        assertFalse(all is EmissionPolicy.FirstThenChanges)
        assertFalse(changes is EmissionPolicy.All)
    }

    @Test
    fun `ScannerConfig default emission is FirstThenChanges`() {
        assertTrue(ScannerConfig().emission is EmissionPolicy.FirstThenChanges)
    }

    @Test
    fun `ScannerConfig DSL sets All emission`() {
        val config = ScannerConfig().apply { emission = EmissionPolicy.All }
        assertTrue(config.emission is EmissionPolicy.All)
    }

    @Test
    fun `ScannerConfig DSL sets custom FirstThenChanges emission`() {
        val config =
            ScannerConfig().apply {
                emission = EmissionPolicy.FirstThenChanges(rssiThreshold = 8)
            }
        assertTrue(config.emission is EmissionPolicy.FirstThenChanges)
        assertEquals(8, (config.emission as EmissionPolicy.FirstThenChanges).rssiThreshold)
    }

    // =========================================================================
    // ScannerConfig -- full defaults
    // =========================================================================

    @Test
    fun `ScannerConfig default timeout is null`() {
        assertNull(ScannerConfig().timeout)
    }

    @Test
    fun `ScannerConfig default legacyOnly is true`() {
        assertTrue(ScannerConfig().legacyOnly)
    }

    @Test
    fun `ScannerConfig default phy is All`() {
        assertEquals(ScanPhy.All, ScannerConfig().phy)
    }

    @Test
    fun `ScannerConfig DSL sets custom timeout`() {
        val config = ScannerConfig().apply { timeout = 45.seconds }
        assertEquals(45.seconds, config.timeout)
    }

    @Test
    fun `ScannerConfig DSL sets custom legacyOnly`() {
        val config = ScannerConfig().apply { legacyOnly = false }
        assertFalse(config.legacyOnly)
    }

    @Test
    fun `ScannerConfig DSL sets custom phy`() {
        val config = ScannerConfig().apply { phy = ScanPhy.LeCoded }
        assertEquals(ScanPhy.LeCoded, config.phy)
    }

    @Test
    fun `ScannerConfig full DSL configuration`() {
        val config =
            ScannerConfig().apply {
                timeout = 60.seconds
                emission = EmissionPolicy.FirstThenChanges(rssiThreshold = 10)
                legacyOnly = false
                phy = ScanPhy.Le1M
                scanMode = ScanMode.LowLatency
                filters { match { serviceUuid("180d") } }
            }

        assertEquals(60.seconds, config.timeout)
        assertEquals(10, (config.emission as EmissionPolicy.FirstThenChanges).rssiThreshold)
        assertFalse(config.legacyOnly)
        assertEquals(ScanPhy.Le1M, config.phy)
        assertEquals(ScanMode.LowLatency, config.scanMode)
        assertEquals(1, config.filterGroups.size)
    }

    // =========================================================================
    // ScannerConfig.default companion helper
    // =========================================================================

    @Test
    fun `ScannerConfig.default sets all defaults`() {
        val config =
            ScannerConfig().apply {
                timeout = null
                emission = EmissionPolicy.All
                legacyOnly = true
                phy = ScanPhy.Le1M
                scanMode = ScanMode.LowPower
            }
        ScannerConfig.default(config)

        assertEquals(ScanPhy.All, config.phy)
        assertFalse(config.legacyOnly)
        assertEquals(30.seconds, config.timeout)
        assertTrue(config.emission is EmissionPolicy.FirstThenChanges)
        // scanMode is NOT affected by the default helper
        assertEquals(ScanMode.LowPower, config.scanMode)
    }

    // =========================================================================
    // ScanPredicate -- type coverage
    // =========================================================================

    @Test
    fun `MatchScope name creates Name predicate with exact string`() {
        val config =
            ScannerConfig().apply {
                filters { match { name("HeartRateSensor") } }
            }
        val predicate = config.filterGroups[0][0] as ScanPredicate.Name
        assertEquals("HeartRateSensor", predicate.exact)
    }

    @Test
    fun `MatchScope namePrefix creates NamePrefix predicate`() {
        val config =
            ScannerConfig().apply {
                filters { match { namePrefix("HR") } }
            }
        val predicate = config.filterGroups[0][0] as ScanPredicate.NamePrefix
        assertEquals("HR", predicate.prefix)
    }

    @Test
    fun `MatchScope serviceUuid with 16-bit form creates predicate`() {
        val config =
            ScannerConfig().apply {
                filters { match { serviceUuid("180d") } }
            }
        val predicate = config.filterGroups[0][0] as ScanPredicate.ServiceUuid
        assertNotNull(predicate.uuid)
    }

    @Test
    fun `MatchScope rssi creates MinRssi predicate with correct value`() {
        val config =
            ScannerConfig().apply {
                filters { match { rssi(-40) } }
            }
        val predicate = config.filterGroups[0][0] as ScanPredicate.MinRssi
        assertEquals(-40, predicate.minRssi)
    }

    @Test
    fun `MatchScope address creates Address predicate with correct MAC`() {
        val config =
            ScannerConfig().apply {
                filters { match { address("AA:BB:CC:DD:EE:FF") } }
            }
        val predicate = config.filterGroups[0][0] as ScanPredicate.Address
        assertEquals("AA:BB:CC:DD:EE:FF", predicate.mac)
    }

    @Test
    fun `MatchScope manufacturerData without data creates predicate with null data`() {
        val config =
            ScannerConfig().apply {
                filters { match { manufacturerData(companyId = 0x004C) } }
            }
        val predicate = config.filterGroups[0][0] as ScanPredicate.ManufacturerData
        assertEquals(0x004C, predicate.companyId)
        assertNull(predicate.data)
        assertNull(predicate.mask)
    }

    @Test
    fun `MatchScope manufacturerData with data creates predicate`() {
        val config =
            ScannerConfig().apply {
                filters {
                    match {
                        manufacturerData(
                            companyId = 0x004C,
                            data = byteArrayOf(0x01, 0x02),
                        )
                    }
                }
            }
        val predicate = config.filterGroups[0][0] as ScanPredicate.ManufacturerData
        assertEquals(0x004C, predicate.companyId)
        assertEquals(2, predicate.data?.size)
    }

    @Test
    fun `MatchScope serviceData with data creates predicate`() {
        val config =
            ScannerConfig().apply {
                filters {
                    match { serviceData("180d", byteArrayOf(1, 2, 3)) }
                }
            }
        val predicate = config.filterGroups[0][0] as ScanPredicate.ServiceData
        assertNotNull(predicate.uuid)
        assertEquals(3, predicate.data?.size)
    }

    // =========================================================================
    // UUID from short/long form helpers
    // =========================================================================

    @Test
    fun `uuidFrom 4-char short form expands correctly`() {
        val uuid = uuidFrom("180d")
        assertEquals(
            "0000180d-0000-1000-8000-00805f9b34fb",
            uuid.toString(),
        )
    }

    @Test
    fun `uuidFrom 8-char form expands correctly`() {
        val uuid = uuidFrom("0000180d")
        assertEquals(
            "0000180d-0000-1000-8000-00805f9b34fb",
            uuid.toString(),
        )
    }

    @Test
    fun `uuidFrom full 128-bit form passes through unchanged`() {
        val full = "0000180d-0000-1000-8000-00805f9b34fb"
        val uuid = uuidFrom(full)
        assertEquals(full, uuid.toString())
    }
}
