package com.atruedev.kmpble.profiles.codec

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

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
        val frames = flowOf(*chunks.toTypedArray()).unframedBy(framer.unframer()).toList()
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
    fun decodeFramedDropsFramesThatFailToDecode() = runTest {
        val framer = LengthPrefixFramer()
        val good = framer.frame(Uint16Codec.encode(7))
        val bad = framer.frame(byteArrayOf(0x01))
        val stream = bad + good
        val values = flowOf(stream).decodeFramed(Uint16Codec, framer).toList()
        assertEquals(listOf(7), values)
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
        val frames = flowOf(partial).unframedBy(framer.unframer()).toList()
        assertEquals(emptyList(), frames)
    }
}
