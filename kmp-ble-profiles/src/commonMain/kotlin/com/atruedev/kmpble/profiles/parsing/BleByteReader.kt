package com.atruedev.kmpble.profiles.parsing

/**
 * Little-endian byte cursor for parsing BLE characteristic payloads.
 *
 * Designed to be created, consumed linearly, and discarded within a single
 * pure parse function. Not thread-safe — not intended to be shared.
 */
public class BleByteReader(private val data: ByteArray) {

    public var offset: Int = 0
        private set

    public val remaining: Int get() = data.size - offset

    public fun hasRemaining(n: Int = 1): Boolean = remaining >= n

    public fun readUInt8(): Int {
        check(hasRemaining(1))
        return data[offset++].toInt() and 0xFF
    }

    public fun readInt8(): Int {
        check(hasRemaining(1))
        return data[offset++].toInt()
    }

    public fun readUInt16(): Int {
        check(hasRemaining(2))
        val low = data[offset++].toInt() and 0xFF
        val high = data[offset++].toInt() and 0xFF
        return (high shl 8) or low
    }

    public fun readInt16(): Int {
        val unsigned = readUInt16()
        return if (unsigned >= 0x8000) unsigned - 0x10000 else unsigned
    }

    public fun readUInt32(): Long {
        check(hasRemaining(4))
        val b0 = (data[offset++].toInt() and 0xFF).toLong()
        val b1 = (data[offset++].toInt() and 0xFF).toLong()
        val b2 = (data[offset++].toInt() and 0xFF).toLong()
        val b3 = (data[offset++].toInt() and 0xFF).toLong()
        return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    }

    public fun readSFloat(): Float? {
        val raw = readUInt16()
        return sfloatToFloat(raw)
    }

    public fun readFloat(): Float? {
        val raw = readUInt32()
        return floatToFloat(raw)
    }

    public fun readUtf8(length: Int): String {
        check(hasRemaining(length))
        val result = data.decodeToString(offset, offset + length)
        offset += length
        return result
    }

    public fun readDateTime(): BleDateTime {
        check(hasRemaining(7))
        return BleDateTime(
            year = readUInt16(),
            month = readUInt8(),
            day = readUInt8(),
            hours = readUInt8(),
            minutes = readUInt8(),
            seconds = readUInt8(),
        )
    }

    public fun skip(n: Int) {
        check(hasRemaining(n))
        offset += n
    }
}
