package com.atruedev.kmpble.codec

/** Codec that passes bytes through unchanged. Defensive copies on both sides. */
public object RawBytesCodec : BleCodec<ByteArray> {
    override fun encode(value: ByteArray): ByteArray = value.copyOf()

    override fun decode(data: ByteArray): ByteArray = data.copyOf()
}

/**
 * UTF-8 string codec. [decode] throws [IllegalArgumentException] on malformed
 * UTF-8 byte sequences (via [ByteArray.decodeToString] with
 * `throwOnInvalidSequence = true`).
 */
public object Utf8StringCodec : BleCodec<String> {
    override fun encode(value: String): ByteArray = value.encodeToByteArray()

    override fun decode(data: ByteArray): String = data.decodeToString(throwOnInvalidSequence = true)
}

/**
 * Single-byte unsigned integer codec (0..255). Common for level/enum
 * characteristics like Battery Level (0x2A19).
 *
 * Strict size: [decode] throws [IllegalArgumentException] if input is not
 * exactly 1 byte. [encode] throws if value is out of range.
 */
public object Uint8Codec : BleCodec<Int> {
    override fun encode(value: Int): ByteArray {
        require(value in 0..0xFF) { "uint8 out of range: $value" }
        return byteArrayOf(value.toByte())
    }

    override fun decode(data: ByteArray): Int {
        require(data.size == 1) { "Uint8Codec expects 1 byte, got ${data.size}" }
        return data[0].toInt() and 0xFF
    }
}

/** Single-byte signed integer codec (-128..127). Strict size on decode. */
public object Int8Codec : BleCodec<Int> {
    override fun encode(value: Int): ByteArray {
        require(value in Byte.MIN_VALUE..Byte.MAX_VALUE) { "int8 out of range: $value" }
        return byteArrayOf(value.toByte())
    }

    override fun decode(data: ByteArray): Int {
        require(data.size == 1) { "Int8Codec expects 1 byte, got ${data.size}" }
        return data[0].toInt()
    }
}

/**
 * Little-endian unsigned 16-bit integer codec (0..65535). Strict size on decode.
 */
public object Uint16Codec : BleCodec<Int> {
    override fun encode(value: Int): ByteArray {
        require(value in 0..0xFFFF) { "uint16 out of range: $value" }
        return byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())
    }

    override fun decode(data: ByteArray): Int {
        require(data.size == 2) { "Uint16Codec expects 2 bytes, got ${data.size}" }
        return (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
    }
}

/** Little-endian signed 16-bit integer codec (-32768..32767). Strict size on decode. */
public object Int16Codec : BleCodec<Int> {
    override fun encode(value: Int): ByteArray {
        require(value in Short.MIN_VALUE..Short.MAX_VALUE) { "int16 out of range: $value" }
        val unsigned = if (value < 0) value + 0x10000 else value
        return byteArrayOf((unsigned and 0xFF).toByte(), ((unsigned shr 8) and 0xFF).toByte())
    }

    override fun decode(data: ByteArray): Int {
        require(data.size == 2) { "Int16Codec expects 2 bytes, got ${data.size}" }
        val unsigned = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
        return if (unsigned >= 0x8000) unsigned - 0x10000 else unsigned
    }
}

/**
 * Little-endian unsigned 32-bit integer codec (0..4_294_967_295). Strict size
 * on decode.
 */
public object Uint32Codec : BleCodec<Long> {
    override fun encode(value: Long): ByteArray {
        require(value in 0..0xFFFFFFFFL) { "uint32 out of range: $value" }
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
        )
    }

    override fun decode(data: ByteArray): Long {
        require(data.size == 4) { "Uint32Codec expects 4 bytes, got ${data.size}" }
        val b0 = (data[0].toInt() and 0xFF).toLong()
        val b1 = (data[1].toInt() and 0xFF).toLong()
        val b2 = (data[2].toInt() and 0xFF).toLong()
        val b3 = (data[3].toInt() and 0xFF).toLong()
        return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    }
}

/** Little-endian signed 32-bit integer codec. Strict size on decode. */
public object Int32Codec : BleCodec<Int> {
    override fun encode(value: Int): ByteArray =
        byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
        )

    override fun decode(data: ByteArray): Int {
        require(data.size == 4) { "Int32Codec expects 4 bytes, got ${data.size}" }
        return (data[0].toInt() and 0xFF) or
            ((data[1].toInt() and 0xFF) shl 8) or
            ((data[2].toInt() and 0xFF) shl 16) or
            ((data[3].toInt() and 0xFF) shl 24)
    }
}
