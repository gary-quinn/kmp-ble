package com.atruedev.kmpble.mesh.crypto

/**
 * Pure Kotlin cryptographic primitives used as fallback on all platforms.
 *
 * BLE Mesh crypto is well-defined with spec test vectors for every primitive.
 * These pure Kotlin implementations are production-quality and ensure
 * deterministic behavior across Android, iOS, and JVM.
 *
 * All implementations avoid platform-specific APIs and compile on all
 * KMP targets including Kotlin/Native (iOS).
 */
internal object PureKotlinCrypto {

    // --- AES-128 single block (used by CCM and CMAC) ---

    private val SBOX = intArrayOf(
        0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5, 0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76,
        0xca, 0x82, 0xc9, 0x7d, 0xfa, 0x59, 0x47, 0xf0, 0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0,
        0xb7, 0xfd, 0x93, 0x26, 0x36, 0x3f, 0xf7, 0xcc, 0x34, 0xa5, 0xe5, 0xf1, 0x71, 0xd8, 0x31, 0x15,
        0x04, 0xc7, 0x23, 0xc3, 0x18, 0x96, 0x05, 0x9a, 0x07, 0x12, 0x80, 0xe2, 0xeb, 0x27, 0xb2, 0x75,
        0x09, 0x83, 0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0, 0x52, 0x3b, 0xd6, 0xb3, 0x29, 0xe3, 0x2f, 0x84,
        0x53, 0xd1, 0x00, 0xed, 0x20, 0xfc, 0xb1, 0x5b, 0x6a, 0xcb, 0xbe, 0x39, 0x4a, 0x4c, 0x58, 0xcf,
        0xd0, 0xef, 0xaa, 0xfb, 0x43, 0x4d, 0x33, 0x85, 0x45, 0xf9, 0x02, 0x7f, 0x50, 0x3c, 0x9f, 0xa8,
        0x51, 0xa3, 0x40, 0x8f, 0x92, 0x9d, 0x38, 0xf5, 0xbc, 0xb6, 0xda, 0x21, 0x10, 0xff, 0xf3, 0xd2,
        0xcd, 0x0c, 0x13, 0xec, 0x5f, 0x97, 0x44, 0x17, 0xc4, 0xa7, 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73,
        0x60, 0x81, 0x4f, 0xdc, 0x22, 0x2a, 0x90, 0x88, 0x46, 0xee, 0xb8, 0x14, 0xde, 0x5e, 0x0b, 0xdb,
        0xe0, 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c, 0xc2, 0xd3, 0xac, 0x62, 0x91, 0x95, 0xe4, 0x79,
        0xe7, 0xc8, 0x37, 0x6d, 0x8d, 0xd5, 0x4e, 0xa9, 0x6c, 0x56, 0xf4, 0xea, 0x65, 0x7a, 0xae, 0x08,
        0xba, 0x78, 0x25, 0x2e, 0x1c, 0xa6, 0xb4, 0xc6, 0xe8, 0xdd, 0x74, 0x1f, 0x4b, 0xbd, 0x8b, 0x8a,
        0x70, 0x3e, 0xb5, 0x66, 0x48, 0x03, 0xf6, 0x0e, 0x61, 0x35, 0x57, 0xb9, 0x86, 0xc1, 0x1d, 0x9e,
        0xe1, 0xf8, 0x98, 0x11, 0x69, 0xd9, 0x8e, 0x94, 0x9b, 0x1e, 0x87, 0xe9, 0xce, 0x55, 0x28, 0xdf,
        0x8c, 0xa1, 0x89, 0x0d, 0xbf, 0xe6, 0x42, 0x68, 0x41, 0x99, 0x2d, 0x0f, 0xb0, 0x54, 0xbb, 0x16,
    )

    /** AES-128 single-block encrypt. Both key and block must be 16 bytes. */
    fun aes128EncryptBlock(key: ByteArray, block: ByteArray): ByteArray {
        require(key.size == 16 && block.size == 16)
        val state = IntArray(16)
        val roundKeys = expandKey(key)
        for (i in 0 until 16) state[i] = block[i].toInt() and 0xFF

        addRoundKey(state, roundKeys, 0)
        for (round in 1..9) {
            subBytes(state); shiftRows(state); mixColumns(state)
            addRoundKey(state, roundKeys, round)
        }
        subBytes(state); shiftRows(state)
        addRoundKey(state, roundKeys, 10)

        return ByteArray(16) { state[it].toByte() }
    }

    private fun expandKey(key: ByteArray): IntArray {
        val w = IntArray(176)
        for (i in 0 until 4) {
            w[i] = ((key[4*i].toInt() and 0xFF) shl 24) or
                ((key[4*i+1].toInt() and 0xFF) shl 16) or
                ((key[4*i+2].toInt() and 0xFF) shl 8) or
                (key[4*i+3].toInt() and 0xFF)
        }
        for (i in 4 until 44) {
            var temp = w[i-1]
            if (i % 4 == 0) {
                temp = (SBOX[(temp ushr 16) and 0xFF] shl 24) or
                    (SBOX[(temp ushr 8) and 0xFF] shl 16) or
                    (SBOX[temp and 0xFF] shl 8) or
                    SBOX[(temp ushr 24) and 0xFF]
                temp = temp xor (RCON[i/4 - 1] shl 24)
            }
            w[i] = w[i-4] xor temp
        }
        return w
    }

