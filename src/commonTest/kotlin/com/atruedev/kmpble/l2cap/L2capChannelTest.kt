@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.atruedev.kmpble.l2cap

import com.atruedev.kmpble.testing.FakeL2capChannel
import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class L2capChannelTest {

    @Test
    fun channelIsOpenAfterCreation() {
        val channel = FakeL2capChannel(psm = 0x25)
        assertTrue(channel.isOpen)
    }

    @Test
    fun channelPsmIsCorrect() {
        val channel = FakeL2capChannel(psm = 0x25)
        assertEquals(0x25, channel.psm)
    }

    @Test
    fun channelMtuDefaultsTo2048() {
        val channel = FakeL2capChannel(psm = 0x25)
        assertEquals(2048, channel.mtu)
    }

    @Test
    fun channelMtuIsConfigurable() {
        val channel = FakeL2capChannel(psm = 0x25, mtu = 4096)
        assertEquals(4096, channel.mtu)
    }

    @Test
    fun writeRecordsData() = runTest {
        val channel = FakeL2capChannel(psm = 0x25)
        val data = byteArrayOf(0x01, 0x02, 0x03)

        channel.write(data)

        val written = channel.getWrittenData()
        assertEquals(1, written.size)
        assertContentEquals(data, written[0])
    }

    @Test
    fun writeRecordsMultipleWrites() = runTest {
        val channel = FakeL2capChannel(psm = 0x25)

        channel.write(byteArrayOf(0x01))
        channel.write(byteArrayOf(0x02))
        channel.write(byteArrayOf(0x03))

        assertEquals(3, channel.getWrittenData().size)
    }

    @Test
    fun writeThrowsChannelClosedAfterClose() = runTest {
        val channel = FakeL2capChannel(psm = 0x25)
        channel.close()

        assertFailsWith<L2capException.ChannelClosed> {
            channel.write(byteArrayOf(0x01, 0x02))
        }
    }

    @Test
    fun isOpenReturnsFalseAfterClose() {
        val channel = FakeL2capChannel(psm = 0x25)
        assertTrue(channel.isOpen)

        channel.close()
        assertFalse(channel.isOpen)
    }

    @Test
    fun closeIsIdempotent() {
        val channel = FakeL2capChannel(psm = 0x25)

        channel.close()
        channel.close()
        channel.close()

        assertFalse(channel.isOpen)
    }

    @Test
    fun incomingFlowReceivesData() = runTest {
        val channel = FakeL2capChannel(psm = 0x25)
        val received = mutableListOf<ByteArray>()

        val collectJob = launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            channel.incoming.collect { received.add(it) }
        }

        channel.emitIncoming(byteArrayOf(0x01, 0x02))
        channel.emitIncoming(byteArrayOf(0x03, 0x04))

        assertEquals(2, received.size)
        assertContentEquals(byteArrayOf(0x01, 0x02), received[0])
        assertContentEquals(byteArrayOf(0x03, 0x04), received[1])

        collectJob.cancel()
    }

    @Test
    fun clearWrittenDataWorks() = runTest {
        val channel = FakeL2capChannel(psm = 0x25)

        channel.write(byteArrayOf(0x01))
        assertEquals(1, channel.getWrittenData().size)

        channel.clearWrittenData()
        assertEquals(0, channel.getWrittenData().size)
    }

    @Test
    fun writeCopiesInputData() = runTest {
        val channel = FakeL2capChannel(psm = 0x25)
        val data = byteArrayOf(0x01, 0x02)

        channel.write(data)
        data[0] = 0xFF.toByte()

        assertContentEquals(byteArrayOf(0x01, 0x02), channel.getWrittenData()[0])
    }

    // --- FakePeripheral L2CAP integration ---

    @Test
    fun openL2capChannelThrowsNotConnectedWhenDisconnected() = runTest {
        val peripheral = FakePeripheral {
            onOpenL2capChannel { psm -> FakeL2capChannel(psm) }
        }

        assertFailsWith<L2capException.NotConnected> {
            peripheral.openL2capChannel(psm = 0x25)
        }
    }

    @Test
    fun openL2capChannelSucceedsWhenConnected() = runTest {
        val peripheral = FakePeripheral {
            onOpenL2capChannel { psm -> FakeL2capChannel(psm) }
        }

        peripheral.connect()
        val channel = peripheral.openL2capChannel(psm = 0x25)

        assertTrue(channel.isOpen)
        assertEquals(0x25, channel.psm)
    }

    @Test
    fun openL2capChannelThrowsNotSupportedWithoutHandler() = runTest {
        val peripheral = FakePeripheral {}

        peripheral.connect()
        assertFailsWith<L2capException.NotSupported> {
            peripheral.openL2capChannel(psm = 0x25)
        }
    }

    @Test
    fun openL2capChannelPassesPsmToHandler() = runTest {
        var receivedPsm = -1
        val peripheral = FakePeripheral {
            onOpenL2capChannel { psm ->
                receivedPsm = psm
                FakeL2capChannel(psm)
            }
        }

        peripheral.connect()
        peripheral.openL2capChannel(psm = 0x42)

        assertEquals(0x42, receivedPsm)
    }

    // --- L2capException hierarchy ---

    @Test
    fun openFailedContainsPsmInMessage() {
        val ex = L2capException.OpenFailed(psm = 0x25, message = "timeout")
        assertTrue(ex.message!!.contains("PSM 37"))
        assertEquals(0x25, ex.psm)
    }

    @Test
    fun writeFailedContainsMessage() {
        val ex = L2capException.WriteFailed("stream error")
        assertTrue(ex.message!!.contains("stream error"))
    }

    @Test
    fun channelClosedHasDefaultMessage() {
        val ex = L2capException.ChannelClosed()
        assertTrue(ex.message!!.contains("closed"))
    }

    @Test
    fun notConnectedHasDefaultMessage() {
        val ex = L2capException.NotConnected()
        assertTrue(ex.message!!.contains("not connected"))
    }

    @Test
    fun notSupportedHasDefaultMessage() {
        val ex = L2capException.NotSupported()
        assertTrue(ex.message!!.contains("not supported"))
    }

    @Test
    fun allExceptionsAreSealedSubtypes() {
        val exceptions: List<L2capException> = listOf(
            L2capException.OpenFailed(0, "test"),
            L2capException.WriteFailed("test"),
            L2capException.ChannelClosed(),
            L2capException.NotConnected(),
            L2capException.NotSupported(),
        )

        exceptions.forEach { ex ->
            assertIs<L2capException>(ex)
            assertIs<Exception>(ex)
        }
    }
}
