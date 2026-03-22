package com.atruedev.kmpble.dfu.internal

internal fun ByteArray.readIntLE(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        ((this[offset + 2].toInt() and 0xFF) shl 16) or
        ((this[offset + 3].toInt() and 0xFF) shl 24)

internal fun ByteArray.readUIntLE(offset: Int): UInt =
    readIntLE(offset).toUInt()

internal fun ByteArray.readShortLE(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8)

internal fun Int.toLittleEndianBytes(): ByteArray = byteArrayOf(
    (this and 0xFF).toByte(),
    ((this shr 8) and 0xFF).toByte(),
    ((this shr 16) and 0xFF).toByte(),
    ((this shr 24) and 0xFF).toByte(),
)

internal fun Short.toLittleEndianBytes(): ByteArray = byteArrayOf(
    (this.toInt() and 0xFF).toByte(),
    ((this.toInt() shr 8) and 0xFF).toByte(),
)
