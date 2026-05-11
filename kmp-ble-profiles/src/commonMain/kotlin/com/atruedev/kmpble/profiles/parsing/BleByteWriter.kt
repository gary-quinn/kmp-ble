package com.atruedev.kmpble.profiles.parsing

/**
 * Little-endian byte builder for constructing BLE characteristic payloads.
 *
 * Symmetric counterpart to [BleByteReader]: same backing storage strategy
 * (raw [ByteArray] + cursor), same little-endian semantics, same strict
 * range validation. Designed to be created, written to linearly, and
 * finalized with [toByteArray] within a single pure encode function.
 *
 * Not thread-safe.
 */
public class BleByteWriter(initialCapacity: Int = 16) {
    private var buffer: ByteArray = ByteArray(initialCapacity.coerceAtLeast(1))

    public var size: Int = 0
        private set

    public fun writeUInt8(value: Int): BleByteWriter {
        require(value in 0..0xFF) { "uint8 out of range: $value" }
        ensure(1)
        buffer[size++] = value.toByte()
        return this
    }

    public fun writeInt8(value: Int): BleByteWriter {
        require(value in Byte.MIN_VALUE..Byte.MAX_VALUE) { "int8 out of range: $value" }
        ensure(1)
        buffer[size++] = value.toByte()
        return this
    }

    public fun writeUInt16(value: Int): BleByteWriter {
        require(value in 0..0xFFFF) { "uint16 out of range: $value" }
        ensure(2)
        buffer[size++] = (value and 0xFF).toByte()
        buffer[size++] = ((value shr 8) and 0xFF).toByte()
        return this
    }

    public fun writeInt16(value: Int): BleByteWriter {
        require(value in Short.MIN_VALUE..Short.MAX_VALUE) { "int16 out of range: $value" }
        val unsigned = if (value < 0) value + 0x10000 else value
        return writeUInt16(unsigned)
    }

    public fun writeUInt32(value: Long): BleByteWriter {
        require(value in 0..0xFFFFFFFFL) { "uint32 out of range: $value" }
        ensure(4)
        buffer[size++] = (value and 0xFF).toByte()
        buffer[size++] = ((value shr 8) and 0xFF).toByte()
        buffer[size++] = ((value shr 16) and 0xFF).toByte()
        buffer[size++] = ((value shr 24) and 0xFF).toByte()
        return this
    }

    public fun writeUtf8(value: String): BleByteWriter = writeBytes(value.encodeToByteArray())

    public fun writeBytes(bytes: ByteArray): BleByteWriter {
        ensure(bytes.size)
        bytes.copyInto(buffer, size)
        size += bytes.size
        return this
    }

    public fun toByteArray(): ByteArray = buffer.copyOf(size)

    private fun ensure(extra: Int) {
        val needed = size + extra
        if (needed <= buffer.size) return
        var newCap = buffer.size
        while (newCap < needed) newCap *= 2
        buffer = buffer.copyOf(newCap)
    }
}
