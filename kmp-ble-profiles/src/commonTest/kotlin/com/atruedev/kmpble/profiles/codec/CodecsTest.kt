package com.atruedev.kmpble.profiles.codec

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

class CodecsTest {

    @Test
    fun rawBytesRoundTrips() {
        val original = byteArrayOf(0x01, 0x02, 0x03)
        val encoded = RawBytesCodec.encode(original)
        val decoded = RawBytesCodec.decode(encoded)
        assertContentEquals(original, decoded)
    }

    @Test
    fun rawBytesEncodeIsDefensiveCopy() {
        val original = byteArrayOf(0x01, 0x02)
        val encoded = RawBytesCodec.encode(original)
        original[0] = 0xFF.toByte()
        assertContentEquals(byteArrayOf(0x01, 0x02), encoded)
    }

    @Test
    fun rawBytesDecodeIsDefensiveCopy() {
        val source = byteArrayOf(0x01, 0x02)
        val decoded = RawBytesCodec.decode(source)
        source[0] = 0xFF.toByte()
        assertContentEquals(byteArrayOf(0x01, 0x02), decoded)
    }

    @Test
    fun utf8RoundTrips() {
        val encoded = Utf8StringCodec.encode("Hello, 世界")
        assertEquals("Hello, 世界", Utf8StringCodec.decode(encoded))
    }

    @Test
    fun utf8DecodeReturnsNullOnInvalidSequence() {
        val invalid = byteArrayOf(0xC3.toByte(), 0x28)
        assertNull(Utf8StringCodec.decode(invalid))
    }

    @Test
    fun uint8RoundTrips() {
        for (v in 0..255) {
            val encoded = Uint8Codec.encode(v)
            assertEquals(1, encoded.size)
            assertEquals(v, Uint8Codec.decode(encoded))
        }
    }

    @Test
    fun uint8DecodeRejectsWrongSize() {
        assertNull(Uint8Codec.decode(byteArrayOf()))
        assertNull(Uint8Codec.decode(byteArrayOf(0x01, 0x02)))
    }

    @Test
    fun uint8EncodeRejectsOutOfRange() {
        assertFails { Uint8Codec.encode(-1) }
        assertFails { Uint8Codec.encode(256) }
    }

    @Test
    fun int8RoundTripsAcrossFullRange() {
        for (v in -128..127) {
            val encoded = Int8Codec.encode(v)
            assertEquals(1, encoded.size)
            assertEquals(v, Int8Codec.decode(encoded))
        }
    }

    @Test
    fun int8DecodeRejectsWrongSize() {
        assertNull(Int8Codec.decode(byteArrayOf()))
        assertNull(Int8Codec.decode(byteArrayOf(0x01, 0x02)))
    }

    @Test
    fun int8EncodeRejectsOutOfRange() {
        assertFails { Int8Codec.encode(-129) }
        assertFails { Int8Codec.encode(128) }
    }

    @Test
    fun uint16RoundTrips() {
        for (v in listOf(0, 1, 255, 256, 384, 0xFFFF)) {
            val encoded = Uint16Codec.encode(v)
            assertEquals(2, encoded.size)
            assertEquals(v, Uint16Codec.decode(encoded))
        }
    }

    @Test
    fun uint16DecodeRejectsWrongSize() {
        assertNull(Uint16Codec.decode(byteArrayOf()))
        assertNull(Uint16Codec.decode(byteArrayOf(0x01)))
        assertNull(Uint16Codec.decode(byteArrayOf(0x01, 0x02, 0x03)))
    }

    @Test
    fun uint16EncodeRejectsOutOfRange() {
        assertFails { Uint16Codec.encode(-1) }
        assertFails { Uint16Codec.encode(0x10000) }
    }

    @Test
    fun int16RoundTrips() {
        for (v in listOf(Short.MIN_VALUE.toInt(), -1, 0, 1, Short.MAX_VALUE.toInt())) {
            val encoded = Int16Codec.encode(v)
            assertEquals(2, encoded.size)
            assertEquals(v, Int16Codec.decode(encoded))
        }
    }

    @Test
    fun int16DecodeNegative() {
        assertEquals(-1, Int16Codec.decode(byteArrayOf(0xFF.toByte(), 0xFF.toByte())))
    }

    @Test
    fun int16DecodeRejectsWrongSize() {
        assertNull(Int16Codec.decode(byteArrayOf(0x01)))
        assertNull(Int16Codec.decode(byteArrayOf(0x01, 0x02, 0x03)))
    }

    @Test
    fun int16EncodeRejectsOutOfRange() {
        assertFails { Int16Codec.encode(-32769) }
        assertFails { Int16Codec.encode(32768) }
    }

    @Test
    fun uint32RoundTrips() {
        for (v in listOf(0L, 1L, 0xFFFFL, 0x12345678L, 0xFFFFFFFFL)) {
            val encoded = Uint32Codec.encode(v)
            assertEquals(4, encoded.size)
            assertEquals(v, Uint32Codec.decode(encoded))
        }
    }

    @Test
    fun uint32DecodeRejectsWrongSize() {
        assertNull(Uint32Codec.decode(byteArrayOf(0x01, 0x02, 0x03)))
        assertNull(Uint32Codec.decode(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)))
    }

    @Test
    fun uint32EncodeRejectsOutOfRange() {
        assertFails { Uint32Codec.encode(-1L) }
        assertFails { Uint32Codec.encode(0x1_0000_0000L) }
    }

    @Test
    fun int32RoundTrips() {
        for (v in listOf(Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE)) {
            val encoded = Int32Codec.encode(v)
            assertEquals(4, encoded.size)
            assertEquals(v, Int32Codec.decode(encoded))
        }
    }

    @Test
    fun int32IsLittleEndian() {
        assertContentEquals(byteArrayOf(0x78, 0x56, 0x34, 0x12), Int32Codec.encode(0x12345678))
    }

    @Test
    fun int32DecodeRejectsWrongSize() {
        assertNull(Int32Codec.decode(byteArrayOf(0x01, 0x02, 0x03)))
    }
}
