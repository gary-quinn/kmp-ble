package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.testing.FakeScanner
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

/**
 * Scanner filter and emission behavior tests that run on the commonTest
 * source set. Tests the Kotlin-side matching logic that applies after
 * advertisements arrive, independent of platform-specific OS filters.
 *
 * Unlike device-level filter construction tests (which validate Android OS
 * filter objects), these tests exercise the post-filter pipeline and
 * EmissionPolicy deduplication that run regardless of platform.
 */
@OptIn(ExperimentalUuidApi::class)
class FakeScannerFiltersTest {
    // =========================================================================
    // Post-filter: name prefix matching (simulated via manual filtering)
    // =========================================================================

    @Test
    fun `name prefix filter matches advertisements starting with prefix`() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement {
                        name("HeartRate-001")
                        rssi(-55)
                    }
                    advertisement {
                        name("HeartRate-002")
                        rssi(-60)
                    }
                    advertisement {
                        name("TempSensor-001")
                        rssi(-65)
                    }
                }

            val results =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .take(3)
                    .toList()

            val heartRateDevices = results.filter { it.advertisement.name?.startsWith("HeartRate") == true }
            assertEquals(2, heartRateDevices.size)
            scanner.close()
        }

    @Test
    fun `name prefix filter does not match partial substring`() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement {
                        name("MySensor")
                        rssi(-55)
                    }
                    advertisement {
                        name("SensorHub")
                        rssi(-60)
                    }
                }

            val results =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .take(2)
                    .toList()

            val prefixMatch = results.filter { it.advertisement.name?.startsWith("Sensor") == true }
            assertEquals(1, prefixMatch.size)
            assertEquals("SensorHub", prefixMatch[0].advertisement.name)
            scanner.close()
        }

    @Test
    fun `name prefix filter handles null names`() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement {
                        rssi(-55)
                    }
                }

            val results =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .take(1)
                    .toList()

            val prefixMatch = results.filter { it.advertisement.name?.startsWith("Anything") == true }
            assertEquals(0, prefixMatch.size)
            scanner.close()
        }

    // =========================================================================
    // Post-filter: RSSI threshold matching (simulated via manual filtering)
    // =========================================================================

    @Test
    fun `RSSI filter selects devices above threshold`() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement {
                        name("Strong")
                        rssi(-45)
                    }
                    advertisement {
                        name("Medium")
                        rssi(-65)
                    }
                    advertisement {
                        name("Weak")
                        rssi(-85)
                    }
                }

            val results =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .take(3)
                    .toList()

            val strongEnough = results.filter { it.advertisement.rssi >= -70 }
            assertEquals(2, strongEnough.size)
            assertTrue(strongEnough.any { it.advertisement.name == "Strong" })
            assertTrue(strongEnough.any { it.advertisement.name == "Medium" })
            scanner.close()
        }

    @Test
    fun `RSSI filter boundary condition exact match`() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement {
                        name("Exact")
                        rssi(-70)
                    }
                }

            val results =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .take(1)
                    .toList()

            val aboveThreshold = results.filter { it.advertisement.rssi >= -70 }
            assertEquals(1, aboveThreshold.size)
            scanner.close()
        }

    // =========================================================================
    // EmissionPolicy: All - emits every advertisement
    // =========================================================================

    @Test
    fun `emission policy All emits duplicate advertisements`() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement {
                        name("Device")
                        rssi(-55)
                    }
                    advertisement {
                        name("Device")
                        rssi(-60)
                    }
                    advertisement {
                        name("Device")
                        rssi(-50)
                    }
                }

            val results =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .take(3)
                    .toList()

            assertEquals(3, results.size)
            scanner.close()
        }

    // =========================================================================
    // EmissionPolicy: FirstThenChanges configuration
    // =========================================================================

    @Test
    fun `FirstThenChanges with rssi threshold configured`() {
        val policy = EmissionPolicy.FirstThenChanges(rssiThreshold = 15)
        assertEquals(15, policy.rssiThreshold)
    }

    @Test
    fun `FirstThenChanges default rssi threshold is 5`() {
        val policy = EmissionPolicy.FirstThenChanges()
        assertEquals(5, policy.rssiThreshold)
    }

    // =========================================================================
    // EmissionPolicy: enum coverage
    // =========================================================================

    @Test
    fun `EmissionPolicy All is singleton`() {
        val all1 = EmissionPolicy.All
        val all2 = EmissionPolicy.All
        assertEquals(all1, all2)
    }

    @Test
    fun `EmissionPolicy FirstThenChanges equality`() {
        val a = EmissionPolicy.FirstThenChanges(rssiThreshold = 10)
        val b = EmissionPolicy.FirstThenChanges(rssiThreshold = 10)
        val c = EmissionPolicy.FirstThenChanges(rssiThreshold = 20)
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    // =========================================================================
    // ScannerConfig: timeout and emission combined
    // =========================================================================

    @Test
    fun `ScannerConfig with all options set`() {
        val config =
            ScannerConfig().apply {
                timeout = 30.seconds
                emission = EmissionPolicy.All
                legacyOnly = false
                phy = ScanPhy.LeCoded
            }

        assertEquals(30.seconds, config.timeout)
        assertTrue(config.emission is EmissionPolicy.All)
        assertFalse(config.legacyOnly)
        assertEquals(ScanPhy.LeCoded, config.phy)
    }

    @Test
    fun `ScannerConfig default emission policy is FirstThenChanges`() {
        val config = ScannerConfig()
        assertTrue(config.emission is EmissionPolicy.FirstThenChanges)
    }

    // =========================================================================
    // Advertisement identifier matching via address
    // =========================================================================

    @Test
    fun `advertisement identifier matches case-insensitively`() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement {
                        identifier("aa:bb:cc:dd:ee:ff")
                        name("Lower")
                    }
                }

            val ad =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .take(1)
                    .toList()
                    .first()
                    .advertisement

            val matches = ad.identifier.value.equals("AA:BB:CC:DD:EE:FF", ignoreCase = true)
            assertTrue(matches)
            scanner.close()
        }

    // =========================================================================
    // Advertisement data completeness via FakeAdvertisementBuilder
    // =========================================================================

    @Test
    fun `FakeAdvertisementBuilder sets all extended advertisement fields`() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement {
                        name("Ext")
                        identifier("11:22:33:44:55:66")
                        rssi(-50)
                        txPower(0)
                        isConnectable(false)
                        isLegacy(false)
                        primaryPhy(Phy.LeCoded)
                        secondaryPhy(Phy.Le2M)
                        advertisingSid(7)
                        periodicAdvertisingInterval(240)
                        dataStatus(DataStatus.Truncated)
                    }
                }

            val ad =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .take(1)
                    .toList()
                    .first()
                    .advertisement

            assertEquals("Ext", ad.name)
            assertEquals("11:22:33:44:55:66", ad.identifier.value)
            assertEquals(-50, ad.rssi)
            assertEquals(0, ad.txPower)
            assertFalse(ad.isConnectable)
            assertFalse(ad.isLegacy)
            assertEquals(Phy.LeCoded, ad.primaryPhy)
            assertEquals(Phy.Le2M, ad.secondaryPhy)
            assertEquals(7, ad.advertisingSid)
            assertEquals(240, ad.periodicAdvertisingInterval)
            assertEquals(DataStatus.Truncated, ad.dataStatus)
            scanner.close()
        }

    // =========================================================================
    // Manufacturer data in advertisements
    // =========================================================================

    @Test
    fun `advertisement carries manufacturer data and can be matched`() =
        runTest {
            val companyId = 0x004C
            val data = byteArrayOf(0x02, 0x15)

            val scanner =
                FakeScanner {
                    advertisement {
                        name("Beacon")
                        manufacturerData(companyId, BleData(data))
                        rssi(-55)
                    }
                    advertisement {
                        name("NonBeacon")
                        rssi(-60)
                    }
                }

            val results =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .take(2)
                    .toList()

            val withAppleMfr = results.filter { companyId in it.advertisement.manufacturerData }
            assertEquals(1, withAppleMfr.size)
            assertEquals("Beacon", withAppleMfr[0].advertisement.name)
            scanner.close()
        }

    // =========================================================================
    // Service data in advertisements
    // =========================================================================

    @Test
    fun `advertisement carries service data and can be matched`() =
        runTest {
            val uuid = "180d"
            val data = byteArrayOf(0x01, 0x02, 0x03)

            val scanner =
                FakeScanner {
                    advertisement {
                        name("ServiceData")
                        serviceData(uuid, BleData(data))
                        rssi(-55)
                    }
                    advertisement {
                        name("NoServiceData")
                        rssi(-60)
                    }
                }

            val results =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .take(2)
                    .toList()

            val withServiceData = results.filter { uuidFrom(uuid) in it.advertisement.serviceData }
            assertEquals(1, withServiceData.size)
            assertEquals("ServiceData", withServiceData[0].advertisement.name)
            scanner.close()
        }

    // =========================================================================
    // Multiple advertisement data types combined
    // =========================================================================

    @Test
    fun `advertisement can carry both manufacturer and service data`() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement {
                        name("MultiData")
                        manufacturerData(0x004C, BleData(byteArrayOf(0x02, 0x15)))
                        serviceData("180d", BleData(byteArrayOf(0x01, 0x02)))
                        rssi(-55)
                    }
                }

            val ad =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .take(1)
                    .toList()
                    .first()
                    .advertisement

            assertTrue(0x004C in ad.manufacturerData)
            assertTrue(uuidFrom("180d") in ad.serviceData)
            scanner.close()
        }

    // =========================================================================
    // Edge case: manufacturer data with null data (company ID only match)
    // =========================================================================

    @Test
    fun `manufacturer data company ID present check works`() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement {
                        name("WithMfr")
                        manufacturerData(0x004C, BleData(byteArrayOf(0x01, 0x02)))
                        manufacturerData(0x0123, BleData(byteArrayOf(0x03)))
                        rssi(-55)
                    }
                }

            val ad =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .take(1)
                    .toList()
                    .first()
                    .advertisement

            assertTrue(0x004C in ad.manufacturerData)
            assertTrue(0x0123 in ad.manufacturerData)
            assertFalse(0x9999 in ad.manufacturerData)
            scanner.close()
        }

    // =========================================================================
    // Service UUID presence check
    // =========================================================================

    @Test
    fun `service UUIDs can be checked for presence`() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement {
                        name("MultiUuid")
                        serviceUuids("180d", "180a", "180f")
                        rssi(-55)
                    }
                }

            val ad =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .take(1)
                    .toList()
                    .first()
                    .advertisement

            assertTrue(uuidFrom("180d") in ad.serviceUuids)
            assertTrue(uuidFrom("180a") in ad.serviceUuids)
            assertTrue(uuidFrom("180f") in ad.serviceUuids)
            assertFalse(uuidFrom("1809") in ad.serviceUuids)
            scanner.close()
        }
}
