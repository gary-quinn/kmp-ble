package com.atruedev.kmpble.codec

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

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
    fun utf8DecodeThrowsOnInvalidSequence() {
        val invalid = byteArrayOf(0xC3.toByte(), 0x28)
        assertFails { Utf8StringCodec.decode(invalid) }
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
        assertFailsWith<IllegalArgumentException> { Uint8Codec.decode(byteArrayOf()) }
        assertFailsWith<IllegalArgumentException> { Uint8Codec.decode(byteArrayOf(0x01, 0x02)) }
    }

    @Test
    fun uint8EncodeRejectsOutOfRange() {
        assertFailsWith<IllegalArgumentException> { Uint8Codec.encode(-1) }
        assertFailsWith<IllegalArgumentException> { Uint8Codec.encode(256) }
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
        assertFailsWith<IllegalArgumentException> { Int8Codec.decode(byteArrayOf()) }
        assertFailsWith<IllegalArgumentException> { Int8Codec.decode(byteArrayOf(0x01, 0x02)) }
    }

    @Test
    fun int8EncodeRejectsOutOfRange() {
        assertFailsWith<IllegalArgumentException> { Int8Codec.encode(-129) }
        assertFailsWith<IllegalArgumentException> { Int8Codec.encode(128) }
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
    fun uint16IsLittleEndian() {
        assertContentEquals(byteArrayOf(0x80.toByte(), 0x01), Uint16Codec.encode(384))
    }

    @Test
    fun uint16DecodeRejectsWrongSize() {
        assertFailsWith<IllegalArgumentException> { Uint16Codec.decode(byteArrayOf()) }
        assertFailsWith<IllegalArgumentException> { Uint16Codec.decode(byteArrayOf(0x01)) }
        assertFailsWith<IllegalArgumentException> { Uint16Codec.decode(byteArrayOf(0x01, 0x02, 0x03)) }
    }

    @Test
    fun uint16EncodeRejectsOutOfRange() {
        assertFailsWith<IllegalArgumentException> { Uint16Codec.encode(-1) }
        assertFailsWith<IllegalArgumentException> { Uint16Codec.encode(0x10000) }
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
    fun int16EncodeRejectsOutOfRange() {
        assertFailsWith<IllegalArgumentException> { Int16Codec.encode(-32769) }
        assertFailsWith<IllegalArgumentException> { Int16Codec.encode(32768) }
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
    fun uint32IsLittleEndian() {
        assertContentEquals(byteArrayOf(0x78, 0x56, 0x34, 0x12), Uint32Codec.encode(0x12345678L))
    }

    @Test
    fun uint32EncodeRejectsOutOfRange() {
        assertFailsWith<IllegalArgumentException> { Uint32Codec.encode(-1L) }
        assertFailsWith<IllegalArgumentException> { Uint32Codec.encode(0x1_0000_0000L) }
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
        assertFailsWith<IllegalArgumentException> { Int32Codec.decode(byteArrayOf(0x01, 0x02, 0x03)) }
    }
}
