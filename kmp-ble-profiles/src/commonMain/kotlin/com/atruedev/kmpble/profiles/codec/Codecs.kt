package com.atruedev.kmpble.profiles.codec

import com.atruedev.kmpble.profiles.parsing.BleByteWriter

/** Codec that passes bytes through unchanged. Defensive copies on both sides. */
public object RawBytesCodec : Codec<ByteArray> {
    override fun encode(value: ByteArray): ByteArray = value.copyOf()

    override fun decode(bytes: ByteArray): ByteArray = bytes.copyOf()
}

/**
 * UTF-8 string codec. [decode] always succeeds; invalid byte sequences are
 * replaced with the Unicode replacement character per [ByteArray.decodeToString].
 */
public object Utf8StringCodec : Codec<String> {
    override fun encode(value: String): ByteArray = value.encodeToByteArray()

    override fun decode(bytes: ByteArray): String = bytes.decodeToString()
}

/**
 * Single-byte unsigned integer codec (0..255). Common for level/enum characteristics
 * like Battery Level (0x2A19).
 *
 * [decode] returns `null` for empty input. [encode] throws if value is out of range.
 */
public object Uint8Codec : Codec<Int> {
    override fun encode(value: Int): ByteArray {
        require(value in 0..0xFF) { "uint8 out of range: $value" }
        return byteArrayOf(value.toByte())
    }

    override fun decode(bytes: ByteArray): Int? =
        if (bytes.isEmpty()) null else bytes[0].toInt() and 0xFF
}

/**
 * Little-endian unsigned 16-bit integer codec (0..65535).
 *
 * [decode] returns `null` if input has fewer than 2 bytes. Extra bytes beyond
 * the first two are ignored.
 */
public object Uint16Codec : Codec<Int> {
    override fun encode(value: Int): ByteArray {
        require(value in 0..0xFFFF) { "uint16 out of range: $value" }
        return BleByteWriter(2).writeUInt16(value).toByteArray()
    }

    override fun decode(bytes: ByteArray): Int? {
        if (bytes.size < 2) return null
        return (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
    }
}

/**
 * Little-endian unsigned 32-bit integer codec (0..4_294_967_295).
 *
 * [decode] returns `null` if input has fewer than 4 bytes. Extra bytes beyond
 * the first four are ignored.
 */
public object Uint32Codec : Codec<Long> {
    override fun encode(value: Long): ByteArray {
        require(value in 0..0xFFFFFFFFL) { "uint32 out of range: $value" }
        return BleByteWriter(4).writeUInt32(value).toByteArray()
    }

    override fun decode(bytes: ByteArray): Long? {
        if (bytes.size < 4) return null
        val b0 = (bytes[0].toInt() and 0xFF).toLong()
        val b1 = (bytes[1].toInt() and 0xFF).toLong()
        val b2 = (bytes[2].toInt() and 0xFF).toLong()
        val b3 = (bytes[3].toInt() and 0xFF).toLong()
        return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    }
}
