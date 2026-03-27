package com.atruedev.kmpble.dfu.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CborTest {

    @Test
    fun encodeEmptyMap() {
        val encoded = Cbor.encodeMap(emptyMap())
        // CBOR: A0 = map(0)
        assertEquals(1, encoded.size)
        assertEquals(0xA0.toByte(), encoded[0])
    }

    @Test
    fun encodeMapWithSmallIntegers() {
        val encoded = Cbor.encodeMap(mapOf(0 to 42, 1 to 100))
        val decoded = Cbor.decodeMap(encoded)
        assertEquals(42L, decoded[0])
        assertEquals(100L, decoded[1])
    }

    @Test
    fun roundTripByteString() {
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val encoded = Cbor.encodeMap(mapOf(0 to payload))
        val decoded = Cbor.decodeMap(encoded)
        val result = assertIs<ByteSlice>(decoded[0])
        assertTrue(result.contentEquals(payload))
    }

    @Test
    fun roundTripTextString() {
        val encoded = Cbor.encodeMap(mapOf(0 to "hello"))
        val decoded = Cbor.decodeMap(encoded)
        assertEquals("hello", decoded[0])
    }

    @Test
    fun roundTripLargeInteger() {
        val encoded = Cbor.encodeMap(mapOf(0 to 100_000))
        val decoded = Cbor.decodeMap(encoded)
        assertEquals(100_000L, decoded[0])
    }

    @Test
    fun roundTripNegativeInteger() {
        val encoded = Cbor.encodeMap(mapOf(0 to -1))
        val decoded = Cbor.decodeMap(encoded)
        assertEquals(-1L, decoded[0])
    }

    @Test
    fun roundTripBoolean() {
        val encoded = Cbor.encodeMap(mapOf(0 to true, 1 to false))
        val decoded = Cbor.decodeMap(encoded)
        assertEquals(true, decoded[0])
        assertEquals(false, decoded[1])
    }

    @Test
    fun roundTripMultipleTypes() {
        val bytes = byteArrayOf(0xDE.toByte(), 0xAD.toByte())
        val encoded = Cbor.encodeMap(
            mapOf(
                0 to bytes,
                1 to 256,
                2 to "firmware",
                3 to 0,
            ),
        )
        val decoded = Cbor.decodeMap(encoded)
        assertEquals(4, decoded.size)
        assertIs<ByteSlice>(decoded[0])
        assertEquals(256L, decoded[1])
        assertEquals("firmware", decoded[2])
        assertEquals(0L, decoded[3])
    }

    @Test
    fun twoByteLength() {
        val largePayload = ByteArray(300) { it.toByte() }
        val encoded = Cbor.encodeMap(mapOf(0 to largePayload))
        val decoded = Cbor.decodeMap(encoded)
        val result = assertIs<ByteSlice>(decoded[0])
        assertEquals(300, result.length)
        assertEquals(largePayload.toList(), result.toByteArray().toList())
    }

    @Test
    fun byteSliceRoundTripThroughEncoder() {
        val source = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte())
        val slice = ByteSlice(source, 1, 2) // [0xBB, 0xCC]
        val encoded = Cbor.encodeMap(mapOf(0 to slice))
        val decoded = Cbor.decodeMap(encoded)
        val result = assertIs<ByteSlice>(decoded[0])
        assertTrue(result.contentEquals(byteArrayOf(0xBB.toByte(), 0xCC.toByte())))
    }

    @Test
    fun utf8MultiByteStringRoundTrip() {
        val encoded = Cbor.encodeStringMap(mapOf("café" to 1, "日本語" to 2))
        val decoded = Cbor.decodeStringMap(encoded)
        assertEquals(1L, decoded["café"])
        assertEquals(2L, decoded["日本語"])
    }
}
