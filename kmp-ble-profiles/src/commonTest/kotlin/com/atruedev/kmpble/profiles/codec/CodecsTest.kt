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
    fun uint8RoundTrips() {
        for (v in 0..255) {
            val encoded = Uint8Codec.encode(v)
            assertEquals(1, encoded.size)
            assertEquals(v, Uint8Codec.decode(encoded))
        }
    }

    @Test
    fun uint8DecodeEmptyReturnsNull() {
        assertNull(Uint8Codec.decode(byteArrayOf()))
    }

    @Test
    fun uint8EncodeRejectsOutOfRange() {
        assertFails { Uint8Codec.encode(-1) }
        assertFails { Uint8Codec.encode(256) }
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
    fun uint16DecodeShortReturnsNull() {
        assertNull(Uint16Codec.decode(byteArrayOf()))
        assertNull(Uint16Codec.decode(byteArrayOf(0x01)))
    }

    @Test
    fun uint16DecodeIgnoresExtraBytes() {
        assertEquals(384, Uint16Codec.decode(byteArrayOf(0x80.toByte(), 0x01, 0xFF.toByte())))
    }

    @Test
    fun uint16EncodeRejectsOutOfRange() {
        assertFails { Uint16Codec.encode(-1) }
        assertFails { Uint16Codec.encode(0x10000) }
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
    fun uint32DecodeShortReturnsNull() {
        assertNull(Uint32Codec.decode(byteArrayOf(0x01, 0x02, 0x03)))
    }

    @Test
    fun uint32EncodeRejectsOutOfRange() {
        assertFails { Uint32Codec.encode(-1L) }
        assertFails { Uint32Codec.encode(0x1_0000_0000L) }
    }

    @Test
    fun samConversion() {
        val decoder = Decoder { bytes -> bytes.size }
        val encoder = Encoder<Int> { value -> ByteArray(value) }
        assertEquals(3, decoder.decode(byteArrayOf(1, 2, 3)))
        assertEquals(5, encoder.encode(5).size)
    }
}
