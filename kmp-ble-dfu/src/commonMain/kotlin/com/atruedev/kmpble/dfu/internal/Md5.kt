package com.atruedev.kmpble.dfu.internal

/**
 * Pure Kotlin MD5 implementation for ESP OTA firmware verification.
 *
 * Follows RFC 1321. No platform dependencies — runs identically on all KMP targets.
 */
internal object Md5 {

    private val S = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
    )

    private val T = IntArray(64) { i ->
        val value = kotlin.math.abs(kotlin.math.sin((i + 1).toDouble())) * 4294967296.0
        value.toLong().toInt()
    }

    fun digest(data: ByteArray): ByteArray {
        val padded = pad(data)
        var a0 = 0x67452301
        var b0 = 0xEFCDAB89.toInt()
        var c0 = 0x98BADCFE.toInt()
        var d0 = 0x10325476

        for (blockStart in padded.indices step 64) {
            val m = IntArray(16) { j ->
                (padded[blockStart + j * 4].toInt() and 0xFF) or
                    ((padded[blockStart + j * 4 + 1].toInt() and 0xFF) shl 8) or
                    ((padded[blockStart + j * 4 + 2].toInt() and 0xFF) shl 16) or
                    ((padded[blockStart + j * 4 + 3].toInt() and 0xFF) shl 24)
            }

            var a = a0; var b = b0; var c = c0; var d = d0

            for (i in 0 until 64) {
                val f: Int
                val g: Int
                when {
                    i < 16 -> { f = (b and c) or (b.inv() and d); g = i }
                    i < 32 -> { f = (d and b) or (d.inv() and c); g = (5 * i + 1) % 16 }
                    i < 48 -> { f = b xor c xor d; g = (3 * i + 5) % 16 }
                    else -> { f = c xor (b or d.inv()); g = (7 * i) % 16 }
                }
                val temp = d
                d = c
                c = b
                b += (a + f + T[i] + m[g]).rotateLeft(S[i])
                a = temp
            }

            a0 += a; b0 += b; c0 += c; d0 += d
        }

        return intToLittleEndianBytes(a0) +
            intToLittleEndianBytes(b0) +
            intToLittleEndianBytes(c0) +
            intToLittleEndianBytes(d0)
    }

    fun digestHex(data: ByteArray): String =
        digest(data).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private fun pad(data: ByteArray): ByteArray {
        val bitLen = data.size.toLong() * 8
        val paddingLen = (56 - (data.size + 1) % 64 + 64) % 64
        val padded = ByteArray(data.size + 1 + paddingLen + 8)
        data.copyInto(padded)
        padded[data.size] = 0x80.toByte()
        for (i in 0 until 8) {
            padded[padded.size - 8 + i] = (bitLen shr (i * 8)).toByte()
        }
        return padded
    }

    private fun Int.rotateLeft(bits: Int): Int = (this shl bits) or (this ushr (32 - bits))

    private fun intToLittleEndianBytes(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )
}
