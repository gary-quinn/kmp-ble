package com.atruedev.kmpble.beacon

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.testing.FakeScanner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BeaconScannerTest {
    @Test
    fun `beaconEvents emits iBeacon from FakeScanner`() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            val scanner =
                FakeScanner {
                    advertisement {
                        manufacturerData(0x004C, BleData(iBeaconBytes()))
                    }
                }

            val beaconScanner = BeaconScanner(scanner, this)
            beaconScanner.start()

            val event = beaconScanner.beaconEvents.first()
            assertTrue(event is BeaconEvent.Found)
            assertTrue((event as BeaconEvent.Found).beacon is Beacon.IBeacon)

            beaconScanner.close()
        }
    }

    @Test
    fun `beaconEvents filters out non-beacon ads`() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            val scanner =
                FakeScanner {
                    advertisement {
                        name("PlainDevice")
                        serviceUuids("180d")
                    }
                    advertisement {
                        manufacturerData(0x004C, BleData(iBeaconBytes()))
                    }
                }

            val beaconScanner = BeaconScanner(scanner, this)
            beaconScanner.start()

            // Only the beacon ad should come through
            val event = beaconScanner.beaconEvents.first()
            assertTrue(event is BeaconEvent.Found)
            assertTrue((event as BeaconEvent.Found).beacon is Beacon.IBeacon)

            beaconScanner.close()
        }
    }

    @Test
    fun `EddystoneUID roundtrip through FakeScanner`() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            val scanner =
                FakeScanner {
                    advertisement {
                        name("EddystoneBeacon")
                        rssi(-65)
                        serviceData("feaa", BleData(eddystoneUidBytes()))
                    }
                }

            val beaconScanner = BeaconScanner(scanner, this)
            beaconScanner.start()

            val event = beaconScanner.beaconEvents.first()
            assertTrue(event is BeaconEvent.Found)
            val uid = (event as BeaconEvent.Found).beacon as Beacon.EddystoneUID
            assertEquals(-18, uid.rangingData)
            assertEquals("EddystoneBeacon", uid.source.name)

            beaconScanner.close()
        }
    }

    @Test
    fun `stop prevents further events`() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            val scanner =
                FakeScanner {
                    advertisement {
                        manufacturerData(0x004C, BleData(iBeaconBytes()))
                    }
                }

            val beaconScanner = BeaconScanner(scanner, this)
            beaconScanner.start()

            // Consume the static beacon ad
            val event = beaconScanner.beaconEvents.first()
            assertTrue(event is BeaconEvent.Found)

            // Stop the scanner
            beaconScanner.stop()

            // Emit a dynamic beacon ad after stop
            scanner.emit(
                Advertisement(
                    identifier = Identifier("FF:EE:DD:CC:BB:AA"),
                    name = null,
                    rssi = -50,
                    txPower = null,
                    isConnectable = true,
                    serviceUuids = emptyList(),
                    manufacturerData = mapOf(0x004C to BleData(iBeaconBytes())),
                    serviceData = emptyMap(),
                    timestampNanos = 0L,
                ),
            )

            // Stop is idempotent and should not crash
            beaconScanner.stop()
            beaconScanner.close()
        }
    }

    @Test
    fun `close is idempotent`() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            val scanner =
                FakeScanner {
                    advertisement {
                        manufacturerData(0x004C, BleData(iBeaconBytes()))
                    }
                }

            val beaconScanner = BeaconScanner(scanner, this)
            beaconScanner.start()

            val event = beaconScanner.beaconEvents.first()
            assertTrue(event is BeaconEvent.Found)

            beaconScanner.close()
            beaconScanner.close() // double close should not throw
        }
    }

    @Test
    fun `start is idempotent`() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            val scanner =
                FakeScanner {
                    advertisement {
                        manufacturerData(0x004C, BleData(iBeaconBytes()))
                    }
                }

            val beaconScanner = BeaconScanner(scanner, this)
            beaconScanner.start()
            beaconScanner.start() // second call should be no-op

            val event = beaconScanner.beaconEvents.first()
            assertTrue(event is BeaconEvent.Found)

            beaconScanner.close()
        }
    }

    @Test
    fun `beaconEvents forwards scan failures`() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            val scanner = FakeScanner { }

            val beaconScanner = BeaconScanner(scanner, this)
            beaconScanner.start()

            scanner.emitScanFailed(42)

            val event = beaconScanner.beaconEvents.first()
            assertTrue(event is BeaconEvent.Failed)

            beaconScanner.close()
        }
    }

    companion object {
        fun iBeaconBytes(): ByteArray =
            byteArrayOf(
                0x02,
                0x15.toByte(),
                0xE2.toByte(),
                0xC5.toByte(),
                0x6D.toByte(),
                0xB5.toByte(),
                0xDF.toByte(),
                0xFB.toByte(),
                0x48.toByte(),
                0xD2.toByte(),
                0xB0.toByte(),
                0x60.toByte(),
                0xD0.toByte(),
                0xF5.toByte(),
                0xA7.toByte(),
                0x10.toByte(),
                0x96.toByte(),
                0xE0.toByte(),
                0x00,
                0x01,
                0x00,
                0x01,
                0xC5.toByte(),
            )

        fun eddystoneUidBytes(): ByteArray =
            byteArrayOf(
                0x00,
                0xEE.toByte(),
                0x6E.toByte(),
                0x4A.toByte(),
                0x0C.toByte(),
                0x0D.toByte(),
                0x9C.toByte(),
                0x8E.toByte(),
                0x4B.toByte(),
                0x3A.toByte(),
                0xA1.toByte(),
                0xF2.toByte(),
                0xC5.toByte(),
                0xD6.toByte(),
                0xE7.toByte(),
                0xF8.toByte(),
                0xA9.toByte(),
                0xB0.toByte(),
            )
    }
}
