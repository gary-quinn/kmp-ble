package com.atruedev.kmpble.profiles.parsing

/**
 * Little-endian byte builder for constructing BLE characteristic payloads.
 *
 * Dual of [BleByteReader]. Designed to be created, written to linearly, and
 * finalized with [toByteArray] within a single pure encode function.
 * Not thread-safe - not intended to be shared.
 *
 * Range-validates every integer write and throws [IllegalArgumentException]
 * on overflow, matching the strictness of [BleByteReader].
 */
public class BleByteWriter(initialCapacity: Int = 16) {
    private val buffer: ArrayList<Byte> = ArrayList(initialCapacity)

    public val size: Int get() = buffer.size

    public fun writeUInt8(value: Int): BleByteWriter {
        require(value in 0..0xFF) { "uint8 out of range: $value" }
        buffer.add(value.toByte())
        return this
    }

    public fun writeInt8(value: Int): BleByteWriter {
        require(value in Byte.MIN_VALUE..Byte.MAX_VALUE) { "int8 out of range: $value" }
        buffer.add(value.toByte())
        return this
    }

    public fun writeUInt16(value: Int): BleByteWriter {
        require(value in 0..0xFFFF) { "uint16 out of range: $value" }
        buffer.add((value and 0xFF).toByte())
        buffer.add(((value shr 8) and 0xFF).toByte())
        return this
    }

    public fun writeInt16(value: Int): BleByteWriter {
        require(value in Short.MIN_VALUE..Short.MAX_VALUE) { "int16 out of range: $value" }
        val unsigned = if (value < 0) value + 0x10000 else value
        return writeUInt16(unsigned)
    }

    public fun writeUInt32(value: Long): BleByteWriter {
        require(value in 0..0xFFFFFFFFL) { "uint32 out of range: $value" }
        buffer.add((value and 0xFF).toByte())
        buffer.add(((value shr 8) and 0xFF).toByte())
        buffer.add(((value shr 16) and 0xFF).toByte())
        buffer.add(((value shr 24) and 0xFF).toByte())
        return this
    }

    public fun writeUtf8(value: String): BleByteWriter {
        return writeBytes(value.encodeToByteArray())
    }

    public fun writeBytes(bytes: ByteArray): BleByteWriter {
        for (b in bytes) buffer.add(b)
        return this
    }

    public fun toByteArray(): ByteArray = buffer.toByteArray()
}
