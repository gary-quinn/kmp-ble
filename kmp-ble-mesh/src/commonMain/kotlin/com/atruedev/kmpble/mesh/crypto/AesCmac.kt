package com.atruedev.kmpble.mesh.crypto

/**
 * Pure Kotlin AES-128-CMAC implementation (RFC 4493).
 *
 * CMAC (Cipher-based Message Authentication Code) is used extensively in
 * BLE Mesh for key derivation functions (k1, k2, k3, s1) and provisioning
 * confirmation calculation.
 *
 * This implementation uses the AES-128 block cipher from [CryptoEngine].
 *
 * ## Reference
 * - RFC 4493: The AES-CMAC Algorithm
 * - NIST SP 800-38B: Recommendation for Block Cipher Modes of Operation: CMAC
 * - BLE Mesh Profile v1.1, Section 3.8: Cryptographic toolbox
 */
internal object AesCmac {
    /** AES block size in bytes. */
    private const val BLOCK_SIZE = 16

    /** Subkey generation constants from RFC 4493. */
    private val Rb = byteArrayOf(
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    ).apply { this[15] = 0x87.toByte() } // R_128 constant

    /**
     * Compute AES-128-CMAC for the given key and message.
     *
     * @param key 16-byte AES key.
     * @param data Input data to authenticate.
     * @return 16-byte CMAC tag.
     */
    fun compute(key: ByteArray, data: ByteArray): ByteArray {
        require(key.size == BLOCK_SIZE) { "CMAC key must be 16 bytes" }

        // Step 1: Generate subkeys K1 and K2
        val l = CryptoEngine.aes128EcbEncrypt(key, ByteArray(BLOCK_SIZE))
        val k1 = generateSubkey(l)
        val k2 = generateSubkey(k1)

        // Step 2: Process message blocks
        val n = if (data.isEmpty()) 1 else
            (data.size + BLOCK_SIZE - 1) / BLOCK_SIZE

        val isCompleteBlock = data.isNotEmpty() &&
            data.size % BLOCK_SIZE == 0

        var x = ByteArray(BLOCK_SIZE) // Initial state = 0

        // Process complete blocks
        for (i in 0 until (if (isCompleteBlock) n - 1 else n)) {
            val block = ByteArray(BLOCK_SIZE)
            data.copyInto(block, 0, i * BLOCK_SIZE,
                minOf((i + 1) * BLOCK_SIZE, data.size))
            x = CryptoEngine.aes128EcbEncrypt(key, xor16(x, block))
        }

        // Process last block
        val lastBlock = ByteArray(BLOCK_SIZE)
        if (data.isNotEmpty()) {
            val startOffset = (n - 1) * BLOCK_SIZE
            data.copyInto(lastBlock, 0, startOffset,
                minOf(startOffset + BLOCK_SIZE, data.size))
        }

        // Apply K1 or K2 padding
        val paddedBlock = if (isCompleteBlock && data.isNotEmpty()) {
            xor16(lastBlock, k1)
        } else {
            // Pad with 0x80 followed by zeros
            val padLen = if (data.isEmpty()) BLOCK_SIZE else data.size % BLOCK_SIZE
            lastBlock[padLen] = 0x80.toByte()
            xor16(lastBlock, k2)
        }

        x = CryptoEngine.aes128EcbEncrypt(key, xor16(x, paddedBlock))

        return x // 16-byte tag
    }

    /**
     * Generate a CMAC subkey (K1 or K2) from the intermediate value L.
     *
     * K1 = (L << 1) XOR Rb if MSB(L) == 1, else (L << 1)
     * K2 = (K1 << 1) XOR Rb if MSB(K1) == 1, else (K1 << 1)
     */
    private fun generateSubkey(input: ByteArray): ByteArray {
        val shifted = leftShift1(input)
        return if ((input[0].toInt() and 0x80) != 0) {
            xor16(shifted, Rb)
        } else {
            shifted
        }
    }

    /** Left-shift a byte array by 1 bit. */
    private fun leftShift1(input: ByteArray): ByteArray {
        val result = ByteArray(input.size)
        var carry = 0
        for (i in input.indices.reversed()) {
            val value = (input[i].toInt() and 0xFF)
            result[i] = ((value shl 1) or carry).toByte()
            carry = (value ushr 7) and 1
        }
        return result
    }

    /** XOR two 16-byte arrays. */
    private fun xor16(a: ByteArray, b: ByteArray): ByteArray {
        val result = ByteArray(BLOCK_SIZE)
        for (i in 0 until BLOCK_SIZE) {
            result[i] = (a[i].toInt() xor b[i].toInt()).toByte()
        }
        return result
    }
}
