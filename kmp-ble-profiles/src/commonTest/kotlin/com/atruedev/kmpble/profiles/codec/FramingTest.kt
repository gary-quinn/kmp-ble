package com.atruedev.kmpble.profiles.codec

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FramingTest {

    @Test
    fun frameEmptyPayload() {
        val framed = LengthPrefixFramer().frame(byteArrayOf())
        // length=0 → 0x00 0x00 0x00 0x00, no payload
        assertContentEquals(byteArrayOf(0, 0, 0, 0), framed)
    }

    @Test
    fun frameLengthIsLittleEndianUint32() {
        val payload = ByteArray(0x180) { it.toByte() }
        val framed = LengthPrefixFramer().frame(payload)
        // length=0x180 → 0x80, 0x01, 0x00, 0x00
        assertEquals(0x80.toByte(), framed[0])
        assertEquals(0x01.toByte(), framed[1])
        assertEquals(0x00.toByte(), framed[2])
        assertEquals(0x00.toByte(), framed[3])
        assertEquals(payload.size + 4, framed.size)
    }

    @Test
    fun frameRejectsPayloadLargerThanCap() {
        val framer = LengthPrefixFramer(maxFrameSize = 4)
        framer.frame(ByteArray(4))   // boundary OK
        assertFailsWith<IllegalArgumentException> { framer.frame(ByteArray(5)) }
    }

    @Test
    fun unframerSingleCompleteFrameInOneFeed() {
        val framer = LengthPrefixFramer()
        val payload = byteArrayOf(0x01, 0x02, 0x03)
        val framed = framer.frame(payload)
        val frames = framer.unframer().feed(framed)
        assertEquals(1, frames.size)
        assertContentEquals(payload, frames[0])
    }

    @Test
    fun unframerMultipleFramesInOneFeed() {
        val framer = LengthPrefixFramer()
        val combined = framer.frame(byteArrayOf(0x0A)) + framer.frame(byteArrayOf(0x0B, 0x0C))
        val frames = framer.unframer().feed(combined)
        assertEquals(2, frames.size)
        assertContentEquals(byteArrayOf(0x0A), frames[0])
        assertContentEquals(byteArrayOf(0x0B, 0x0C), frames[1])
    }

    @Test
    fun unframerPartialHeaderBuffersUntilComplete() {
        val framer = LengthPrefixFramer()
        val framed = framer.frame(byteArrayOf(0x42))
        val unframer = framer.unframer()
        // Feed bytes one at a time
        for (i in 0 until framed.size - 1) {
            assertEquals(emptyList(), unframer.feed(byteArrayOf(framed[i])))
        }
        val frames = unframer.feed(byteArrayOf(framed.last()))
        assertEquals(1, frames.size)
        assertContentEquals(byteArrayOf(0x42), frames[0])
    }

    @Test
    fun unframerSplitAcrossFeeds() {
        val framer = LengthPrefixFramer()
        val framed = framer.frame(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05))
        val unframer = framer.unframer()
        // Split after byte 6 (header + 2 payload bytes)
        assertEquals(emptyList(), unframer.feed(framed.copyOfRange(0, 6)))
        val frames = unframer.feed(framed.copyOfRange(6, framed.size))
        assertEquals(1, frames.size)
        assertContentEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05), frames[0])
    }

    @Test
    fun unframerHandlesEmptyFeed() {
        val unframer = LengthPrefixFramer().unframer()
        assertEquals(emptyList(), unframer.feed(byteArrayOf()))
    }

    @Test
    fun unframerLeavesPartialTailForNextFeed() {
        val framer = LengthPrefixFramer()
        val first = framer.frame(byteArrayOf(0x01))
        val second = framer.frame(byteArrayOf(0x02, 0x03))
        val combined = first + second
        val unframer = framer.unframer()
        // Feed first frame + 3 bytes of second frame's header
        val frames1 = unframer.feed(combined.copyOfRange(0, first.size + 3))
        assertEquals(1, frames1.size)
        assertContentEquals(byteArrayOf(0x01), frames1[0])
        // Feed remaining bytes
        val frames2 = unframer.feed(combined.copyOfRange(first.size + 3, combined.size))
        assertEquals(1, frames2.size)
        assertContentEquals(byteArrayOf(0x02, 0x03), frames2[0])
    }

    @Test
    fun unframerThrowsOnFrameLargerThanCap() {
        val framer = LengthPrefixFramer(maxFrameSize = 2)
        // Manually craft a frame claiming length=3
        val bad = byteArrayOf(0x03, 0x00, 0x00, 0x00, 0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
        val ex = assertFailsWith<FrameTooLargeException> { framer.unframer().feed(bad) }
        assertEquals(3L, ex.size)
        assertEquals(2, ex.maxSize)
    }

    @Test
    fun unframerHandlesZeroLengthFrame() {
        val framer = LengthPrefixFramer()
        val framed = framer.frame(byteArrayOf())
        val frames = framer.unframer().feed(framed)
        assertEquals(1, frames.size)
        assertTrue(frames[0].isEmpty())
    }

    @Test
    fun roundTripPreservesPayload() {
        val framer = LengthPrefixFramer()
        val payloads = listOf(
            byteArrayOf(),
            byteArrayOf(0x42),
            ByteArray(100) { it.toByte() },
            ByteArray(1024) { (it * 7).toByte() },
        )
        val stream = payloads.fold(byteArrayOf()) { acc, p -> acc + framer.frame(p) }
        val frames = framer.unframer().feed(stream)
        assertEquals(payloads.size, frames.size)
        payloads.zip(frames).forEach { (expected, actual) ->
            assertContentEquals(expected, actual)
        }
    }
}
