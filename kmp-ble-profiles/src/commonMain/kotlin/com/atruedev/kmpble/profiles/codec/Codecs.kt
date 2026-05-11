package com.atruedev.kmpble.profiles.codec

import com.atruedev.kmpble.profiles.parsing.BleByteWriter

/** Codec that passes bytes through unchanged. Defensive copies on both sides. */
public object RawBytesCodec : Codec<ByteArray> {
    override fun encode(value: ByteArray): ByteArray = value.copyOf()

    override fun decode(bytes: ByteArray): ByteArray = bytes.copyOf()
}

/**
 * UTF-8 string codec. [decode] returns `null` on malformed UTF-8 byte sequences
 * (honoring the [Decoder] contract); pair with a logger if you need to surface
 * corrupted input.
 */
public object Utf8StringCodec : Codec<String> {
    override fun encode(value: String): ByteArray = value.encodeToByteArray()

    override fun decode(bytes: ByteArray): String? = try {
        bytes.decodeToString(throwOnInvalidSequence = true)
    } catch (_: CharacterCodingException) {
        null
    }
}

/**
 * Single-byte unsigned integer codec (0..255). Common for level/enum
 * characteristics like Battery Level (0x2A19).
 *
 * Strict size: [decode] returns `null` if input is not exactly 1 byte.
 */
public object Uint8Codec : Codec<Int> {
    override fun encode(value: Int): ByteArray {
        require(value in 0..0xFF) { "uint8 out of range: $value" }
        return byteArrayOf(value.toByte())
    }

    override fun decode(bytes: ByteArray): Int? =
        if (bytes.size != 1) null else bytes[0].toInt() and 0xFF
}

/** Single-byte signed integer codec (-128..127). Strict size on decode. */
public object Int8Codec : Codec<Int> {
    override fun encode(value: Int): ByteArray {
        require(value in Byte.MIN_VALUE..Byte.MAX_VALUE) { "int8 out of range: $value" }
        return byteArrayOf(value.toByte())
    }

    override fun decode(bytes: ByteArray): Int? =
        if (bytes.size != 1) null else bytes[0].toInt()
}

/**
 * Little-endian unsigned 16-bit integer codec (0..65535). Strict size on decode.
 */
public object Uint16Codec : Codec<Int> {
    override fun encode(value: Int): ByteArray {
        require(value in 0..0xFFFF) { "uint16 out of range: $value" }
        return BleByteWriter(2).writeUInt16(value).toByteArray()
    }

    override fun decode(bytes: ByteArray): Int? {
        if (bytes.size != 2) return null
        return (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
    }
}

/** Little-endian signed 16-bit integer codec (-32768..32767). Strict size on decode. */
public object Int16Codec : Codec<Int> {
    override fun encode(value: Int): ByteArray {
        require(value in Short.MIN_VALUE..Short.MAX_VALUE) { "int16 out of range: $value" }
        return BleByteWriter(2).writeInt16(value).toByteArray()
    }

    override fun decode(bytes: ByteArray): Int? {
        if (bytes.size != 2) return null
        val unsigned = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
        return if (unsigned >= 0x8000) unsigned - 0x10000 else unsigned
    }
}

/**
 * Little-endian unsigned 32-bit integer codec (0..4_294_967_295). Strict size
 * on decode.
 */
public object Uint32Codec : Codec<Long> {
    override fun encode(value: Long): ByteArray {
        require(value in 0..0xFFFFFFFFL) { "uint32 out of range: $value" }
        return BleByteWriter(4).writeUInt32(value).toByteArray()
    }

    override fun decode(bytes: ByteArray): Long? {
        if (bytes.size != 4) return null
        val b0 = (bytes[0].toInt() and 0xFF).toLong()
        val b1 = (bytes[1].toInt() and 0xFF).toLong()
        val b2 = (bytes[2].toInt() and 0xFF).toLong()
        val b3 = (bytes[3].toInt() and 0xFF).toLong()
        return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    }
}

/** Little-endian signed 32-bit integer codec. Strict size on decode. */
public object Int32Codec : Codec<Int> {
    override fun encode(value: Int): ByteArray {
        val unsigned = value.toLong() and 0xFFFFFFFFL
        return BleByteWriter(4).writeUInt32(unsigned).toByteArray()
    }

    override fun decode(bytes: ByteArray): Int? {
        if (bytes.size != 4) return null
        return (bytes[0].toInt() and 0xFF) or
            ((bytes[1].toInt() and 0xFF) shl 8) or
            ((bytes[2].toInt() and 0xFF) shl 16) or
            ((bytes[3].toInt() and 0xFF) shl 24)
    }
}
