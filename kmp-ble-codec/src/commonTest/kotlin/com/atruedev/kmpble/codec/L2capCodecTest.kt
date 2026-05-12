@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.atruedev.kmpble.codec

import com.atruedev.kmpble.testing.FakeL2capChannel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class L2capCodecTest {

    @Test
    fun incomingDecodesPackets() = runTest {
        val channel = FakeL2capChannel(psm = 0x25)
        val received = mutableListOf<String>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            channel.incoming(TestStringDecoder).collect { received.add(it) }
        }

        channel.emitIncoming("alpha".encodeToByteArray())
        channel.emitIncoming("beta".encodeToByteArray())

        assertEquals(listOf("alpha", "beta"), received)
        job.cancel()
    }

    @Test
    fun writeEncodesBeforeSending() = runTest {
        val channel = FakeL2capChannel(psm = 0x25)

        channel.write("hello", TestStringEncoder)

        val written = channel.getWrittenData()
        assertEquals(1, written.size)
        assertContentEquals("hello".encodeToByteArray(), written[0])
    }

    @Test
    fun writeMultipleEncodedValues() = runTest {
        val channel = FakeL2capChannel(psm = 0x25)

        channel.write("one", TestStringEncoder)
        channel.write("two", TestStringEncoder)
        channel.write("three", TestStringEncoder)

        val written = channel.getWrittenData()
        assertEquals(3, written.size)
        assertContentEquals("one".encodeToByteArray(), written[0])
        assertContentEquals("two".encodeToByteArray(), written[1])
        assertContentEquals("three".encodeToByteArray(), written[2])
    }

    @Test
    fun incomingDecoderFailurePropagates() = runTest {
        val channel = FakeL2capChannel(psm = 0x25)
        var caughtException: Throwable? = null

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            try {
                channel.incoming(FailingDecoder).collect {}
            } catch (e: IllegalArgumentException) {
                caughtException = e
            }
        }

        channel.emitIncoming(byteArrayOf(0x01))

        assertIs<IllegalArgumentException>(caughtException)
        job.cancel()
    }

    @Test
    fun writeFramedPrefixesLengthHeader() = runTest {
        val channel = FakeL2capChannel(psm = 0x25)

        channel.writeFramed("hi", TestStringEncoder)

        val written = channel.getWrittenData()
        assertEquals(1, written.size)
        val expected = byteArrayOf(0x02, 0x00, 0x00, 0x00) + "hi".encodeToByteArray()
        assertContentEquals(expected, written[0])
    }

    @Test
    fun framedIncomingDecodesMultiFrameStream() = runTest {
        val channel = FakeL2capChannel(psm = 0x25)
        val framer = LengthPrefixFramer()
        val received = mutableListOf<String>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            channel.framedIncoming(TestStringDecoder, framer).collect { received.add(it) }
        }

        channel.emitIncoming(framer.frame("alpha".encodeToByteArray()))
        channel.emitIncoming(framer.frame("beta".encodeToByteArray()))
        channel.emitIncoming(framer.frame("gamma".encodeToByteArray()))

        assertEquals(listOf("alpha", "beta", "gamma"), received)
        job.cancel()
    }

    @Test
    fun framedIncomingRoutesDecodeFailureToCallback() = runTest {
        val channel = FakeL2capChannel(psm = 0x25)
        val framer = LengthPrefixFramer()
        val failures = mutableListOf<ByteArray>()
        val received = mutableListOf<Int>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            channel
                .framedIncoming(TestIntDecoder, framer, onDecodeFailure = { failures.add(it) })
                .collect { received.add(it) }
        }

        channel.emitIncoming(framer.frame(TestIntEncoder.encode(0x1234)))
        channel.emitIncoming(framer.frame(byteArrayOf(0x01)))
        channel.emitIncoming(framer.frame(TestIntEncoder.encode(0x5678)))

        assertEquals(listOf(0x1234, 0x5678), received)
        assertEquals(1, failures.size)
        assertContentEquals(byteArrayOf(0x01), failures[0])
        job.cancel()
    }

    @Test
    fun framedIncomingPropagatesFrameTooLargeException() = runTest {
        val channel = FakeL2capChannel(psm = 0x25)
        val framer = LengthPrefixFramer(maxFrameSize = 2)
        var caught: Throwable? = null

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            channel
                .framedIncoming(TestStringDecoder, framer)
                .catch { caught = it }
                .collect {}
        }

        channel.emitIncoming(
            byteArrayOf(0x03, 0x00, 0x00, 0x00, 0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte()),
        )

        assertIs<FrameTooLargeException>(caught)
        assertEquals(3L, (caught as FrameTooLargeException).size)
        assertEquals(2, (caught as FrameTooLargeException).maxSize)
        job.cancel()
    }

    @Test
    fun writeFramedRoundTripsThroughFramedIncoming() = runTest {
        val sender = FakeL2capChannel(psm = 0x25)
        val receiver = FakeL2capChannel(psm = 0x25)
        val framer = LengthPrefixFramer()
        val values = listOf("one", "two", "three")

        values.forEach { sender.writeFramed(it, TestStringEncoder, framer) }
        sender.getWrittenData().forEach { receiver.emitIncoming(it) }
        receiver.close()

        val decoded = receiver.framedIncoming(TestStringDecoder, framer).toList()

        assertEquals(values, decoded)
    }

    @Test
    fun framedIncomingHandlesPacketBoundariesDifferentFromFrameBoundaries() = runTest {
        val channel = FakeL2capChannel(psm = 0x25)
        val framer = LengthPrefixFramer()
        val full = framer.frame("payload".encodeToByteArray())

        val firstHalf = full.copyOfRange(0, 5)
        val secondHalf = full.copyOfRange(5, full.size)

        channel.emitIncoming(firstHalf)
        channel.emitIncoming(secondHalf)
        channel.close()

        val received = channel.framedIncoming(TestStringDecoder, framer).take(1).toList()
        assertEquals(1, received.size)
        assertEquals("payload", received[0])
    }

    @Test
    fun writeFramedAndFramedIncomingDefaultToLengthPrefixFramer() = runTest {
        val sender = FakeL2capChannel(psm = 0x25)
        val receiver = FakeL2capChannel(psm = 0x25)

        sender.writeFramed(0x4242, TestIntEncoder)
        sender.getWrittenData().forEach { receiver.emitIncoming(it) }
        receiver.close()

        val decoded = receiver.framedIncoming(TestIntDecoder).toList()
        assertEquals(1, decoded.size)
        assertEquals(0x4242, decoded[0])
        assertTrue(sender.getWrittenData()[0].size > 2, "framed payload includes length header")
    }
}
