@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.atruedev.kmpble.codec.serialization

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.cbor.Cbor
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Serializable
private data class Reading(val timestampMs: Long, val celsius: Double)

@Serializable
private data class Nested(
    val id: Int,
    val tags: List<String>,
    val payload: Reading,
)

@Serializable
private data class Tiny(val v: Int)

class CborCodecTest {

    @Test
    fun roundTripsSimpleStruct() {
        val codec = CborCodec(Reading.serializer())
        val value = Reading(timestampMs = 1_700_000_000_000, celsius = 21.5)

        val encoded = codec.encode(value)
        val decoded = codec.decode(encoded)

        assertEquals(value, decoded)
    }

    @Test
    fun roundTripsNestedStruct() {
        val codec = CborCodec(Nested.serializer())
        val value =
            Nested(
                id = 42,
                tags = listOf("alpha", "beta"),
                payload = Reading(0L, -10.0),
            )

        val decoded = codec.decode(codec.encode(value))

        assertEquals(value, decoded)
    }

    @Test
    fun encodesIndependentBytesForDifferentValues() {
        val codec = CborCodec(Tiny.serializer())

        val a = codec.encode(Tiny(1))
        val b = codec.encode(Tiny(2))

        assertFalse(a.contentEquals(b))
    }

    @Test
    fun decodeThrowsOnMalformedBytes() {
        val codec = CborCodec(Reading.serializer())
        val malformed = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0xFD.toByte())

        assertFailsWith<SerializationException> { codec.decode(malformed) }
    }

    @Test
    fun decodeThrowsOnTruncatedBytes() {
        val codec = CborCodec(Reading.serializer())
        val full = codec.encode(Reading(1L, 1.0))
        val truncated = full.copyOfRange(0, full.size / 2)

        assertFailsWith<SerializationException> { codec.decode(truncated) }
    }

    @Test
    fun acceptsCustomCborConfiguration() {
        val customCodec =
            CborCodec(
                Tiny.serializer(),
                Cbor { ignoreUnknownKeys = true },
            )
        val encoded = customCodec.encode(Tiny(7))

        assertEquals(Tiny(7), customCodec.decode(encoded))
    }

    @Test
    fun reifiedFactoryProducesEquivalentCodec() {
        val direct = CborCodec(Reading.serializer())
        val reified = cborCodec<Reading>()
        val value = Reading(1L, 2.0)

        assertContentEquals(direct.encode(value), reified.encode(value))
        assertEquals(value, reified.decode(direct.encode(value)))
    }

    @Test
    fun reifiedFactoryHonorsCustomCborInstance() {
        val codec = cborCodec<Tiny>(Cbor { ignoreUnknownKeys = true })

        assertEquals(Tiny(99), codec.decode(codec.encode(Tiny(99))))
    }

    @Test
    fun encodedPayloadIsCompactRelativeToJsonString() {
        val codec = CborCodec(Reading.serializer())
        val value = Reading(1_700_000_000_000L, 21.5)
        val cborBytes = codec.encode(value)
        val asJsonString = "{\"timestampMs\":${value.timestampMs},\"celsius\":${value.celsius}}"

        assertTrue(
            cborBytes.size < asJsonString.encodeToByteArray().size,
            "CBOR encoding ought to be smaller than the equivalent JSON string",
        )
    }
}
