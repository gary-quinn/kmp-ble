package com.atruedev.kmpble.mesh.crypto

/**
 * Nonce generation for each BLE Mesh protocol layer.
 *
 * BLE Mesh uses AES-128-CCM with 13-byte nonces. Each protocol layer
 * constructs its nonce differently to prevent cross-layer nonce reuse,
 * which would catastrophically break security.
 *
 * ## Nonce Formats (BLE Mesh Profile v1.1, Section 3.8.5)
 *
 * All nonces are 13 bytes, big-endian for multi-byte fields.
 *
 * **Network Nonce (0x00):**
 * ```
 * [0x00(1)] [CTL|TTL(1)] [SEQ(3)] [SRC(2)] [DST(2)] [IVIndex(4)]
 * ```
 *
 * **Application Nonce (0x01):**
 * ```
 * [0x01(1)] [ASZMIC|RFU(1)] [SEQ(3)] [SRC(2)] [DST(2)] [IVIndex(4)]
 * ```
 *
 * **Device Nonce (0x02):** Same format as Application but type 0x02.
 *
 * **Proxy Nonce (0x03):**
 * ```
 * [0x03(1)] [Pad(5)] [SEQ(3)] [SRC(2)] [DST(2)]
 * ```
 */
internal object NonceGenerator {

    // Nonce type codes (Section 3.8.5)
    private const val NETWORK_NONCE: Byte = 0x00
    private const val APPLICATION_NONCE: Byte = 0x01
    private const val DEVICE_NONCE: Byte = 0x02
    private const val PROXY_NONCE: Byte = 0x03

    /**
     * Generate the 13-byte nonce for Network Layer encryption.
     *
     * Network nonce format (Section 3.8.5.1):
     * ```
     * [0x00(1)] [CTL|TTL(1)] [SEQ(3)] [SRC(2)] [DST(2)] [IVIndex(4)]
     * ```
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
        // Byte 1: CTL (bit 7) | TTL (bits 0-6)
        nonce[1] = (((ctl and 1) shl 7) or (ttl and 0x7F)).toByte()
        // Bytes 2-4: SEQ (24-bit, big-endian)
        val seqInt = seq.toInt()
        nonce[2] = ((seqInt shr 16) and 0xFF).toByte()
        nonce[3] = ((seqInt shr 8) and 0xFF).toByte()
        nonce[4] = (seqInt and 0xFF).toByte()
        // Bytes 5-6: SRC (16-bit, big-endian)
        nonce[5] = ((src shr 8) and 0xFF).toByte()
        nonce[6] = (src and 0xFF).toByte()
        // Bytes 7-8: DST (16-bit, big-endian)
        nonce[7] = ((dst shr 8) and 0xFF).toByte()
        nonce[8] = (dst and 0xFF).toByte()
        // Bytes 9-12: IV Index (32-bit, big-endian)
        val iv = ivIndex.toInt()
        nonce[9] = ((iv shr 24) and 0xFF).toByte()
        nonce[10] = ((iv shr 16) and 0xFF).toByte()
        nonce[11] = ((iv shr 8) and 0xFF).toByte()
        nonce[12] = (iv and 0xFF).toByte()
        return nonce
    }

    /**
     * Generate the 13-byte nonce for Upper Transport Layer (application)
     * encryption.
     *
     * Application nonce format (Section 3.8.5.2):
     * ```
     * [0x01(1)] [ASZMIC|RFU(1)] [SEQ(3)] [SRC(2)] [DST(2)] [IVIndex(4)]
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
        // Byte 1: ASZMIC (bit 7) | RFU (bits 0-6, all zero)
        nonce[1] = ((szmic and 1) shl 7).toByte()
        // Bytes 2-4: SEQ (24-bit, big-endian)
        val seqInt = seq.toInt()
        nonce[2] = ((seqInt shr 16) and 0xFF).toByte()
        nonce[3] = ((seqInt shr 8) and 0xFF).toByte()
        nonce[4] = (seqInt and 0xFF).toByte()
        // Bytes 5-6: SRC (16-bit, big-endian)
        nonce[5] = ((src shr 8) and 0xFF).toByte()
        nonce[6] = (src and 0xFF).toByte()
        // Bytes 7-8: DST (16-bit, big-endian)
        nonce[7] = ((dst shr 8) and 0xFF).toByte()
        nonce[8] = (dst and 0xFF).toByte()
        // Bytes 9-12: IV Index (32-bit, big-endian)
        val iv = ivIndex.toInt()
        nonce[9] = ((iv shr 24) and 0xFF).toByte()
        nonce[10] = ((iv shr 16) and 0xFF).toByte()
        nonce[11] = ((iv shr 8) and 0xFF).toByte()
        nonce[12] = (iv and 0xFF).toByte()
        return nonce
    }

    /**
     * Generate the 13-byte nonce for Device Key (configuration) encryption.
     *
     * Device nonce format: same as Application Nonce but with NonceType 0x02.
     * Per Section 3.8.5.3.
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
     * Proxy nonce format (Section 3.8.5.4):
     * ```
     * [0x03(1)] [Pad(5)] [SEQ(3)] [SRC(2)] [DST(2)]
     * ```
     */
    fun proxyNonce(
        seq: UInt,
        src: Int,
        dst: Int,
    ): ByteArray {
        val nonce = ByteArray(13)
        nonce[0] = PROXY_NONCE
        // Bytes 1-5: Padding (zeros)
        // Bytes 6-8: SEQ (24-bit, big-endian)
        val seqInt = seq.toInt()
        nonce[6] = ((seqInt shr 16) and 0xFF).toByte()
        nonce[7] = ((seqInt shr 8) and 0xFF).toByte()
        nonce[8] = (seqInt and 0xFF).toByte()
        // Bytes 9-10: SRC (16-bit, big-endian)
        nonce[9] = ((src shr 8) and 0xFF).toByte()
        nonce[10] = (src and 0xFF).toByte()
        // Bytes 11-12: DST (16-bit, big-endian)
        nonce[11] = ((dst shr 8) and 0xFF).toByte()
        nonce[12] = (dst and 0xFF).toByte()
        return nonce
    }
}
