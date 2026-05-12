package com.atruedev.kmpble.codec

import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlowFramingTest {

    @Test
    fun unframedByEmitsFramesFromChunkedStream() = runTest {
        val framer = LengthPrefixFramer()
        val frame1 = framer.frame(byteArrayOf(0x01, 0x02))
        val frame2 = framer.frame(byteArrayOf(0x03))
        val chunks = listOf(
            frame1.copyOfRange(0, 5),
            frame1.copyOfRange(5, frame1.size) + frame2,
        )
        val frames = flowOf(*chunks.toTypedArray()).unframedBy(framer).toList()
        assertEquals(2, frames.size)
        assertContentEquals(byteArrayOf(0x01, 0x02), frames[0])
        assertContentEquals(byteArrayOf(0x03), frames[1])
    }

    @Test
    fun decodeFramedReturnsDecodedValues() = runTest {
        val framer = LengthPrefixFramer()
        val stream = framer.frame(Uint16Codec.encode(42)) +
            framer.frame(Uint16Codec.encode(0xCAFE))
        val values = flowOf(stream).decodeFramed(Uint16Codec, framer).toList()
        assertEquals(listOf(42, 0xCAFE), values)
    }

    @Test
    fun decodeFramedRoutesDecoderThrowsToCallback() = runTest {
        val framer = LengthPrefixFramer()
        val good = framer.frame(Uint16Codec.encode(7))
        val malformed = framer.frame(byteArrayOf(0x01))
        val stream = malformed + good
        val failures = mutableListOf<ByteArray>()
        val values = flowOf(stream)
            .decodeFramed(Uint16Codec, framer, onDecodeFailure = { failures.add(it) })
            .toList()
        assertEquals(listOf(7), values)
        assertEquals(1, failures.size)
        assertContentEquals(byteArrayOf(0x01), failures[0])
    }

    @Test
    fun decodeFramedDefaultDropsFailuresSilently() = runTest {
        val framer = LengthPrefixFramer()
        val malformed = framer.frame(byteArrayOf(0x01))
        val good = framer.frame(Uint16Codec.encode(42))
        val values = flowOf(malformed + good).decodeFramed(Uint16Codec, framer).toList()
        assertEquals(listOf(42), values)
    }

    @Test
    fun decodeFramedHandlesIncrementalChunks() = runTest {
        val framer = LengthPrefixFramer()
        val framed = framer.frame(Uint16Codec.encode(0x1234))
        val chunks = Array(framed.size) { byteArrayOf(framed[it]) }
        val values = flowOf(*chunks).decodeFramed(Uint16Codec, framer).toList()
        assertEquals(listOf(0x1234), values)
    }

    @Test
    fun unframedByDropsTrailingPartialFrame() = runTest {
        val framer = LengthPrefixFramer()
        val partial = byteArrayOf(0x05, 0x00, 0x00, 0x00, 0x01, 0x02)
        val frames = flowOf(partial).unframedBy(framer).toList()
        assertEquals(emptyList(), frames)
    }

    @Test
    fun frameTooLargeExceptionPropagatesThroughFlow() = runTest {
        val framer = LengthPrefixFramer(maxFrameSize = 2)
        val bad = byteArrayOf(0x03, 0x00, 0x00, 0x00, 0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
        var caught: Throwable? = null
        flowOf(bad)
            .unframedBy(framer)
            .catch { caught = it }
            .toList()
        assertTrue(caught is FrameTooLargeException)
        assertEquals(3L, (caught as FrameTooLargeException).size)
    }

    @Test
    fun flowCancellationMidStreamLeavesNoRemnantState() = runTest {
        val framer = LengthPrefixFramer()
        val source = flow {
            emit(framer.frame(byteArrayOf(0x01)))
            emit(framer.frame(byteArrayOf(0x02)))
            emit(framer.frame(byteArrayOf(0x03)))
        }
        val received = source.unframedBy(framer).take(1).toList()
        assertEquals(1, received.size)
        assertContentEquals(byteArrayOf(0x01), received[0])
    }

    @Test
    fun framerInstanceIsReusableAcrossCollections() = runTest {
        val framer = LengthPrefixFramer()
        val stream = framer.frame(byteArrayOf(0x77))
        val first = flowOf(stream).unframedBy(framer).toList()
        val second = flowOf(stream).unframedBy(framer).toList()
        assertContentEquals(byteArrayOf(0x77), first.single())
        assertContentEquals(byteArrayOf(0x77), second.single())
    }
}
