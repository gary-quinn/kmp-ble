package com.atruedev.kmpble.scanner

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class AndroidScannerTest {

    // =========================================================================
    // scanPhyToAndroid
    // =========================================================================

    @Test
    fun `Le1M maps to PHY_LE_1M`() {
        assertEquals(
            BluetoothDevice.PHY_LE_1M,
            AndroidScanner.scanPhyToAndroid(ScanPhy.Le1M),
        )
    }

    @Test
    fun `LeCoded maps to PHY_LE_CODED`() {
        assertEquals(
            BluetoothDevice.PHY_LE_CODED,
            AndroidScanner.scanPhyToAndroid(ScanPhy.LeCoded),
        )
    }

    @Test
    fun `All maps to PHY_LE_ALL_SUPPORTED`() {
        assertEquals(
            ScanSettings.PHY_LE_ALL_SUPPORTED,
            AndroidScanner.scanPhyToAndroid(ScanPhy.All),
        )
    }

    // =========================================================================
    // buildOsFilters - service UUID
    // =========================================================================

    @Test
    fun `single service UUID filter`() {
        val filterGroups = filterGroups {
            match { serviceUuid("180d") }
        }
        val result = AndroidScanner.buildOsFilters(filterGroups)

        assertNotNull(result)
        assertEquals(1, result.size)
        assertNotNull(result[0].serviceUuid)
    }

    @Test
    fun `multiple service UUIDs in separate AND groups produce OR'd filters`() {
        val filterGroups = filterGroups {
            match { serviceUuid("180d") }
            match { serviceUuid("180a") }
        }
        val result = AndroidScanner.buildOsFilters(filterGroups)

        assertNotNull(result)
        assertEquals(2, result.size)
        assertNotNull(result[0].serviceUuid)
        assertNotNull(result[1].serviceUuid)
    }

    // =========================================================================
    // buildOsFilters - name
    // =========================================================================

    @Test
    fun `exact name filter`() {
        val filterGroups = filterGroups {
            match { name("TestDevice") }
        }
        val result = AndroidScanner.buildOsFilters(filterGroups)

        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals("TestDevice", result[0].deviceName)
    }

    // =========================================================================
    // buildOsFilters - address
    // =========================================================================

    @Test
    fun `address filter`() {
        val filterGroups = filterGroups {
            match { address("AA:BB:CC:DD:EE:FF") }
        }
        val result = AndroidScanner.buildOsFilters(filterGroups)

        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals("AA:BB:CC:DD:EE:FF", result[0].deviceAddress)
    }

    // =========================================================================
    // buildOsFilters - manufacturer data
    // =========================================================================

    @Test
    fun `manufacturer data filter with company ID only`() {
        val filterGroups = filterGroups {
            match { manufacturerData(companyId = 0x004C /* Apple */) }
        }
        val result = AndroidScanner.buildOsFilters(filterGroups)

        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals(0x004C, result[0].manufacturerId)
        assertNull(result[0].manufacturerData)
        assertNull(result[0].manufacturerDataMask)
    }

    @Test
    fun `manufacturer data filter with data and mask`() {
        val data = byteArrayOf(0x02, 0x15.toByte())
        val mask = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
        val filterGroups = filterGroups {
            match {
                manufacturerData(
                    companyId = 0x004C,
                    data = data,
                    mask = mask,
                )
            }
        }
        val result = AndroidScanner.buildOsFilters(filterGroups)

        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals(0x004C, result[0].manufacturerId)
        assertEquals(data, result[0].manufacturerData)
        assertEquals(mask, result[0].manufacturerDataMask)
    }

    // =========================================================================
    // buildOsFilters - service data
    // =========================================================================

    @Test
    fun `service data filter with UUID only`() {
        val filterGroups = filterGroups {
            match { serviceData(uuid = "180d") }
        }
        val result = AndroidScanner.buildOsFilters(filterGroups)

        assertNotNull(result)
        assertEquals(1, result.size)
        assertNotNull(result[0].serviceDataUuid)
        assertNull(result[0].serviceData)
        assertNull(result[0].serviceDataMask)
    }

    @Test
    fun `service data filter with data and mask`() {
        val data = byteArrayOf(0x06, 0x42, 0x00)
        val mask = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val filterGroups = filterGroups {
            match { serviceData(uuid = "feaa", data = data, mask = mask) }
        }
        val result = AndroidScanner.buildOsFilters(filterGroups)

        assertNotNull(result)
        assertEquals(1, result.size)
        assertNotNull(result[0].serviceDataUuid)
        assertEquals(data, result[0].serviceData)
        assertEquals(mask, result[0].serviceDataMask)
    }

    // =========================================================================
    // buildOsFilters - combined AND predicates
    // =========================================================================

    @Test
    fun `combined name and service UUID in one AND group`() {
        val filterGroups = filterGroups {
            match {
                name("HeartRate")
                serviceUuid("180d")
            }
        }
        val result = AndroidScanner.buildOsFilters(filterGroups)

        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals("HeartRate", result[0].deviceName)
        assertNotNull(result[0].serviceUuid)
    }

    @Test
    fun `combined address and manufacturer data in one AND group`() {
        val filterGroups = filterGroups {
            match {
                address("11:22:33:44:55:66")
                manufacturerData(companyId = 0x06 /* Microsoft */)
            }
        }
        val result = AndroidScanner.buildOsFilters(filterGroups)

        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals("11:22:33:44:55:66", result[0].deviceAddress)
        assertEquals(0x06, result[0].manufacturerId)
    }

    // =========================================================================
    // buildOsFilters - post-filter-only predicates (should produce null)
    // =========================================================================

    @Test
    fun `namePrefix only produces null group and is dropped`() {
        // NamePrefix is post-filter only - no OS-level equivalent
        val filterGroups = filterGroups {
            match { namePrefix("HR") }
        }
        val result = AndroidScanner.buildOsFilters(filterGroups)

        assertNull(result)
    }

    @Test
    fun `rssi only produces null group and is dropped`() {
        // MinRssi is post-filter only - no OS-level equivalent
        val filterGroups = filterGroups {
            match { rssi(-60) }
        }
        val result = AndroidScanner.buildOsFilters(filterGroups)

        assertNull(result)
    }

    @Test
    fun `post-filter-only combined with hardware filter still produces a filter`() {
        val filterGroups = filterGroups {
            match {
                namePrefix("HR")
                serviceUuid("180d")
            }
        }
        val result = AndroidScanner.buildOsFilters(filterGroups)

        assertNotNull(result)
        assertEquals(1, result.size)
        assertNotNull(result[0].serviceUuid)
    }

    // =========================================================================
    // buildOsFilters - empty input
    // =========================================================================

    @Test
    fun `empty filter groups returns null`() {
        val result = AndroidScanner.buildOsFilters(emptyList())
        assertNull(result)
    }

    @Test
    fun `empty match block in filter groups produces no result`() {
        val filterGroups = filterGroups {
            // match without any predicates produces empty group
            // which FiltersScope skips (predicates.isEmpty check)
        }
        val result = AndroidScanner.buildOsFilters(filterGroups)
        assertNull(result)
    }

    // =========================================================================
    // buildOsFilters - mixed OR groups (one hardware, one post-filter-only)
    // =========================================================================

    @Test
    fun `one hardware group and one post-filter-only group drops the post-filter group`() {
        val filterGroups = filterGroups {
            match { serviceUuid("180d") }
            match { namePrefix("Device") }
        }
        val result = AndroidScanner.buildOsFilters(filterGroups)

        assertNotNull(result)
        assertEquals(1, result.size)
        assertNotNull(result[0].serviceUuid)
    }

    // =========================================================================
    // buildOsFilters - multiple hardware predicates in one group with post-filter
    // =========================================================================

    @Test
    fun `all hardware predicate types combined in single match`() {
        val filterGroups = filterGroups {
            match {
                name("MyDevice")
                serviceUuid("180d")
                address("AA:BB:CC:DD:EE:FF")
                manufacturerData(companyId = 0x004C)
                serviceData(uuid = "180a")
            }
        }
        val result = AndroidScanner.buildOsFilters(filterGroups)

        assertNotNull(result)
        assertEquals(1, result.size)
        val filter = result[0]
        assertEquals("MyDevice", filter.deviceName)
        assertNotNull(filter.serviceUuid)
        assertEquals("AA:BB:CC:DD:EE:FF", filter.deviceAddress)
        assertEquals(0x004C, filter.manufacturerId)
        assertNotNull(filter.serviceDataUuid)
    }

    // =========================================================================
    // buildOsFilters - edge cases for data/mask
    // =========================================================================

    @Test
    fun `manufacturer data with data and null mask`() {
        val data = byteArrayOf(0x01, 0x02)
        val filterGroups = filterGroups {
            match {
                manufacturerData(
                    companyId = 0x004C,
                    data = data,
                    mask = null,
                )
            }
        }
        val result = AndroidScanner.buildOsFilters(filterGroups)

        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals(data, result[0].manufacturerData)
        assertNull(result[0].manufacturerDataMask)
    }

    @Test
    fun `manufacturer data with null data is skipped`() {
        // When data is null, ScanFilter.Builder skips setManufacturerData
        // and hasOsPredicate stays false => null result
        val filterGroups = filterGroups {
            match {
                manufacturerData(
                    companyId = 0x004C,
                    data = null,
                )
            }
        }
        val result = AndroidScanner.buildOsFilters(filterGroups)

        // data=null bypasses the setManufacturerData block entirely
        assertNull(result)
    }

    // =========================================================================
    // buildOsFilters - service data with null data
    // =========================================================================

    @Test
    fun `service data with only UUID and null data`() {
        // ServiceData predicate with null data still sets serviceDataUuid
        // on the builder, so hasOsPredicate = true
        val filterGroups = filterGroups {
            match {
                serviceData(uuid = "feaa", data = null, mask = null)
            }
        }
        val result = AndroidScanner.buildOsFilters(filterGroups)

        assertNotNull(result)
        assertEquals(1, result.size)
        assertNotNull(result[0].serviceDataUuid)
        assertNull(result[0].serviceData)
        assertNull(result[0].serviceDataMask)
    }

    // =========================================================================
    // ScannerConfig DSL integration
    // =========================================================================

    @Test
    fun `ScannerConfig default values are correct`() {
        val config = ScannerConfig()
        assertNull(config.timeout)
        assertTrue(config.emission is EmissionPolicy.FirstThenChanges)
        assertTrue(config.legacyOnly)
        assertEquals(ScanPhy.All, config.phy)
    }

    @Test
    fun `ScannerConfig DSL sets custom values`() {
        val config = ScannerConfig().apply {
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
    fun `ScannerConfig filters DSL produces expected filter groups`() {
        val config = ScannerConfig().apply {
            filters {
                match {
                    name("DeviceA")
                    serviceUuid("180d")
                }
                match { name("DeviceB") }
            }
        }
        val osFilters = AndroidScanner.buildOsFilters(config.filterGroups)
        assertNotNull(osFilters)
        assertEquals(2, osFilters.size)
    }

    // =========================================================================
    // ScanSettings builder verification via AndroidScanner construction attempt
    // =========================================================================

    @Test
    fun `scanPhyToAndroid covers all ScanPhy values`() {
        // Verify exhaustive coverage of the ScanPhy enum
        val phyValues = ScanPhy.entries
        for (phy in phyValues) {
            val androidPhy = AndroidScanner.scanPhyToAndroid(phy)
            // Android PHY constant must be a non-zero int
            assertTrue(androidPhy > 0, "PHY mapping for $phy returned $androidPhy")
        }
    }
}

// ============================================================================
// Helper: construct filter groups via the public DSL
// ============================================================================

private fun filterGroups(block: FiltersScope.() -> Unit): List<List<ScanPredicate>> {
    val scope = FiltersScope().apply(block)
    return scope.matchGroups
}
