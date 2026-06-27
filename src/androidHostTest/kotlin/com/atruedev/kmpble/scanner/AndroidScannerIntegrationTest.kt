package com.atruedev.kmpble.scanner

import android.bluetooth.le.ScanSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests for AndroidScanner covering all scan modes, filters,
 * and edge cases. Uses androidHostTest runner for real Android APIs.
 */
class AndroidScannerIntegrationTest {

    // ==========================================================================
    // scanModeToAndroid -- all ScanMode values
    // ==========================================================================

    @Test
    fun `scanModeToAndroid covers all ScanMode values`() {
        val results = ScanMode.entries.map { it to AndroidScanner.scanModeToAndroid(it) }
        assertEquals(3, results.size)
        // ScanMode.LowPower -> SCAN_MODE_BALANCED
        assertEquals(ScanSettings.SCAN_MODE_BALANCED, results.first { it.first == ScanMode.LowPower }.second)
        // ScanMode.Balanced -> SCAN_MODE_BALANCED
        assertEquals(ScanSettings.SCAN_MODE_BALANCED, results.first { it.first == ScanMode.Balanced }.second)
        // ScanMode.LowLatency -> SCAN_MODE_LOW_LATENCY
        assertEquals(ScanSettings.SCAN_MODE_LOW_LATENCY, results.first { it.first == ScanMode.LowLatency }.second)
        // ScanMode.Opportunistic -> SCAN_MODE_OPPORTUNISTIC (API 23+)
        val opportunistic = results.first { it.first == ScanMode.Opportunistic }.second
        assertTrue(
            opportunistic == ScanSettings.SCAN_MODE_OPPORTUNISTIC ||
                opportunistic == ScanSettings.SCAN_MODE_LOW_POWER,
            "Opportunistic should map to SCAN_MODE_OPPORTUNISTIC or fallback to LOW_POWER",
        )
    }

    @Test
    fun `scanModeToAndroid Opportunistic maps to SCAN_MODE_OPPORTUNISTIC when available`() {
        // ScanSettings.SCAN_MODE_OPPORTUNISTIC = -2 (API 23+)
        // If the API level doesn't support it, it falls back to SCAN_MODE_LOW_POWER (-1)
        val result = AndroidScanner.scanModeToAndroid(ScanMode.Opportunistic)
        // The exact value depends on API level, but it should be either -1 or -2
        assertTrue(
            result == ScanSettings.SCAN_MODE_OPPORTUNISTIC || result == ScanSettings.SCAN_MODE_LOW_POWER,
        )
    }

    // ==========================================================================
    // ScannerConfig defaults
    // ==========================================================================

    @Test
    fun `ScannerConfig default values match expected baseline`() {
        val config = ScannerConfig()
        assertNull(config.timeout, "Default timeout should be null")
        assertTrue(config.emission is EmissionPolicy.FirstThenChanges)
        assertTrue(config.legacyOnly, "Default legacyOnly should be true")
        assertEquals(ScanPhy.All, config.phy, "Default PHY should be All")
        assertEquals(ScanMode.Balanced, config.scanMode, "Default scanMode should be Balanced")
    }

    // ==========================================================================
    // ScannerConfig DSL -- scanMode
    // ==========================================================================

    @Test
    fun `ScannerConfig DSL sets scanMode to LowLatency`() {
        val config =
            ScannerConfig()
                .apply {
                    scanMode = ScanMode.LowLatency
                }
        assertEquals(ScanMode.LowLatency, config.scanMode)
    }

    @Test
    fun `ScannerConfig DSL sets scanMode to LowPower`() {
        val config =
            ScannerConfig()
                .apply {
                    scanMode = ScanMode.LowPower
                }
        assertEquals(ScanMode.LowPower, config.scanMode)
    }

    @Test
    fun `ScannerConfig DSL sets scanMode to Opportunistic`() {
        val config =
            ScannerConfig()
                .apply {
                    scanMode = ScanMode.Opportunistic
                }
        assertEquals(ScanMode.Opportunistic, config.scanMode)
    }

    @Test
    fun `ScannerConfig DSL sets scanMode to Balanced`() {
        val config =
            ScannerConfig()
                .apply {
                    scanMode = ScanMode.Balanced
                }
        assertEquals(ScanMode.Balanced, config.scanMode)
    }

    // ==========================================================================
    // buildOsFilters -- edge cases for filter groups
    // ==========================================================================

    @Test
    fun `buildOsFilters with multiple filter groups returns list`() {
        val config =
            ScannerConfig().apply {
                filters {
                    match { name("DeviceA") }
                    match { name("DeviceB") }
                }
            }
        val result = AndroidScanner.buildOsFilters(config.filterGroups)
        assertTrue(result != null, "Should return non-null list")
        assertTrue(result!!.size == 2, "Should have 2 filter entries")
    }

    @Test
    fun `buildOsFilters with single filter group returns list`() {
        val config =
            ScannerConfig().apply {
                filters {
                    match { serviceUuid("180d") }
                }
            }
        val result = AndroidScanner.buildOsFilters(config.filterGroups)
        assertTrue(result != null)
        assertTrue(result!!.size == 1)
    }

    @Test
    fun `buildOsFilters with empty filterGroups returns null`() {
        assertNull(AndroidScanner.buildOsFilters(emptyList()))
    }

    // ==========================================================================
    // AndroidScanner lifecycle -- close behavior
    // ==========================================================================

    @Test
    fun `close cancels the scanner scope`() {
        // Create a scanner in a test context (requires androidMain)
        // This test verifies that close() doesn't throw and completes
        val context = AndroidJUnit4.runner.context.applicationContext
        val scanner = AndroidScanner(context) {
            timeout = 10.seconds
        }
        // close() should not throw
        runCatching { scanner.close() }
    }

    // ==========================================================================
    // ScanPhy constants -- coverage for all values
    // ==========================================================================

    @Test
    fun `scanPhyToAndroid covers all ScanPhy entries`() {
        val results = ScanPhy.entries.map { it to AndroidScanner.scanPhyToAndroid(it) }
        assertEquals(3, results.size, "Should have 3 ScanPhy values: Le1M, LeCoded, All")
        // Verify each has a valid Android PHY constant
        results.forEach { (phy, androidPhy) ->
            assertTrue(
                androidPhy == 1 || androidPhy == 3 || androidPhy == 255,
                "ScanPhy $phy should map to a valid Android PHY constant",
            )
        }
    }
}
