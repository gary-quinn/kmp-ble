package com.atruedev.kmpble.conformance

import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.l2cap.L2capException
import com.atruedev.kmpble.testing.FakeL2capChannel
import com.atruedev.kmpble.testing.FakeL2capListener
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * L2CAP channel and listener conformance tests.
 *
 * Verifies L2CAP client channel open/write/incoming/close flows and
 * server-side listener lifecycle across KMP platforms.
 */
public abstract class L2capConformanceTest : BleConformanceTest() {
    @Test
    fun `l2cap channel open returns channel with correct psm`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    onOpenL2capChannel { psm, _ -> FakeL2capChannel(psm) }
                }

            peripheral.connect(ConnectionOptions())
            val channel = peripheral.openL2capChannel(psm = 0x25)

            assertEquals(0x25, channel.psm)
            assertTrue(channel.isOpen)
            channel.close()
            peripheral.close()
        }

    @Test
    fun `l2cap channel write records data`() =
        runTest {
            val channel = FakeL2capChannel(psm = 0x25)
            val peripheral =
                buildPeripheral {
                    onOpenL2capChannel { _, _ -> channel }
                }

            peripheral.connect(ConnectionOptions())
            val opened = peripheral.openL2capChannel(psm = 0x25)
            val data = byteArrayOf(0x01, 0x02, 0x03)

            opened.write(data)

            val written = (opened as FakeL2capChannel).getWrittenData()
            assertEquals(1, written.size)
            assertContentEquals(data, written[0])
            opened.close()
            peripheral.close()
        }

    @Test
    fun `l2cap channel incoming flow receives emitted data`() =
        runTest {
            val channel = FakeL2capChannel(psm = 0x25)
            val peripheral =
                buildPeripheral {
                    onOpenL2capChannel { _, _ -> channel }
                }

            peripheral.connect(ConnectionOptions())
            val opened = peripheral.openL2capChannel(psm = 0x25)
            val incomingData = mutableListOf<ByteArray>()
            val job = backgroundScope.launch { opened.incoming.collect { incomingData.add(it) } }
            testScheduler.runCurrent()

            val data = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
            channel.emitIncoming(data)
            testScheduler.runCurrent()

            assertEquals(1, incomingData.size, "Should have received emitted data")
            assertContentEquals(data, incomingData[0])
            job.cancel()
            opened.close()
            peripheral.close()
        }

    @Test
    fun `l2cap channel close stops writes`() =
        runTest {
            val channel = FakeL2capChannel(psm = 0x25)
            val peripheral =
                buildPeripheral {
                    onOpenL2capChannel { _, _ -> channel }
                }

            peripheral.connect(ConnectionOptions())
            val opened = peripheral.openL2capChannel(psm = 0x25)
            opened.close()
            assertTrue(!opened.isOpen)

            assertFailsWith<L2capException> {
                opened.write(byteArrayOf(0x01))
            }
            peripheral.close()
        }

    @Test
    fun `l2cap open fails when not connected`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    onOpenL2capChannel { psm, _ -> FakeL2capChannel(psm) }
                }

            assertFailsWith<L2capException> {
                peripheral.openL2capChannel(psm = 0x25)
            }
            peripheral.close()
        }

    @Test
    fun `l2cap open fails with mtu zero`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    onOpenL2capChannel { psm, _ -> FakeL2capChannel(psm) }
                }

            peripheral.connect(ConnectionOptions())

            assertFailsWith<IllegalArgumentException> {
                peripheral.openL2capChannel(psm = 0x25, mtu = 0)
            }
            peripheral.close()
        }

    @Test
    fun `l2cap listener open assigns psm`() =
        runTest {
            val listener = FakeL2capListener(assignedPsm = 0x80)

            listener.open()
            assertEquals(0x80, listener.psm)
            assertTrue(listener.isOpen.value)
            listener.close()
        }

    @Test
    fun `l2cap listener emits accepted channels`() =
        runTest {
            val listener = FakeL2capListener(assignedPsm = 0x81)

            listener.open()
            val accepted = mutableListOf<L2capChannel>()
            val job = backgroundScope.launch { listener.incoming.collect { accepted.add(it) } }
            testScheduler.runCurrent()

            val channel = FakeL2capChannel(psm = 0x81)
            listener.simulateIncoming(channel)
            testScheduler.runCurrent()

            assertEquals(1, accepted.size, "Should have received accepted channel")
            assertEquals(channel, accepted[0])
            job.cancel()
            channel.close()
            listener.close()
        }

    @Test
    fun `l2cap listener close stops acceptance`() =
        runTest {
            val listener = FakeL2capListener(assignedPsm = 0x82)

            listener.open()
            listener.close()
            assertTrue(!listener.isOpen.value)

            assertFailsWith<IllegalStateException> {
                listener.simulateIncoming(FakeL2capChannel(psm = 0x82))
            }
        }
}
