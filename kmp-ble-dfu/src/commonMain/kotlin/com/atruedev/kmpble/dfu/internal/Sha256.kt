package com.atruedev.kmpble.dfu.internal

/**
 * Pure Kotlin SHA-256 implementation for MCUboot image verification.
 *
 * Follows FIPS 180-4. No platform dependencies — runs identically on all KMP targets.
 * Trade-off: slower than platform-native crypto (MessageDigest / CC_SHA256) but avoids
 * an expect/actual boundary. Acceptable because the hash is computed once per DFU
 * transfer. Consider an expect/actual with platform crypto if profiling shows a bottleneck.
 *
 * Allocation strategy: complete 64-byte blocks are processed directly from the source
 * array. Only the 1–2 padding tail blocks (max 128 bytes) are materialized in a
 * temporary buffer — no full-image copy regardless of firmware size.
 */
@OptIn(ExperimentalUnsignedTypes::class)
internal object Sha256 {

    private val K = uintArrayOf(
        0x428a2f98u, 0x71374491u, 0xb5c0fbcfu, 0xe9b5dba5u, 0x3956c25bu, 0x59f111f1u, 0x923f82a4u, 0xab1c5ed5u,
        0xd807aa98u, 0x12835b01u, 0x243185beu, 0x550c7dc3u, 0x72be5d74u, 0x80deb1feu, 0x9bdc06a7u, 0xc19bf174u,
        0xe49b69c1u, 0xefbe4786u, 0x0fc19dc6u, 0x240ca1ccu, 0x2de92c6fu, 0x4a7484aau, 0x5cb0a9dcu, 0x76f988dau,
        0x983e5152u, 0xa831c66du, 0xb00327c8u, 0xbf597fc7u, 0xc6e00bf3u, 0xd5a79147u, 0x06ca6351u, 0x14292967u,
        0x27b70a85u, 0x2e1b2138u, 0x4d2c6dfcu, 0x53380d13u, 0x650a7354u, 0x766a0abbu, 0x81c2c92eu, 0x92722c85u,
        0xa2bfe8a1u, 0xa81a664bu, 0xc24b8b70u, 0xc76c51a3u, 0xd192e819u, 0xd6990624u, 0xf40e3585u, 0x106aa070u,
        0x19a4c116u, 0x1e376c08u, 0x2748774cu, 0x34b0bcb5u, 0x391c0cb3u, 0x4ed8aa4au, 0x5b9cca4fu, 0x682e6ff3u,
        0x748f82eeu, 0x78a5636fu, 0x84c87814u, 0x8cc70208u, 0x90befffau, 0xa4506cebu, 0xbef9a3f7u, 0xc67178f2u,
    )

    private val H0 = uintArrayOf(
        0x6a09e667u, 0xbb67ae85u, 0x3c6ef372u, 0xa54ff53au,
        0x510e527fu, 0x9b05688cu, 0x1f83d9abu, 0x5be0cd19u,
    )

    fun digest(data: ByteArray): ByteArray {
        val h = H0.copyOf()
        val w = UIntArray(64)

        // Process all complete 64-byte blocks directly from the source — no copy.
        val completeBlocks = data.size / 64
        for (b in 0 until completeBlocks) {
            processBlock(h, w, data, b * 64)
        }

        // Build the padding tail: 1 block if the remainder fits (< 56 bytes), else 2.
        // Max allocation: 128 bytes, regardless of firmware size.
        val tailStart = completeBlocks * 64
        val remaining = data.size - tailStart
        val tailBlocks = if (remaining < 56) 1 else 2
        val tail = ByteArray(tailBlocks * 64)
        data.copyInto(tail, 0, tailStart, data.size)
        tail[remaining] = 0x80.toByte()
        val bitLen = data.size.toLong() * 8
        for (i in 0 until 8) tail[tail.size - 1 - i] = (bitLen shr (i * 8)).toByte()
        for (b in 0 until tailBlocks) processBlock(h, w, tail, b * 64)

        val result = ByteArray(32)
        for (i in 0 until 8) {
            result[i * 4] = (h[i] shr 24).toByte()
            result[i * 4 + 1] = (h[i] shr 16).toByte()
            result[i * 4 + 2] = (h[i] shr 8).toByte()
            result[i * 4 + 3] = h[i].toByte()
        }
        return result
    }

    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    fun digestHex(data: ByteArray): String {
        val hash = digest(data)
        val chars = CharArray(64)
        for (i in hash.indices) {
            val b = hash[i].toInt() and 0xFF
            chars[i * 2] = HEX_CHARS[b shr 4]
            chars[i * 2 + 1] = HEX_CHARS[b and 0x0F]
        }
        return String(chars)
    }

    private fun processBlock(h: UIntArray, w: UIntArray, data: ByteArray, blockStart: Int) {
        for (t in 0 until 16) {
            w[t] = ((data[blockStart + t * 4].toInt() and 0xFF).toUInt() shl 24) or
                ((data[blockStart + t * 4 + 1].toInt() and 0xFF).toUInt() shl 16) or
                ((data[blockStart + t * 4 + 2].toInt() and 0xFF).toUInt() shl 8) or
                (data[blockStart + t * 4 + 3].toInt() and 0xFF).toUInt()
        }
        for (t in 16 until 64) {
            w[t] = sigma1(w[t - 2]) + w[t - 7] + sigma0(w[t - 15]) + w[t - 16]
        }

        var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
        var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]

        for (t in 0 until 64) {
            val t1 = hh + bigSigma1(e) + ch(e, f, g) + K[t] + w[t]
            val t2 = bigSigma0(a) + maj(a, b, c)
            hh = g; g = f; f = e; e = d + t1
            d = c; c = b; b = a; a = t1 + t2
        }

        h[0] += a; h[1] += b; h[2] += c; h[3] += d
        h[4] += e; h[5] += f; h[6] += g; h[7] += hh
    }

    private fun rotr(x: UInt, n: Int): UInt = (x shr n) or (x shl (32 - n))
    private fun ch(x: UInt, y: UInt, z: UInt): UInt = (x and y) xor (x.inv() and z)
    private fun maj(x: UInt, y: UInt, z: UInt): UInt = (x and y) xor (x and z) xor (y and z)
    private fun bigSigma0(x: UInt): UInt = rotr(x, 2) xor rotr(x, 13) xor rotr(x, 22)
    private fun bigSigma1(x: UInt): UInt = rotr(x, 6) xor rotr(x, 11) xor rotr(x, 25)
    private fun sigma0(x: UInt): UInt = rotr(x, 7) xor rotr(x, 18) xor (x shr 3)
    private fun sigma1(x: UInt): UInt = rotr(x, 17) xor rotr(x, 19) xor (x shr 10)
}
