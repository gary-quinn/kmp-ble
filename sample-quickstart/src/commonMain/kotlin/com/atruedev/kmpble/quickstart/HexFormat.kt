package com.atruedev.kmpble.quickstart

private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

internal fun ByteArray.toHexString(): String {
    val result = StringBuilder(size * 2)
    for (byte in this) {
        val i = byte.toInt()
        result.append(HEX_CHARS[(i shr 4) and 0x0F])
        result.append(HEX_CHARS[i and 0x0F])
    }
    return result.toString()
}

internal fun parseHeartRate(data: ByteArray): Int? {
    if (data.isEmpty()) return null
    val flags = data[0].toInt() and 0xFF
    return if ((flags and 0x01) == 0) {
        if (data.size < 2) null else data[1].toInt() and 0xFF
    } else {
        if (data.size < 3) null else ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
    }
}