    private fun addRoundKey(state: IntArray, w: IntArray, round: Int) {
        for (i in 0 until 4) {
            val k = w[round*4 + i]
            state[4*i] = state[4*i] xor ((k ushr 24) and 0xFF)
            state[4*i+1] = state[4*i+1] xor ((k ushr 16) and 0xFF)
            state[4*i+2] = state[4*i+2] xor ((k ushr 8) and 0xFF)
            state[4*i+3] = state[4*i+3] xor (k and 0xFF)
        }
    }

    private fun subBytes(state: IntArray) { for (i in 0 until 16) state[i] = SBOX[state[i]] }

    private fun shiftRows(state: IntArray) {
        var t = state[1]; state[1] = state[5]; state[5] = state[9]; state[9] = state[13]; state[13] = t
        t = state[2]; state[2] = state[10]; state[10] = t
        t = state[6]; state[6] = state[14]; state[14] = t
        t = state[3]; state[3] = state[15]; state[15] = state[11]; state[11] = state[7]; state[7] = t
    }

    private fun mixColumns(state: IntArray) {
        for (i in 0 until 4) {
            val col = i * 4
            val a = IntArray(4) { state[col + it] }
            state[col] = gm(2, a[0]) xor gm(3, a[1]) xor a[2] xor a[3]
            state[col+1] = a[0] xor gm(2, a[1]) xor gm(3, a[2]) xor a[3]
            state[col+2] = a[0] xor a[1] xor gm(2, a[2]) xor gm(3, a[3])
            state[col+3] = gm(3, a[0]) xor a[1] xor a[2] xor gm(2, a[3])
        }
    }

    private fun gm(multiplier: Int, value: Int): Int {
        var v = value
        if (multiplier == 2) v = (v shl 1) xor (if (v and 0x80 != 0) 0x1b else 0)
        else if (multiplier == 3) v = gm(2, v) xor value
        return v and 0xFF
    }

    // --- SHA-256 ---

    /** SHA-256 round constants: first 32 bits of fractional parts of cube roots of first 64 primes. */
    private val KCONST = intArrayOf(
        1116352408, 1899447441, -1245643825, -373957723,
        961987163, 1508970993, -1841331548, -1424204075,
        -670586216, 310598401, 607225278, 1426881987,
        1925078388, -2132889090, -1680079193, -1046744716,
        -459576895, -272742522, 264347078, 604807628,
        770255983, 1249150122, 1555081692, 1996064986,
        -1740746414, -1473132947, -1341970488, -1084653625,
        -958395405, -710438585, 113926993, 338241895,
        666307205, 773529912, 1294757372, 1396182291,
        1695183700, 1986661051, -2117940946, -1838011259,
        -1564481375, -1474664885, -1035236496, -949202525,
        -778901479, -694614492, -200395387, 275423344,
        430227734, 506948616, 659060556, 883997877,
        958139571, 1322822218, 1537002063, 1747873779,
        1955562222, 2024104815, -2067236844, -1933114872,
        -1866530822, -1538233109, -1090935817, -965641998,
    )

    private val RCON = intArrayOf(0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1b, 0x36)

    fun sha256(data: ByteArray): ByteArray {
        val h = intArrayOf(
            1779033703, -1150833019, 1013904242, -1521486534,
            1359893119, -1694144372, 528734635, 1541459225,
        )
        val padded = padMessage(data)
        for (chunkStart in padded.indices step 64) {
            val w = IntArray(64)
            for (i in 0 until 16) {
                val base = chunkStart + i * 4
                w[i] = ((padded[base].toInt() and 0xFF) shl 24) or
                    ((padded[base+1].toInt() and 0xFF) shl 16) or
                    ((padded[base+2].toInt() and 0xFF) shl 8) or
                    (padded[base+3].toInt() and 0xFF)
            }
            for (i in 16 until 64) {
                val s0 = rotr(w[i-15], 7) xor rotr(w[i-15], 18) xor (w[i-15] ushr 3)
                val s1 = rotr(w[i-2], 17) xor rotr(w[i-2], 19) xor (w[i-2] ushr 10)
                w[i] = w[i-16] + s0 + w[i-7] + s1
            }
            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
            var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
            for (i in 0 until 64) {
                val s1 = rotr(e, 6) xor rotr(e, 11) xor rotr(e, 25)
                val ch = (e and f) xor (e.inv() and g)
                val temp1 = hh + s1 + ch + KCONST[i] + w[i]
                val s0 = rotr(a, 2) xor rotr(a, 13) xor rotr(a, 22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + maj
                hh = g; g = f; f = e; e = d + temp1
                d = c; c = b; b = a; a = temp1 + temp2
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += d
            h[4] += e; h[5] += f; h[6] += g; h[7] += hh
        }
        return ByteArray(32) { i ->
            ((h[i/4] ushr (24 - (i%4)*8)) and 0xFF).toByte()
        }
    }

    private fun padMessage(data: ByteArray): ByteArray {
        val msgLen = data.size
        val bitLen = msgLen.toLong() * 8
        val padLen = if (msgLen % 64 < 56) 56 - msgLen % 64 else 120 - msgLen % 64
        val totalLen = msgLen + padLen + 8
        val padded = ByteArray(totalLen)
        data.copyInto(padded)
        padded[msgLen] = 0x80.toByte()
        for (i in 0 until 8) {
            val shift = 56 - i * 8
            padded[totalLen - 8 + i] =
                ((bitLen ushr shift) and 0xFFL).toInt().toByte()
        }
        return padded
    }

    private fun rotr(value: Int, bits: Int): Int =
        (value ushr bits) or (value shl (32 - bits))
}
