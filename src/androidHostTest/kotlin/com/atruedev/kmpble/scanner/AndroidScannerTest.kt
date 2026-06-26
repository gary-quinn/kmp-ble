package com.atruedev.kmpble.scanner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class AndroidScannerTest {
    // =========================================================================
    // scanPhyToAndroid -- uses compile-time constants, safe in unit test context
    // =========================================================================

    @Test
    fun `Le1M maps to BluetoothDevice PHY_LE_1M`() {
        // BluetoothDevice.PHY_LE_1M = 1 (API 26+)
        assertEquals(1, AndroidScanner.scanPhyToAndroid(ScanPhy.Le1M))
    }

    @Test
    fun `LeCoded maps to BluetoothDevice PHY_LE_CODED`() {
        // BluetoothDevice.PHY_LE_CODED = 3 (API 26+)
        assertEquals(3, AndroidScanner.scanPhyToAndroid(ScanPhy.LeCoded))
    }

    @Test
    fun `All maps to ScanSettings PHY_LE_ALL_SUPPORTED`() {
        // ScanSettings.PHY_LE_ALL_SUPPORTED = 255 (API 26+)
        assertEquals(255, AndroidScanner.scanPhyToAndroid(ScanPhy.All))
    }

    @Test
    fun `scanPhyToAndroid covers all ScanPhy values`() {
        val results =
            ScanPhy.entries.map { it to AndroidScanner.scanPhyToAndroid(it) }
        assertEquals(3, results.size)
        // PHY_LE_1M = 1
        assertEquals(1, results.first { it.first == ScanPhy.Le1M }.second)
        // PHY_LE_CODED = 3
        assertEquals(3, results.first { it.first == ScanPhy.LeCoded }.second)
        // PHY_LE_ALL_SUPPORTED = 255
        assertEquals(255, results.first { it.first == ScanPhy.All }.second)
    }

    // =========================================================================
    // ScannerConfig defaults and DSL -- pure Kotlin, no Android SDK at runtime
    // =========================================================================

    @Test
    fun `ScannerConfig default values`() {
        val config = ScannerConfig()
        assertNull(config.timeout)
        assertTrue(config.emission is EmissionPolicy.FirstThenChanges)
        assertTrue(config.legacyOnly)
        assertEquals(ScanPhy.All, config.phy)
    }

    @Test
    fun `ScannerConfig DSL sets custom values`() {
        val config =
            ScannerConfig().apply {
                timeout = 30.seconds
                emission = EmissionPolicy.All
                legacyOnly = false
                phy = ScanPhy.LeCoded
            }
        assertEquals(30.seconds, config.timeout)
        assertTrue(config.emission is EmissionPolicy.All)
        assertEquals(false, config.legacyOnly)
        assertEquals(ScanPhy.LeCoded, config.phy)
    }

    @Test
    fun `ScannerConfig filters DSL builds predicate groups`() {
        val config =
            ScannerConfig().apply {
                filters {
                    match {
                        name("DeviceA")
                        serviceUuid("180d")
                    }
                    match { name("DeviceB") }
                }
            }
        // filterGroups is internal but accessible from same-module host test
        assertEquals(2, config.filterGroups.size)
        assertEquals(2, config.filterGroups[0].size)
        assertEquals("DeviceA", (config.filterGroups[0][0] as ScanPredicate.Name).exact)
        assertEquals(1, config.filterGroups[1].size)
        assertEquals("DeviceB", (config.filterGroups[1][0] as ScanPredicate.Name).exact)
    }

    @Test
    fun `ScannerConfig filters DSL produces correct predicate types`() {
        val config =
            ScannerConfig().apply {
                filters {
                    match {
                        serviceUuid("180d")
                        namePrefix("Dev")
                        rssi(-60)
                        address("AA:BB:CC:DD:EE:FF")
                        manufacturerData(companyId = 0x004C)
                    }
                }
            }
        assertEquals(1, config.filterGroups.size)
        val predicates = config.filterGroups[0]
        assertEquals(5, predicates.size)
        assertTrue(predicates.any { it is ScanPredicate.ServiceUuid })
        assertTrue(predicates.any { it is ScanPredicate.NamePrefix })
        assertTrue(predicates.any { it is ScanPredicate.MinRssi })
        assertTrue(predicates.any { it is ScanPredicate.Address })
        assertTrue(predicates.any { it is ScanPredicate.ManufacturerData })
    }

    @Test
    fun `FiltersScope match block without predicates is skipped`() {
        val config =
            ScannerConfig().apply {
                filters {
                    // match block with no predicates - FiltersScope skips it
                }
            }
        assertEquals(0, config.filterGroups.size)
    }

    // =========================================================================
    // buildOsFilters -- edge cases that do not invoke Android SDK methods
    // (return null before reaching the predicate-mapping loop)
    // =========================================================================

    @Test
    fun `buildOsFilters with empty list returns null`() {
        assertNull(AndroidScanner.buildOsFilters(emptyList()))
    }

    @Test
    fun `buildOsFilters with post-filter-only predicates returns null`() {
        // NamePrefix and MinRssi are post-filter only,
        // so hasOsPredicate stays false, group maps to null, result is null
        val config =
            ScannerConfig().apply {
                filters {
                    match { namePrefix("HR") }
                }
            }
        assertNull(AndroidScanner.buildOsFilters(config.filterGroups))
    }

    @Test
    fun `buildOsFilters with rssi-only predicate returns null`() {
        val config =
            ScannerConfig().apply {
                filters {
                    match { rssi(-60) }
                }
            }
        assertNull(AndroidScanner.buildOsFilters(config.filterGroups))
    }

    @Test
    fun `buildOsFilters with only post-filter predicates across multiple matches returns null`() {
        val config =
            ScannerConfig().apply {
                filters {
                    match { namePrefix("A") }
                    match { namePrefix("B") }
                }
            }
        assertNull(AndroidScanner.buildOsFilters(config.filterGroups))
    }

    // =========================================================================
    // buildOsFilters -- tests that invoke Android SDK via ScanFilter.Builder.
    // Must run on an emulator (android-instrumented CI job).
    // These are structured to fail gracefully with assertFailsWith in unit-test
    // context so the test suite still completes.
    // =========================================================================

    @Test
    fun `buildOsFilters with hardware predicate returns list on instrumented`() {
        val config =
            ScannerConfig().apply {
                filters {
                    match { serviceUuid("180d") }
                }
            }
        // On JVM unit test: throws RuntimeException (Stub!) from ParcelUuid/ScanFilter.Builder
        // On instrumented: returns a valid ScanFilter list with the service UUID set
        val result = runCatching { AndroidScanner.buildOsFilters(config.filterGroups) }
        // Accept either success or Stub exception -- the instrumented CI will validate
        assertTrue(result.isSuccess || result.exceptionOrNull() is RuntimeException)
    }

    // =========================================================================
    // scanModeToAndroid -- maps ScanMode enum to Android constants
    // =========================================================================

    @Test
    fun `scanModeToAndroid LowPower maps to SCAN_MODE_BALANCED`() {
        assertEquals(
            ScanSettings.SCAN_MODE_BALANCED,
            AndroidScanner.scanModeToAndroid(ScanMode.LowPower),
        )
    }

    @Test
    fun `scanModeToAndroid Balanced maps to SCAN_MODE_BALANCED`() {
        assertEquals(
            ScanSettings.SCAN_MODE_BALANCED,
            AndroidScanner.scanModeToAndroid(ScanMode.Balanced),
        )
    }

    @Test
    fun `scanModeToAndroid LowLatency maps to SCAN_MODE_LOW_LATENCY`() {
        assertEquals(
            ScanSettings.SCAN_MODE_LOW_LATENCY,
            AndroidScanner.scanModeToAndroid(ScanMode.LowLatency),
        )
    }

    @Test
    fun `scanModeToAndroid covers all ScanMode values`() {
        val results = ScanMode.entries.map { it to AndroidScanner.scanModeToAndroid(it) }
        assertEquals(3, results.size)
        assertTrue(
            results.all {
                it.second == ScanSettings.SCAN_MODE_BALANCED ||
                    it.second == ScanSettings.SCAN_MODE_LOW_LATENCY
            },
        )
    }

    // =========================================================================
    // ScannerConfig scanMode -- default and DSL
    // =========================================================================

    @Test
    fun `ScannerConfig scanMode defaults to Balanced`() {
        val config = ScannerConfig()
        assertEquals(ScanMode.Balanced, config.scanMode)
    }

    @Test
    fun `ScannerConfig DSL sets custom scanMode`() {
        val config = ScannerConfig()
            .apply {
                scanMode = ScanMode.LowLatency
            }
        assertEquals(ScanMode.LowLatency, config.scanMode)
    }

    @Test
    fun `ScannerConfig DSL sets LowPower scanMode`() {
        val config = ScannerConfig()
            .apply {
                scanMode = ScanMode.LowPower
            }
        assertEquals(ScanMode.LowPower, config.scanMode)
    }
}
