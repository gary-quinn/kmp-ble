@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.atruedev.kmpble

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSMakeRange
import platform.Foundation.create
import platform.Foundation.subdataWithRange
import platform.posix.memcpy
import platform.posix.uint8_tVar

public actual class BleData internal constructor(
    internal val nsData: NSData,
) {
    public actual val size: Int get() = nsData.length.toInt()

    public actual operator fun get(index: Int): Byte {
        if (index < 0 || index >= size) throw IndexOutOfBoundsException("index=$index, size=$size")
        val ptr = nsData.bytes!!.reinterpret<uint8_tVar>()
        return ptr[index].toByte()
    }

    public actual fun toByteArray(): ByteArray {
        val length = size
        if (length == 0) return byteArrayOf()
        val bytes = ByteArray(length)
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), nsData.bytes, nsData.length)
        }
        return bytes
    }

    public actual fun slice(fromIndex: Int, toIndex: Int): BleData {
        require(fromIndex in 0..toIndex && toIndex <= size) {
            "Invalid slice: fromIndex=$fromIndex, toIndex=$toIndex, size=$size"
        }
        val subdata = nsData.subdataWithRange(NSMakeRange(fromIndex.toULong(), (toIndex - fromIndex).toULong()))
        return BleData(subdata)
    }

    public actual override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BleData) return false
        return nsData.isEqual(other.nsData)
    }

    public actual override fun hashCode(): Int = nsData.hash.toInt()

    override fun toString(): String = "BleData(size=$size)"
}

public fun bleDataFromNSData(nsData: NSData): BleData = BleData(nsData)

public actual fun BleData(bytes: ByteArray): BleData {
    if (bytes.isEmpty()) return emptyBleData()
    val nsData = bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
    return BleData(nsData)
}

private val EMPTY = BleData(NSData())
public actual fun emptyBleData(): BleData = EMPTY
