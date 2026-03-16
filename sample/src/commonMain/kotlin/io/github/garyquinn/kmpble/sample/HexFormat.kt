package io.github.garyquinn.kmpble.sample

private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

fun ByteArray.toHexString(): String {
    val result = StringBuilder(size * 2)
    for (byte in this) {
        val i = byte.toInt()
        result.append(HEX_CHARS[(i shr 4) and 0x0F])
        result.append(HEX_CHARS[i and 0x0F])
    }
    return result.toString()
}

fun String.hexToByteArray(): ByteArray {
    val hex = replace(" ", "")
    require(hex.length % 2 == 0) { "Hex string must have even length" }
    return ByteArray(hex.length / 2) { i ->
        val index = i * 2
        hex.substring(index, index + 2).toInt(16).toByte()
    }
}
