package com.atruedev.kmpble.isochronous

import app.cash.turbine.test
import com.atruedev.kmpble.testing.FakeIsochronousChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IsochronousStreamTest {

    private val testMtu = 256

    private fun createStream(channel: FakeIsochronousChannel = FakeIsochronousChannel(testMtu)): IsochronousStream =
        IsochronousStream.open(channel)

    // --- Construction ---

    @Test
    fun openStreamOnOpenChannel() {
        val channel = FakeIsochronousChannel(testMtu)
        val stream = IsochronousStream.open(channel)
        assertEquals(IsochronousStream.State.Idle, stream.state.value)
    }

    @Test
    fun openStreamOnClosedChannelThrowsNotConnected() {
        val channel = FakeIsochronousChannel(testMtu)
        channel.close()
        assertFailsWith<IsochronousException.NotConnected> {
            IsochronousStream.open(channel)
        }
    }

    // --- State transitions ---

    @Test
    fun stateTransitionsToStreamingOnCollect() =
        runTest {
            val channel = FakeIsochronousChannel(testMtu)
            val stream = createStream(channel)

            stream.incoming.test {
                assertEquals(IsochronousStream.State.Streaming, stream.state.value)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun stateTransitionsToClosedOnNormalCompletion() =
        runTest {
            val channel = FakeIsochronousChannel(testMtu)
            val stream = createStream(channel)

            stream.incoming.test {
                assertEquals(IsochronousStream.State.Streaming, stream.state.value)
                channel.close()
                // Flow completes, state transitions to Closed
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun closeTransitionsToClosed() {
        val stream = createStream()
        stream.close()
        assertEquals(IsochronousStream.State.Closed, stream.state.value)
    }

    @Test
    fun isStreamingReflectsState() {
        val stream = createStream()
        assertFalse(stream.isStreaming)
        // isStreaming becomes true only after collection starts
    }

    // --- Frame encoding / decoding ---

    @Test
    fun encodeDecodeFrameRoundtrip() {
        val original =
            IsochronousStream.IsochronousFrame(
                data = byteArrayOf(0x01, 0x02, 0x03, 0x04),
                timestamp = 1_234_567_890L,
                sequenceNumber = 42L,
            )

        val encoded = IsochronousStream.encodeFrame(original)
        assertEquals(20, encoded.size) // 16 header + 4 payload

        val decoded = IsochronousStream.decodeFrame(encoded)
        assertNotNull(decoded)
        assertEquals(original, decoded)
    }

    @Test
    fun encodeFramePreservesHeaderFormat() {
        val frame =
            IsochronousStream.IsochronousFrame(
                data = byteArrayOf(0x42),
                timestamp = 0x0000FF00FF00FF00L,
                sequenceNumber = 0x123456789ABCDEF0L,
            )

        val encoded = IsochronousStream.encodeFrame(frame)

        // Verify big-endian timestamp at bytes 0-7
        assertEquals(0x00.toByte(), encoded[0])
        assertEquals(0x00.toByte(), encoded[1])
        assertEquals(0xFF.toByte(), encoded[2])
        assertEquals(0x00.toByte(), encoded[3])
        assertEquals(0xFF.toByte(), encoded[4])
        assertEquals(0x00.toByte(), encoded[5])
        assertEquals(0xFF.toByte(), encoded[6])
        assertEquals(0x00.toByte(), encoded[7])

        // Payload at byte 16
        assertEquals(0x42.toByte(), encoded[16])
    }

    @Test
    fun decodeNullForTooShortInput() {
        assertEquals(null, IsochronousStream.decodeFrame(byteArrayOf(0x01, 0x02)))
        assertEquals(null, IsochronousStream.decodeFrame(byteArrayOf()))
    }

    @Test
    fun encodeEmptyPayload() {
        val frame =
            IsochronousStream.IsochronousFrame(
                data = byteArrayOf(),
                timestamp = 100L,
                sequenceNumber = 1L,
            )
        val encoded = IsochronousStream.encodeFrame(frame)
        assertEquals(16, encoded.size) // Only header
        val decoded = IsochronousStream.decodeFrame(encoded)
        assertNotNull(decoded)
        assertEquals(frame, decoded)
    }

    // --- Send and receive ---

    @Test
    fun sendWritesFrameThroughChannel() =
        runTest {
            val channel = FakeIsochronousChannel(testMtu)
            val stream = createStream(channel)

            stream.send(byteArrayOf(0x01, 0x02, 0x03))

            val written = channel.getWrittenData()
            assertEquals(1, written.size)
            val frame = IsochronousStream.decodeFrame(written[0])
            assertNotNull(frame)
            assertEquals(3, frame.data.size)
            assertEquals(1L, frame.sequenceNumber)
        }

    @Test
    fun sendIncrementsSequenceNumber() =
        runTest {
            val channel = FakeIsochronousChannel(testMtu)
            val stream = createStream(channel)

            stream.send(byteArrayOf(0x01))
            stream.send(byteArrayOf(0x02))
            stream.send(byteArrayOf(0x03))

            val written = channel.getWrittenData()
            assertEquals(3, written.size)
            assertEquals(1L, IsochronousStream.decodeFrame(written[0])!!.sequenceNumber)
            assertEquals(2L, IsochronousStream.decodeFrame(written[1])!!.sequenceNumber)
            assertEquals(3L, IsochronousStream.decodeFrame(written[2])!!.sequenceNumber)
        }

    @Test
    fun sendThrowsOnClosedStream() =
        runTest {
            val stream = createStream()
            stream.close()
            assertFailsWith<IsochronousException.ChannelClosed> {
                stream.send(byteArrayOf(0x01))
            }
        }

    // --- MTU delegation ---

    @Test
    fun mtuDelegatesToChannel() {
        val channel = FakeIsochronousChannel(512)
        val stream = IsochronousStream.open(channel)
        assertEquals(512, stream.mtu)
    }

    // --- Close is idempotent ---

    @Test
    fun closeMultipleTimesIsSafe() {
        val stream = createStream()
        stream.close()
        stream.close()
        stream.close()
        assertEquals(IsochronousStream.State.Closed, stream.state.value)
    }

    // --- Frame equality ---

    @Test
    fun frameEqualityIsContentBased() {
        val a =
            IsochronousStream.IsochronousFrame(
                data = byteArrayOf(0x01, 0x02),
                timestamp = 100L,
                sequenceNumber = 1L,
            )
        val b =
            IsochronousStream.IsochronousFrame(
                data = byteArrayOf(0x01, 0x02),
                timestamp = 100L,
                sequenceNumber = 1L,
            )
        val c =
            IsochronousStream.IsochronousFrame(
                data = byteArrayOf(0x03),
                timestamp = 100L,
                sequenceNumber = 1L,
            )

        assertEquals(a, b)
        assertTrue(a != c)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun frameToStringIsHumanReadable() {
        val frame =
            IsochronousStream.IsochronousFrame(
                data = byteArrayOf(0x01, 0x02),
                timestamp = 100L,
                sequenceNumber = 5L,
            )
        val str = frame.toString()
        assertTrue(str.contains("seq=5"))
        assertTrue(str.contains("ts=100"))
        assertTrue(str.contains("len=2"))
    }

    // --- Invalid state operations ---

    @Test
    fun sendAfterCloseFails() =
        runTest {
            val stream = createStream()
            stream.close()
            assertFailsWith<IsochronousException.ChannelClosed> {
                stream.send(byteArrayOf(0x01))
            }
        }
}
