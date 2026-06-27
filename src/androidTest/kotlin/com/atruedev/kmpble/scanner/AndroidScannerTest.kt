package com.atruedev.kmpble.scanner

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidScannerTest {

    // =========================================================================
    // scanPhyToAndroid
    // =========================================================================

    @Test
    fun `scanPhyToAndroid Le1M returns PHY_LE_1M`() {
        assertEquals(
            BluetoothDevice.PHY_LE_1M,
            AndroidScanner.scanPhyToAndroid(ScanPhy.Le1M),
        )
    }

    @Test
    fun `scanPhyToAndroid LeCoded returns PHY_LE_CODED`() {
        assertEquals(
            BluetoothDevice.PHY_LE_CODED,
            AndroidScanner.scanPhyToAndroid(ScanPhy.LeCoded),
        )
    }

    @Test
    fun `scanPhyToAndroid All returns PHY_LE_ALL_SUPPORTED`() {
        assertEquals(
            ScanSettings.PHY_LE_ALL_SUPPORTED,
            AndroidScanner.scanPhyToAndroid(ScanPhy.All),
        )
    }

    @Test
    fun `scanPhyToAndroid covers all ScanPhy enum values`() {
        val results = ScanPhy.entries.map { it to AndroidScanner.scanPhyToAndroid(it) }
        assertEquals(3, results.size)
    }

    // =========================================================================
    // scanModeToAndroid
    // =========================================================================

    @Test
    fun `scanModeToAndroid Balanced returns SCAN_MODE_BALANCED`() {
        assertEquals(
            ScanSettings.SCAN_MODE_BALANCED,
            AndroidScanner.scanModeToAndroid(ScanMode.Balanced),
        )
    }

    @Test
    fun `scanModeToAndroid LowLatency returns SCAN_MODE_LOW_LATENCY`() {
        assertEquals(
            ScanSettings.SCAN_MODE_LOW_LATENCY,
            AndroidScanner.scanModeToAndroid(ScanMode.LowLatency),
        )
    }

    @Test
    fun `scanModeToAndroid LowPower returns SCAN_MODE_LOW_POWER`() {
        assertEquals(
            ScanSettings.SCAN_MODE_LOW_POWER,
            AndroidScanner.scanModeToAndroid(ScanMode.LowPower),
        )
    }

    @Test
    fun `scanModeToAndroid Opportunistic returns SCAN_MODE_OPPORTUNISTIC`() {
        assertEquals(
            ScanSettings.SCAN_MODE_OPPORTUNISTIC,
            AndroidScanner.scanModeToAndroid(ScanMode.Opportunistic),
        )
    }

    @Test
    fun `scanModeToAndroid covers all ScanMode enum values`() {
        val results = ScanMode.entries.map { it to AndroidScanner.scanModeToAndroid(it) }
        assertEquals(4, results.size)
        results.forEach { (_, value) ->
            assertTrue(
                value == ScanSettings.SCAN_MODE_BALANCED ||
                    value == ScanSettings.SCAN_MODE_LOW_LATENCY ||
                    value == ScanSettings.SCAN_MODE_LOW_POWER ||
                    value == ScanSettings.SCAN_MODE_OPPORTUNISTIC,
            )
        }
    }

    // =========================================================================
    // buildOsFilters
    // =========================================================================

    @Test
    fun `buildOsFilters with no predicates returns null`() {
        val config = ScannerConfig().apply {
            filters { /* no match blocks */ }
        }
        val result = AndroidScanner.buildOsFilters(config.filterGroups)
        assertNull(result)
    }

    @Test
    fun `buildOsFilters with empty filter groups returns null`() {
        val result = AndroidScanner.buildOsFilters(emptyList())
        assertNull(result)
    }

    @Test
    fun `buildOsFilters with service UUID creates valid ScanFilter`() {
        val config = ScannerConfig().apply {
            filters {
                match {
                    serviceUuid("180D")
                }
            }
        }
        val filters = AndroidScanner.buildOsFilters(config.filterGroups)
        assertNotNull(filters)
        assertEquals(1, filters.size)
        assertTrue(filters[0] is ScanFilter)
    }

    @Test
    fun `buildOsFilters with device name creates valid ScanFilter`() {
        val config = ScannerConfig().apply {
            filters {
                match {
                    name("TestDevice")
                }
            }
        }
        val filters = AndroidScanner.buildOsFilters(config.filterGroups)
        assertNotNull(filters)
        assertEquals(1, filters.size)
    }

    @Test
    fun `buildOsFilters with device address creates valid ScanFilter`() {
        val config = ScannerConfig().apply {
            filters {
                match {
                    address("AA:BB:CC:DD:EE:FF")
                }
            }
        }
        val filters = AndroidScanner.buildOsFilters(config.filterGroups)
        assertNotNull(filters)
        assertEquals(1, filters.size)
    }

    @Test
    fun `buildOsFilters with manufacturer data creates valid ScanFilter`() {
        val config = ScannerConfig().apply {
            filters {
                match {
                    manufacturerData(companyId = 0x004C)
                }
            }
        }
        val filters = AndroidScanner.buildOsFilters(config.filterGroups)
        assertNotNull(filters)
        assertEquals(1, filters.size)
    }

    @Test
    fun `buildOsFilters with manufacturer data and mask creates valid ScanFilter`() {
        val config = ScannerConfig().apply {
            filters {
                match {
                    manufacturerData(
                        companyId = 0x004C,
                        data = byteArrayOf(0x01, 0x02),
                        mask = byteArrayOf(0xFF.toByte(), 0xFF.toByte()),
                    )
                }
            }
        }
        val filters = AndroidScanner.buildOsFilters(config.filterGroups)
        assertNotNull(filters)
        assertEquals(1, filters.size)
    }

    @Test
    fun `buildOsFilters with multiple predicates creates single filter group`() {
        val config = ScannerConfig().apply {
            filters {
                match {
                    serviceUuid("180D")
                    name("TestDevice")
                }
            }
        }
        val filters = AndroidScanner.buildOsFilters(config.filterGroups)
        assertNotNull(filters)
        assertEquals(1, filters.size)
    }

    @Test
    fun `buildOsFilters with multiple match blocks creates multiple filter groups`() {
        val config = ScannerConfig().apply {
            filters {
                match {
                    serviceUuid("180D")
                }
                match {
                    name("TestDevice")
                }
            }
        }
        val filters = AndroidScanner.buildOsFilters(config.filterGroups)
        assertNotNull(filters)
        assertEquals(2, filters.size)
    }

    @Test
    fun `buildOsFilters with empty match block skipped`() {
        val config = ScannerConfig().apply {
            filters {
                match { /* empty */ }
            }
        }
        val result = AndroidScanner.buildOsFilters(config.filterGroups)
        assertNull(result)
    }

    // =========================================================================
    // ScannerConfig defaults
    // =========================================================================

    @Test
    fun `ScannerConfig defaults to Balanced mode`() {
        val config = ScannerConfig()
        assertEquals(ScanMode.Balanced, config.scanMode)
    }

    @Test
    fun `ScannerConfig defaults to All PHY`() {
        val config = ScannerConfig()
        assertEquals(ScanPhy.All, config.phy)
    }

    @Test
    fun `ScannerConfig defaults to legacyOnly true`() {
        val config = ScannerConfig()
        assertTrue(config.legacyOnly)
    }

    @Test
    fun `ScannerConfig defaults to no timeout`() {
        val config = ScannerConfig()
        assertNull(config.timeout)
    }

    @Test
    fun `ScannerConfig defaults to FirstThenChanges emission`() {
        val config = ScannerConfig()
        assertTrue(config.emission is EmissionPolicy.FirstThenChanges)
    }

    @Test
    fun `ScannerConfig can set all properties`() {
        val config = ScannerConfig().apply {
            scanMode = ScanMode.LowLatency
            phy = ScanPhy.Le1M
            legacyOnly = false
            timeout = kotlin.time.Duration.parse("PT30S")
            emission = EmissionPolicy.All
            filters {
                match {
                    serviceUuid("180D")
                    namePrefix("Test")
                }
            }
        }
        assertEquals(ScanMode.LowLatency, config.scanMode)
        assertEquals(ScanPhy.Le1M, config.phy)
        assertFalse(config.legacyOnly)
        assertNotNull(config.timeout)
        assertEquals(kotlin.time.Duration.parse("PT30S"), config.timeout)
        assertTrue(config.emission is EmissionPolicy.All)
        assertEquals(1, config.filterGroups.size)
        assertEquals(2, config.filterGroups[0].size)
    }

    // =========================================================================
    // ScannerConfig DSL
    // =========================================================================

    @Test
    fun `ScannerConfig DSL filters with single match`() {
        val config = ScannerConfig().apply {
            filters {
                match {
                    serviceUuid("180D")
                }
            }
        }
        assertEquals(1, config.filterGroups.size)
        assertEquals(1, config.filterGroups[0].size)
    }

    @Test
    fun `ScannerConfig DSL filters with multiple matches`() {
        val config = ScannerConfig().apply {
            filters {
                match {
                    serviceUuid("180D")
                }
                match {
                    serviceUuid("180A")
                }
            }
        }
        assertEquals(2, config.filterGroups.size)
        assertEquals(1, config.filterGroups[0].size)
        assertEquals(1, config.filterGroups[1].size)
    }

    @Test
    fun `ScannerConfig DSL filters with complex predicates`() {
        val config = ScannerConfig().apply {
            filters {
                match {
                    serviceUuid("180D")
                    namePrefix("HR")
                    rssi(min = -70)
                    manufacturerData(companyId = 0x004C)
                }
            }
        }
        assertEquals(1, config.filterGroups.size)
        assertEquals(4, config.filterGroups[0].size)
    }

    // =========================================================================
    // ScanPredicate types
    // =========================================================================

    @Test
    fun `ScanPredicate ServiceUuid stores UUID`() {
        val uuid = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
        val predicate = ScanPredicate.ServiceUuid(uuid)
        assertEquals(uuid, predicate.uuid)
    }

    @Test
    fun `ScanPredicate Name stores exact name`() {
        val predicate = ScanPredicate.Name("TestDevice")
        assertEquals("TestDevice", predicate.exact)
    }

    @Test
    fun `ScanPredicate NamePrefix stores prefix`() {
        val predicate = ScanPredicate.NamePrefix("Test")
        assertEquals("Test", predicate.prefix)
    }

    @Test
    fun `ScanPredicate Address stores MAC address`() {
        val predicate = ScanPredicate.Address("AA:BB:CC:DD:EE:FF")
        assertEquals("AA:BB:CC:DD:EE:FF", predicate.mac)
    }

    @Test
    fun `ScanPredicate ManufacturerData stores company ID`() {
        val predicate = ScanPredicate.ManufacturerData(companyId = 0x004C)
        assertEquals(0x004C, predicate.companyId)
    }

    @Test
    fun `ScanPredicate ManufacturerData stores data and mask`() {
        val data = byteArrayOf(0x01, 0x02)
        val mask = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
        val predicate = ScanPredicate.ManufacturerData(
            companyId = 0x004C,
            data = data,
            mask = mask,
        )
        assertEquals(0x004C, predicate.companyId)
        assertTrue(data.contentEquals(predicate.data))
        assertTrue(mask.contentEquals(predicate.mask))
    }

    @Test
    fun `ScanPredicate MinRssi stores RSSI threshold`() {
        val predicate = ScanPredicate.MinRssi(-70)
        assertEquals(-70, predicate.minRssi)
    }
}
