package com.atruedev.kmpble.codec

import com.atruedev.kmpble.BleData
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class BleCodecTest {

    @Test
    fun encoderProducesByteArray() {
        assertContentEquals(byteArrayOf(0x01, 0x48), TestIntEncoder.encode(0x0148))
    }

    @Test
    fun decoderProducesTypedValue() {
        assertEquals(0x0148, TestIntDecoder.decode(byteArrayOf(0x01, 0x48)))
    }

    @Test
    fun bleCodecFactoryRoundTrips() {
        val codec = bleCodec(TestIntEncoder, TestIntDecoder)
        assertEquals(1000, codec.decode(codec.encode(1000)))
    }

    @Test
    fun decoderMapTransformsOutput() {
        val stringDecoder = TestIntDecoder.map { it.toString() }
        assertEquals("1000", stringDecoder.decode(byteArrayOf(0x03, 0xE8.toByte())))
    }

    @Test
    fun encoderContramapTransformsInput() {
        val stringEncoder = TestIntEncoder.contramap<String, Int> { it.toInt() }
        assertContentEquals(byteArrayOf(0x03, 0xE8.toByte()), stringEncoder.encode("1000"))
    }

    @Test
    fun bimapTransformsBothDirections() {
        val stringCodec = bleCodec(TestIntEncoder, TestIntDecoder).bimap(
            encode = { s: String -> s.toInt() },
            decode = { i: Int -> i.toString() },
        )
        assertEquals("1000", stringCodec.decode(stringCodec.encode("1000")))
    }

    @Test
    fun lambdaConstructionWorksForEncoder() {
        val encoder: BleEncoder<String> = BleEncoder { it.encodeToByteArray() }
        assertContentEquals("hello".encodeToByteArray(), encoder.encode("hello"))
    }

    @Test
    fun lambdaConstructionWorksForDecoder() {
        val decoder: BleDecoder<String> = BleDecoder { it.decodeToString() }
        assertEquals("hello", decoder.decode("hello".encodeToByteArray()))
    }

    @Test
    fun mapChaining() {
        val decoder = TestIntDecoder.map { it * 2 }.map { it + 1 }
        assertEquals(2001, decoder.decode(byteArrayOf(0x03, 0xE8.toByte())))
    }

    @Test
    fun contramapChaining() {
        val encoder = TestIntEncoder
            .contramap<Int, Int> { it - 1 }
            .contramap<Int, Int> { it * 2 }
        // 501 → *2 → 1002 → -1 → 1001 → encode
        assertContentEquals(byteArrayOf(0x03, 0xE9.toByte()), encoder.encode(501))
    }

    @Test
    fun bleDataDecoderReadsFromBleData() {
        assertEquals(0x0148, TestIntBleDataDecoder.decode(BleData(byteArrayOf(0x01, 0x48))))
    }

    @Test
    fun bleDataDecoderLambdaConstruction() {
        val decoder: BleDataDecoder<Byte> = BleDataDecoder { it[0] }
        assertEquals(0x42, decoder.decode(BleData(byteArrayOf(0x42))))
    }

    @Test
    fun bleDataDecoderMap() {
        val stringDecoder = TestIntBleDataDecoder.map { it.toString() }
        assertEquals("1000", stringDecoder.decode(BleData(byteArrayOf(0x03, 0xE8.toByte()))))
    }

    @Test
    fun bleDataDecoderMapChaining() {
        val decoder = TestIntBleDataDecoder.map { it * 2 }.map { it + 1 }
        assertEquals(2001, decoder.decode(BleData(byteArrayOf(0x03, 0xE8.toByte()))))
    }

    @Test
    fun bleDataEncoderProducesBleData() {
        val result = TestIntBleDataEncoder.encode(0x0148)
        assertEquals(0x01, result[0])
        assertEquals(0x48, result[1])
    }

    @Test
    fun bleDataEncoderLambdaConstruction() {
        val encoder: BleDataEncoder<String> = BleDataEncoder { BleData(it.encodeToByteArray()) }
        val result = encoder.encode("hi")
        assertContentEquals("hi".encodeToByteArray(), result.toByteArray())
    }

    @Test
    fun bleDataEncoderContramap() {
        val stringEncoder = TestIntBleDataEncoder.contramap<String, Int> { it.toInt() }
        val result = stringEncoder.encode("1000")
        assertEquals(0x03, result[0])
        assertEquals(0xE8.toByte(), result[1])
    }

    @Test
    fun bleDecoderAsBleDataDecoder() {
        val bridged = TestIntDecoder.asBleDataDecoder()
        assertEquals(0x0148, bridged.decode(BleData(byteArrayOf(0x01, 0x48))))
    }

    @Test
    fun bleDataDecoderAsBleDecoder() {
        val bridged = TestIntBleDataDecoder.asBleDecoder()
        assertEquals(0x0148, bridged.decode(byteArrayOf(0x01, 0x48)))
    }

    @Test
    fun bleEncoderAsBleDataEncoder() {
        val bridged = TestIntEncoder.asBleDataEncoder()
        val result = bridged.encode(0x0148)
        assertEquals(0x01, result[0])
        assertEquals(0x48, result[1])
    }

    @Test
    fun bleDataEncoderAsBleEncoder() {
        val bridged = TestIntBleDataEncoder.asBleEncoder()
        assertContentEquals(byteArrayOf(0x01, 0x48), bridged.encode(0x0148))
    }

    @Test
    fun decoderBridgingRoundTripPreservesValue() {
        val roundTripped = TestIntBleDataDecoder.asBleDecoder().asBleDataDecoder()
        val bleData = BleData(byteArrayOf(0x03, 0xE8.toByte()))
        assertEquals(TestIntBleDataDecoder.decode(bleData), roundTripped.decode(bleData))
    }

    @Test
    fun encoderBridgingRoundTripPreservesValue() {
        val roundTripped = TestIntBleDataEncoder.asBleEncoder().asBleDataEncoder()
        val original = TestIntBleDataEncoder.encode(1000)
        val bridged = roundTripped.encode(1000)
        assertEquals(original.toByteArray().toList(), bridged.toByteArray().toList())
    }

    @Test
    fun foldOnValueReturnsTransformedValue() {
        val obs: DecodedObservation<Int> = DecodedObservation.Value(42)
        val result = obs.fold(
            onValue = { "got $it" },
            onDisconnected = { "disconnected" },
        )
        assertEquals("got 42", result)
    }

    @Test
    fun foldOnDisconnectedReturnsDisconnectedResult() {
        val obs: DecodedObservation<Int> = DecodedObservation.Disconnected
        val result = obs.fold(
            onValue = { "got $it" },
            onDisconnected = { "disconnected" },
        )
        assertEquals("disconnected", result)
    }
}
