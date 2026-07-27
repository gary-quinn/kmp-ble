package com.atruedev.kmpble.mesh.crypto

/**
 * Pure Kotlin AES-128-CCM (Counter with CBC-MAC) implementation.
 *
 * CCM mode is defined in NIST SP 800-38C. It combines CTR mode for
 * encryption with CBC-MAC for authentication.
 *
 * This implementation supports variable MIC sizes (4 or 8 bytes) as
 * required by the BLE Mesh specification. It operates on a single
 * AES-128 block cipher provided by [CryptoEngine].
 *
 * ## Nonce format (BLE Mesh, 13 bytes)
 *
 * ```
 * [NonceType(1)] [sequence/payload(8)] [src(2)] [dst(2)]
 * ```
 *
 * CCM requires a 15-byte nonce internally; the 13-byte mesh nonce is
 * padded with 2 zero bytes at the end.
 *
 * ## Reference
 *
 * - NIST SP 800-38C: Recommendation for Block Cipher Modes of Operation: CCM
 * - BLE Mesh Profile v1.1, Section 3.8: Cryptographic toolbox
 */
internal object AesCcm {
    /** Maximum plaintext length for CCM (BLE Mesh PDU limit is much smaller). */
    private const val MAX_PLAINTEXT_LEN = 384 // more than enough for mesh

    /**
     * Encrypt plaintext and produce MIC using AES-128-CCM.
     *
     * @param key 16-byte AES key.
     * @param nonce 13-byte nonce from the mesh protocol layer.
     * @param plaintext Data to encrypt + authenticate.
     * @param aad Additional Authenticated Data (authenticated but not encrypted).
     * @param micSize Size of the MIC in bytes (4 or 8 for mesh).
     * @return [CcmResult] with ciphertext and MIC.
     */
    fun encrypt(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray,
        micSize: Int,
    ): CcmResult {
        require(key.size == 16) { "AES key must be 16 bytes" }
        require(nonce.size == 13) { "CCM nonce must be 13 bytes" }
        require(micSize in setOf(4, 8)) { "CCM MIC size must be 4 or 8 bytes" }

        // Step 1: Format the B0 block
        val b0 = formatB0(nonce, plaintext.size, aad.isNotEmpty(), micSize)

        // Step 2: CBC-MAC over B0 || AAD || plaintext
        val mac = computeCbcMac(key, b0, aad, plaintext, micSize)

        // Step 3: CTR mode encryption over plaintext || MAC
        val ciphertext = ctrEncrypt(key, nonce, 1, plaintext)
        val encryptedMac = ctrEncrypt(key, nonce, 0, mac)

        return CcmResult(ciphertext, encryptedMac)
    }

    /**
     * Decrypt ciphertext and verify MIC using AES-128-CCM.
     *
     * @return Plaintext if MIC verifies, or null if authentication fails.
     */
    fun decrypt(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        aad: ByteArray,
        mic: ByteArray,
    ): ByteArray? {
        require(key.size == 16) { "AES key must be 16 bytes" }
        require(nonce.size == 13) { "CCM nonce must be 13 bytes" }

        val micSize = mic.size

        // Step 1: CTR decrypt ciphertext
        val plaintext = ctrEncrypt(key, nonce, 1, ciphertext)

        // Step 2: CTR decrypt the MIC to get the original MAC
        val mac = ctrEncrypt(key, nonce, 0, mic)

        // Step 3: Recompute CBC-MAC and compare
        val b0 = formatB0(nonce, plaintext.size, aad.isNotEmpty(), micSize)
        val expectedMac = computeCbcMac(key, b0, aad, plaintext, micSize)

        if (!mac.contentEquals(expectedMac)) return null // MIC mismatch
        return plaintext
    }

    /**
     * Format the B0 block for CCM.
     *
     * B0 format: [Flags(1)] [Nonce(13)] [MessageLength(2)]
     * Flags = (Adata << 6) | ((micSize-2)/2 << 3) | (nonceLen-1)
     * For mesh: nonceLen=13, so (13-1) = 12 = 0xC in the flags.
     */
    private fun formatB0(
        nonce: ByteArray,
        messageLen: Int,
        hasAad: Boolean,
        micSize: Int,
    ): ByteArray {
        val b0 = ByteArray(16)
        // Flags: (adata?1:0) << 6 | ((micSize-2)/2) << 3 | (13-1)
        var flags = 0
        if (hasAad) flags = flags or (1 shl 6)
        flags = flags or (((micSize - 2) / 2) shl 3)
        flags = flags or (13 - 1)
        b0[0] = flags.toByte()

        // Nonce (13 bytes)
        nonce.copyInto(b0, 1, 0, 13)

        // Message length (2 bytes, big-endian)
        b0[14] = ((messageLen shr 8) and 0xFF).toByte()
        b0[15] = (messageLen and 0xFF).toByte()

        return b0
    }

