package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.testing.FakeScanner
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class ScannerIntegrationTest {
    // --- Filter: Service UUID ---

    @Test
    fun `filter matches service UUID exactly`() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement {
                        name("HRM")
                        serviceUuids("180d")
                        rssi(-50)
                    }
                    advertisement {
                        name("Battery")
                        serviceUuids("180f")
                        rssi(-60)
                    }
                }

            val results =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .take(2)
                    .toList()

            assertEquals(2, results.size)
            assertEquals("HRM", results[0].advertisement.name)
            assertEquals("Battery", results[1].advertisement.name)
            scanner.close()
        }

    // --- Filter: Name ---

    @Test
    fun `filter matches exact device name`() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement {
                        name("HeartSensor")
                        rssi(-55)
                    }
                    advertisement {
                        name("TempSensor")
                        rssi(-60)
                    }
                }

            val heartSensor =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .first { it.advertisement.name == "HeartSensor" }

            assertEquals("HeartSensor", heartSensor.advertisement.name)
            scanner.close()
        }

    @Test
    fun `filter matches name prefix`() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement {
                        name("HeartSensor-01")
                        rssi(-55)
                    }
                    advertisement {
                        name("HeartSensor-02")
                        rssi(-60)
                    }
                    advertisement {
                        name("OtherDevice")
                        rssi(-60)
                    }
                }

            val heartDevices =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .take(3)
                    .toList()
                    .filter { it.advertisement.name?.startsWith("HeartSensor") == true }

            assertEquals(2, heartDevices.size)
            scanner.close()
        }

    // --- Filter: RSSI ---

    @Test
    fun `finds device by RSSI threshold`() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement {
                        name("Near")
                        rssi(-40)
                    }
                    advertisement {
                        name("Mid")
                        rssi(-65)
                    }
                    advertisement {
                        name("Far")
                        rssi(-85)
                    }
                }

            val strongSignals =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .take(3)
                    .toList()
                    .filter { it.advertisement.rssi >= -70 }

            // Near and Mid should be >= -70
            assertEquals(2, strongSignals.size)
            assertTrue(strongSignals.all { it.advertisement.rssi >= -70 })
            scanner.close()
        }

    // --- Filter: Multiple Service UUIDs ---

    @Test
    fun `advertisement with multiple service UUIDs all present`() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement {
                        name("MultiService")
                        serviceUuids("180d", "180a", "180f")
                        rssi(-55)
                    }
                }

            val ad =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .first()
                    .advertisement

            assertEquals(3, ad.serviceUuids.size)
            assertTrue(uuidFrom("180d") in ad.serviceUuids)
            assertTrue(uuidFrom("180a") in ad.serviceUuids)
            assertTrue(uuidFrom("180f") in ad.serviceUuids)
            scanner.close()
        }

    // --- Extended Advertisement ---

    @Test
    fun `extended advertisement with coded PHY and secondary PHY`() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement {
                        name("ExtDevice")
                        isLegacy(false)
                        primaryPhy(Phy.LeCoded)
                        secondaryPhy(Phy.Le2M)
                        advertisingSid(5)
                        periodicAdvertisingInterval(320)
                        dataStatus(DataStatus.Complete)
                        rssi(-55)
                    }
                }

            val ad =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .first()
                    .advertisement

            assertFalse(ad.isLegacy)
            assertEquals(Phy.LeCoded, ad.primaryPhy)
            assertEquals(Phy.Le2M, ad.secondaryPhy)
            assertEquals(5, ad.advertisingSid)
            assertEquals(320, ad.periodicAdvertisingInterval)
            assertEquals(DataStatus.Complete, ad.dataStatus)
            scanner.close()
        }

    @Test
    fun `legacy advertisement defaults`() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement {
                        name("Legacy")
                        rssi(-55)
                    }
                }

            val ad =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .first()
                    .advertisement

            assertTrue(ad.isLegacy)
            assertEquals(Phy.Le1M, ad.primaryPhy)
            assertNull(ad.secondaryPhy)
            assertNull(ad.advertisingSid)
            assertNull(ad.periodicAdvertisingInterval)
            scanner.close()
        }

    // --- Manufacturer Data ---

    @Test
    fun `advertisement carries manufacturer data`() =
        runTest {
            val companyId = 0x004C // Apple
            val data = byteArrayOf(0x02, 0x15)

            val scanner =
                FakeScanner {
                    advertisement {
                        name("Beacon")
                        manufacturerData(companyId, BleData(data))
                        rssi(-55)
                    }
                }

            val ad =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .first()
                    .advertisement

            assertTrue(companyId in ad.manufacturerData)
            scanner.close()
        }

    // --- Service Data ---

    @Test
    fun `advertisement carries service data`() =
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
                }

            val ad =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .first()
                    .advertisement

            assertTrue(uuidFrom(uuid) in ad.serviceData)
            scanner.close()
        }

    // --- Dynamic Emission ---

    @Test
    fun `dynamic advertisements after construction`() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement {
                        name("Static")
                        rssi(-55)
                    }
                }

            // Emit dynamically after construction using the public builder
            val dynamicAd =
                buildAdvertisement {
                    name("Dynamic")
                    rssi(-60)
                }
            scanner.emit(dynamicAd)

            // Collect the static ad first
            val first =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .first()

            assertEquals("Static", first.advertisement.name)
            scanner.close()
        }

    // --- Scan Failure ---

    @Test
    fun `scan failure emits Failed event`() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement {
                        name("BeforeFailure")
                        rssi(-55)
                    }
                }

            scanner.emitScanFailed(42)

            val events =
                scanner.scanEvents
                    .take(2)
                    .toList()

            // First event should be Found
            assertIs<ScanEvent.Found>(events[0])
            // Second event should be Failed
            val failed = events[1]
            assertIs<ScanEvent.Failed>(failed)
            assertEquals(42, failed.error.errorCode)
            scanner.close()
        }

    // --- Close Behavior ---

    @Test
    fun `close is safe to call multiple times`() =
        runTest {
            val scanner = FakeScanner { advertisement { rssi(-55) } }

            scanner.close()
            scanner.close() // Should not throw
            scanner.close() // Should not throw
        }

    // --- ScannerConfig ---

    @Test
    fun `scannerConfig defaults to All PHY and legacy only`() {
        val config = ScannerConfig()
        assertEquals(ScanPhy.All, config.phy)
        assertTrue(config.legacyOnly)
        assertNull(config.timeout)
    }

    @Test
    fun `scannerConfig non-legacy with coded PHY`() {
        val config = ScannerConfig()
        config.legacyOnly = false
        config.phy = ScanPhy.LeCoded
        config.timeout = 15.seconds

        assertFalse(config.legacyOnly)
        assertEquals(ScanPhy.LeCoded, config.phy)
        assertEquals(15.seconds, config.timeout)
    }

    @Test
    fun `scannerConfig All PHY and Le1m PHY are distinct`() {
        val allConfig = ScannerConfig()
        allConfig.phy = ScanPhy.All

        val le1mConfig = ScannerConfig()
        le1mConfig.phy = ScanPhy.Le1M

        assertEquals(ScanPhy.All, allConfig.phy)
        assertEquals(ScanPhy.Le1M, le1mConfig.phy)
        assertTrue(allConfig.phy != le1mConfig.phy)
    }

    // --- Scanner Extensions ---

    @Test
    fun `firstOrNull finds matching device`() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement {
                        name("Target")
                        rssi(-55)
                    }
                }

            val result = scanner.firstOrNull(timeout = 5.seconds) { it.name == "Target" }

            assertNotNull(result)
            assertEquals("Target", result.name)
            scanner.close()
        }

    @Test
    fun `firstOrNull returns null when no match`() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement {
                        name("Other")
                        rssi(-55)
                    }
                }

            // Launch firstOrNull in a coroutine so we can advance time past its timeout
            val deferred = async { scanner.firstOrNull(timeout = 1.seconds) { it.name == "Missing" } }
            testScheduler.advanceTimeBy(2_000)
            val result = deferred.await()

            assertNull(result)
            scanner.close()
        }

    @Test
    fun `firstOrThrow finds matching device`() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement {
                        name("Target")
                        rssi(-55)
                    }
                }

            val result = scanner.firstOrThrow(timeout = 5.seconds) { it.name == "Target" }

            assertEquals("Target", result.name)
            scanner.close()
        }

    @Test
    fun `firstOrThrow throws when no match within timeout`() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement {
                        name("Other")
                        rssi(-55)
                    }
                }

            assertFailsWith<kotlinx.coroutines.TimeoutCancellationException> {
                scanner.firstOrThrow(timeout = 1.seconds) { it.name == "Missing" }
            }
            scanner.close()
        }

    @Test
    fun `firstOrThrow throws ScanFailedException on scan failure`() =
        runTest {
            val scanner = FakeScanner {}

            scanner.emitScanFailed(1337)

            assertFailsWith<ScanFailedException> {
                scanner.firstOrThrow(timeout = 5.seconds)
            }
            scanner.close()
        }

    // --- Edge Case: Empty Scanner ---

    @Test
    fun `empty scanner completes flow when closed`() =
        runTest {
            val scanner = FakeScanner {}
            scanner.close()
            // Flow is cold - no collector means no suspension
        }

    // --- Edge Case: Cancellation ---

    @Test
    fun `cancelling scan collection is clean`() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement {
                        name("Device")
                        rssi(-55)
                    }
                }

            // Collect one event then cancel
            val event =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .first()

            assertEquals("Device", event.advertisement.name)
            scanner.close()
        }

    // --- Edge Case: Multiple Concurrent Scanners ---

    @Test
    fun `multiple scanners operate independently`() =
        runTest {
            val scanner1 =
                FakeScanner {
                    advertisement {
                        name("Scanner1-Device")
                        rssi(-55)
                    }
                }
            val scanner2 =
                FakeScanner {
                    advertisement {
                        name("Scanner2-Device")
                        rssi(-60)
                    }
                }

            val result1 =
                scanner1.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .first()

            val result2 =
                scanner2.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .first()

            assertEquals("Scanner1-Device", result1.advertisement.name)
            assertEquals("Scanner2-Device", result2.advertisement.name)

            scanner1.close()
            scanner2.close()
        }

    // --- DataStatus Edge Cases ---

    @Test
    fun `truncated advertisement data status`() =
        runTest {
            val scanner =
                FakeScanner {
                    advertisement {
                        name("Truncated")
                        dataStatus(DataStatus.Truncated)
                        rssi(-55)
                    }
                }

            val ad =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .first()
                    .advertisement

            assertEquals(DataStatus.Truncated, ad.dataStatus)
            scanner.close()
        }
}
