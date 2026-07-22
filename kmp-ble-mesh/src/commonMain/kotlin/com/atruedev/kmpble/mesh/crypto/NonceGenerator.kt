package com.atruedev.kmpble.mesh.crypto

/**
 * Nonce generation for each BLE Mesh protocol layer.
 *
 * BLE Mesh uses AES-128-CCM with 13-byte nonces. Each protocol layer
 * constructs its nonce differently to prevent cross-layer nonce reuse,
 * which would catastrophically break security.
 *
 * All nonces are 13 bytes with the format:
 * ```
 * [NonceType(1)] [Layer-specific fields(8)] [SRC(2)] [DST(2)]
 * ```
 *
 * The NonceType byte ensures nonces from different layers can never collide,
 * even if all other fields are identical.
 */
internal object NonceGenerator {

    // Nonce type codes
    private const val NETWORK_NONCE: Byte = 0x00
    private const val APPLICATION_NONCE: Byte = 0x01
    private const val DEVICE_NONCE: Byte = 0x02
    private const val PROXY_NONCE: Byte = 0x03

    /**
     * Generate the 13-byte nonce for Network Layer encryption.
     *
     * Network nonce format:
     * ```
     * [0x00(1)] [CTL(1)] [TTL(1)] [SEQ(3)] [SRC(2)] [DST(2)] [IVIndex(4)]
     * ```
     * Note: CTL + TTL fields are packed and the total is 13 bytes.
     */
    fun networkNonce(
        ctl: Int,
        ttl: Int,
        seq: UInt,
        src: Int,
        dst: Int,
        ivIndex: UInt,
    ): ByteArray {
        val nonce = ByteArray(13)
        nonce[0] = NETWORK_NONCE
        // Nonce bytes 1-8: CTL/TTL/SEQ padded
        nonce[1] = ((ctl and 1) or ((ttl and 0x7F) shl 1)).toByte()
        nonce[2] = (seq.toInt() and 0xFF).toByte()
        nonce[3] = ((seq.toInt() shr 8) and 0xFF).toByte()
        nonce[4] = ((seq.toInt() shr 16) and 0xFF).toByte()
        nonce[5] = 0x00
        nonce[6] = 0x00
        nonce[7] = 0x00
        nonce[8] = 0x00
        // Source (2 bytes, little-endian)
        nonce[9] = (src and 0xFF).toByte()
        nonce[10] = ((src shr 8) and 0xFF).toByte()
        // Destination (2 bytes, big-endian - spec convention)
        nonce[11] = ((dst shr 8) and 0xFF).toByte()
        nonce[12] = (dst and 0xFF).toByte()
        return nonce
    }

    /**
     * Generate the 13-byte nonce for Upper Transport Layer (application) encryption.
     *
     * Application nonce format:
     * ```
     * [0x01(1)] [ASEQ(3)] [DST(2)] [SRC(2)] [IVIndex(4)] [SZMIC(1)]
     * ```
     */
    fun applicationNonce(
        seq: UInt,
        src: Int,
        dst: Int,
        ivIndex: UInt,
        szmic: Int,
    ): ByteArray {
        val nonce = ByteArray(13)
        nonce[0] = APPLICATION_NONCE
        // ASEQ (3 bytes, little-endian)
        nonce[1] = (seq.toInt() and 0xFF).toByte()
        nonce[2] = ((seq.toInt() shr 8) and 0xFF).toByte()
        nonce[3] = ((seq.toInt() shr 16) and 0xFF).toByte()
        // DST (2 bytes, big-endian)
        nonce[4] = ((dst shr 8) and 0xFF).toByte()
        nonce[5] = (dst and 0xFF).toByte()
        // SRC (2 bytes, big-endian)
        nonce[6] = ((src shr 8) and 0xFF).toByte()
        nonce[7] = (src and 0xFF).toByte()
        // IVIndex (4 bytes, big-endian)
        nonce[8] = ((ivIndex.toInt() shr 24) and 0xFF).toByte()
        nonce[9] = ((ivIndex.toInt() shr 16) and 0xFF).toByte()
        nonce[10] = ((ivIndex.toInt() shr 8) and 0xFF).toByte()
        nonce[11] = (ivIndex.toInt() and 0xFF).toByte()
        // SZMIC (1 byte)
        nonce[12] = szmic.toByte()
        return nonce
    }

    /**
     * Generate the 13-byte nonce for Device Key (configuration) encryption.
     *
     * Device nonce format:
     * ```
     * [0x02(1)] [ASEQ(3)] [DST(2)] [SRC(2)] [IVIndex(4)] [SZMIC(1)]
     * ```
     * Same format as application nonce but with different NonceType.
     */
    fun deviceNonce(
        seq: UInt,
        src: Int,
        dst: Int,
        ivIndex: UInt,
        szmic: Int,
    ): ByteArray {
        val nonce = applicationNonce(seq, src, dst, ivIndex, szmic)
        nonce[0] = DEVICE_NONCE
        return nonce
    }

    /**
     * Generate the 13-byte nonce for Proxy protocol encryption.
     *
     * Proxy nonce format:
     * ```
     * [0x03(1)] [Pad(5)] [Seq(3)] [SRC(2)] [DST(2)]
     * ```
     */
    fun proxyNonce(
        seq: UInt,
        src: Int,
        dst: Int,
    ): ByteArray {
        val nonce = ByteArray(13)
        nonce[0] = PROXY_NONCE
        // Padding (5 bytes)
        // Pad
        // SEQ (3 bytes, little-endian)
        nonce[6] = (seq.toInt() and 0xFF).toByte()
        nonce[7] = ((seq.toInt() shr 8) and 0xFF).toByte()
        nonce[8] = ((seq.toInt() shr 16) and 0xFF).toByte()
        // SRC (2 bytes, big-endian)
        nonce[9] = ((src shr 8) and 0xFF).toByte()
        nonce[10] = (src and 0xFF).toByte()
        // DST (2 bytes, big-endian)
        nonce[11] = ((dst shr 8) and 0xFF).toByte()
        nonce[12] = (dst and 0xFF).toByte()
        return nonce
    }
}