    /**
     * Compute CBC-MAC over [B0 || AAD || plaintext].
     */
    private fun computeCbcMac(
        key: ByteArray,
        b0: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
        micSize: Int,
    ): ByteArray {
        var x = ByteArray(16) // Initialization vector is zero

        // Process B0
        x = CryptoEngine.aes128EcbEncrypt(key, xor16(x, b0))

        // Process AAD with length prefix
        if (aad.isNotEmpty()) {
            x = processAad(key, x, aad)
        }

        // Process plaintext
        var offset = 0
        while (offset < plaintext.size) {
            val block = ByteArray(16)
            val remaining = minOf(16, plaintext.size - offset)
            plaintext.copyInto(block, 0, offset, offset + remaining)
            x = CryptoEngine.aes128EcbEncrypt(key, xor16(x, block))
            offset += 16
        }

        return x.copyOf(micSize)
    }

    /**
     * Process AAD for CBC-MAC: [length(2)] [aad] [padding(0)]
     */
    private fun processAad(
        key: ByteArray,
        initialX: ByteArray,
        aad: ByteArray,
    ): ByteArray {
        var x = initialX

        // AAD length as 2 bytes big-endian
        val aadLenBlock = ByteArray(16)
        aadLenBlock[0] = ((aad.size shr 8) and 0xFF).toByte()
        aadLenBlock[1] = (aad.size and 0xFF).toByte()
        aad.copyInto(aadLenBlock, 2, 0, minOf(14, aad.size))
        x = CryptoEngine.aes128EcbEncrypt(key, xor16(x, aadLenBlock))

        // Remaining AAD blocks
        var aadOffset = 14
        while (aadOffset < aad.size) {
            val block = ByteArray(16)
            val remaining = minOf(16, aad.size - aadOffset)
            aad.copyInto(block, 0, aadOffset, aadOffset + remaining)
            x = CryptoEngine.aes128EcbEncrypt(key, xor16(x, block))
            aadOffset += 16
        }

        return x
    }

    /**
     * CTR mode encryption.
     *
     * Generates a keystream by encrypting counter blocks, then XORs with
     * the input to produce output. Encryption and decryption are identical
     * in CTR mode.
     *
     * @param key 16-byte AES key.
     * @param nonce 13-byte nonce.
     * @param startCounter Starting counter value (0 for MAC, 1 for payload).
     * @param input Data to XOR with keystream.
     * @return Keystream XOR input.
     */
    private fun ctrEncrypt(
        key: ByteArray,
        nonce: ByteArray,
        startCounter: Int,
        input: ByteArray,
    ): ByteArray {
        val output = ByteArray(input.size)
        val counterBlock = ByteArray(16)

        // Format: [Flags(1)] [Nonce(13)] [Counter(2)]
        counterBlock[0] = 0x01.toByte() // Flags: L=2 (2-byte counter)
        nonce.copyInto(counterBlock, 1, 0, 13)

        var counter = startCounter
        var offset = 0
        while (offset < input.size) {
            counterBlock[14] = ((counter shr 8) and 0xFF).toByte()
            counterBlock[15] = (counter and 0xFF).toByte()
            counter++

            val keystream = CryptoEngine.aes128EcbEncrypt(key, counterBlock)
            val remaining = minOf(16, input.size - offset)
            for (i in 0 until remaining) {
                output[offset + i] = (input[offset + i].toInt() xor
                    (keystream[i].toInt() and 0xFF)).toByte()
            }
            offset += 16
        }

        return output
    }

    /** XOR two 16-byte arrays. */
    private fun xor16(a: ByteArray, b: ByteArray): ByteArray {
        val result = ByteArray(16)
        for (i in 0 until 16) {
            result[i] = (a[i].toInt() xor b[i].toInt()).toByte()
        }
        return result
    }
}
