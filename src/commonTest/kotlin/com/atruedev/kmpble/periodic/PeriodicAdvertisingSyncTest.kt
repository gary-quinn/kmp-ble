package com.atruedev.kmpble.periodic

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.testing.FakePeriodicAdvertisingSync
import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class PeriodicAdvertisingSyncTest {
    @Test
    fun `factory throws NotSupported on JVM`() =
        runTest {
            assertFailsWith<PastException.NotSupported> {
                PeriodicAdvertisingSync(Identifier("test"), 0, 5.seconds)
            }
        }

    @Test
    fun `fake sync reports active by default`() {
        val sync = FakePeriodicAdvertisingSync()
        assertTrue(sync.isActive)
        assertEquals(Identifier("fake-advertiser"), sync.advertiserAddress)
        assertEquals(0, sync.advertisingSid)
    }

    @Test
    fun `fake sync receives reports`() =
        runTest {
            val sync = FakePeriodicAdvertisingSync()
            val report =
                PeriodicReport(
                    rssi = -45,
                    txPower = 0,
                    advertisingSid = 0,
                    data = byteArrayOf(0x01, 0x02, 0x03),
                    phy = Phy.Le1M,
                    dataStatus = PeriodicReport.DataStatus.Complete,
                )

            val collected = mutableListOf<PeriodicReport>()
            val job =
                launch {
                    sync.reports.toList(collected)
                }

            sync.emitReport(report)
            yield()
            sync.close()
            job.join()

            assertEquals(1, collected.size)
            assertEquals(-45, collected[0].rssi)
            assertTrue(collected[0].data.contentEquals(byteArrayOf(0x01, 0x02, 0x03)))
        }

    @Test
    fun `fake sync closes cleanly`() {
        val sync = FakePeriodicAdvertisingSync()
        sync.close()
        assertFalse(sync.isActive)
    }

    @Test
    fun `transfer to connected peripheral records transfer`() =
        runTest {
            val sync = FakePeriodicAdvertisingSync()
            val peripheral = FakePeripheral {}

            sync.transferTo(peripheral)
            assertEquals(1, sync.transferredTo.size)
            assertEquals(peripheral, sync.transferredTo[0])
        }

    @Test
    fun `transfer to closed sync throws SyncInactive`() =
        runTest {
            val sync = FakePeriodicAdvertisingSync()
            val peripheral = FakePeripheral {}
            sync.close()

            assertFailsWith<PastException.SyncInactive> {
                sync.transferTo(peripheral)
            }
        }

    @Test
    fun `simulated sync loss closes reports with exception`() =
        runTest {
            val sync = FakePeriodicAdvertisingSync()

            val job =
                launch {
                    try {
                        sync.reports.collect { }
                    } catch (e: PastException.SyncFailed) {
                        // Expected
                    }
                }

            sync.simulateLost()
            job.join()
            assertFalse(sync.isActive)
        }

    @Test
    fun `receivePastSync on disconnected fake peripheral throws NotConnected`() =
        runTest {
            val peripheral = FakePeripheral {}

            assertFailsWith<PastException.NotConnected> {
                peripheral.receivePastSync()
            }
        }

    @Test
    fun `receivePastSync on connected fake peripheral without handler throws NotSupported`() =
        runTest {
            val peripheral = FakePeripheral {}
            peripheral.connect()

            assertFailsWith<PastException.NotSupported> {
                peripheral.receivePastSync()
            }
            peripheral.close()
        }

    @Test
    fun `receivePastSync on connected fake peripheral with handler returns sync`() =
        runTest {
            val expectedSync = FakePeriodicAdvertisingSync()
            val peripheral =
                FakePeripheral {
                    onReceivePastSync { expectedSync }
                }
            peripheral.connect()

            val result = peripheral.receivePastSync()
            assertNotNull(result)
            assertTrue(result.isActive)
            peripheral.close()
        }

    @Test
    fun `PeriodicReport equality works correctly`() {
        val data = byteArrayOf(0x01, 0x02)
        val report1 =
            PeriodicReport(
                rssi = -50,
                txPower = null,
                advertisingSid = 1,
                data = data,
                phy = Phy.Le1M,
                dataStatus = PeriodicReport.DataStatus.Complete,
            )
        val report2 =
            PeriodicReport(
                rssi = -50,
                txPower = null,
                advertisingSid = 1,
                data = byteArrayOf(0x01, 0x02),
                phy = Phy.Le1M,
                dataStatus = PeriodicReport.DataStatus.Complete,
            )
        val report3 =
            PeriodicReport(
                rssi = -50,
                txPower = null,
                advertisingSid = 1,
                data = byteArrayOf(0x01, 0x03), // Different data
                phy = Phy.Le1M,
                dataStatus = PeriodicReport.DataStatus.Complete,
            )

        assertEquals(report1, report2)
        assertFalse(report1.equals(report3))
        assertEquals(report1.hashCode(), report2.hashCode())
    }

    @Test
    fun `chronous exception hierarchy`() {
        val notSupported = PastException.NotSupported()
        assertTrue(notSupported is PastException)
        assertTrue(notSupported is Exception)

        val syncFailed = PastException.SyncFailed("test", RuntimeException("cause"))
        assertEquals("Periodic advertising sync lost: test", syncFailed.message)

        val transferFailed = PastException.TransferFailed("timeout")
        assertEquals("PAST transfer failed: timeout", transferFailed.message)

        val notConnected = PastException.NotConnected()
        assertEquals(
            "Peripheral is not connected -- PAST requires an active connection",
            notConnected.message,
        )

        val syncInactive = PastException.SyncInactive()
        assertEquals(
            "Periodic advertising sync is no longer active",
            syncInactive.message,
        )
    }

    @Test
    fun `PeriodicReport toString contains key fields`() {
        val report =
            PeriodicReport(
                rssi = -42,
                txPower = 4,
                advertisingSid = 7,
                data = byteArrayOf(0xAA.toByte(), 0xBB.toByte()),
                phy = Phy.Le2M,
                dataStatus = PeriodicReport.DataStatus.Truncated,
            )
        val str = report.toString()
        assertTrue(str.contains("rssi=-42"))
        assertTrue(str.contains("sid=7"))
        assertTrue(str.contains("2B"))
        assertTrue(str.contains("Truncated"))
    }
}
