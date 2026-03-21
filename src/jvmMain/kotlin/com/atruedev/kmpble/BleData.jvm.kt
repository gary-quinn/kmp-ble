package com.atruedev.kmpble

public actual class BleData internal constructor(
    internal val bytes: ByteArray,
    private val offset: Int = 0,
    public actual val size: Int = bytes.size,
) {
    public actual operator fun get(index: Int): Byte {
        if (index !in 0..<size) throw IndexOutOfBoundsException("index=$index, size=$size")
        return bytes[offset + index]
    }

    public actual fun toByteArray(): ByteArray = bytes.copyOfRange(offset, offset + size)

    public actual fun slice(fromIndex: Int, toIndex: Int): BleData {
        require(fromIndex in 0..toIndex && toIndex <= size) {
            "Invalid slice: fromIndex=$fromIndex, toIndex=$toIndex, size=$size"
        }
        return BleData(bytes, offset + fromIndex, toIndex - fromIndex)
    }

    public actual override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BleData) return false
        if (size != other.size) return false
        for (i in 0 until size) {
            if (this[i] != other[i]) return false
        }
        return true
    }

    public actual override fun hashCode(): Int {
        var result = size
        for (i in 0 until size) {
            result = 31 * result + this[i].toInt()
        }
        return result
    }

    public override fun toString(): String = "BleData(size=$size)"
}

public actual fun BleData(bytes: ByteArray): BleData = BleData(bytes.copyOf(), 0, bytes.size)

private val EMPTY: BleData = BleData(byteArrayOf())
public actual fun emptyBleData(): BleData = EMPTY
