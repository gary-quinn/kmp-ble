package com.atruedev.kmpble.mesh.crypto

/**
 * Key derivation functions defined by the BLE Mesh Profile specification.
 *
 * All key derivation in BLE Mesh is based on AES-CMAC. The SIG defines
 * four key derivation functions:
 *
 * - **s1(M)**: Derive a 16-byte salt from a UTF-8 string.
 * - **k1(N, salt, P)**: Derive a 16-byte key from parameters.
 * - **k2(N, P)**: Derive NID, encryption key, and privacy key from a NetKey.
 * - **k3(N)**: Derive a 64-bit value from a NetKey.
 *
 * ## Reference
 * BLE Mesh Profile v1.1, Section 3.8: Cryptographic toolbox
 */
internal object KeyDerivation {
    /** Salt for the s1 function (zero-padded 16 bytes). */
    private val S1_SALT = ByteArray(16)

    /** Salt for the k1 function (identity key derivation). */
    val IDENTITY_SALT: ByteArray = byteArrayOf(
        0x6E, 0x6B, 0x69, 0x6B, // "nkik" in ASCII
    ).copyOf(16) // zero-padded to 16 bytes

    /** Salt for the k1 function (beacon key derivation). */
    val BEACON_SALT: ByteArray = byteArrayOf(
        0x6E, 0x6B, 0x62, 0x6B, // "nkbk" in ASCII
    ).copyOf(16)

    /** Salt for the virtual address derivation. */
    val VTAD_SALT: ByteArray = byteArrayOf(
        0x76, 0x74, 0x61, 0x64, // "vtad" in ASCII
    ).copyOf(16)

    /**
     * s1: Generate salt value from a UTF-8 string M.
     *
     * s1(M) = AES-CMAC(zeroKey, M)
     *
     * @param m The UTF-8 string to hash.
     * @return 16-byte salt value.
     */
    fun s1(m: String): ByteArray =
        AesCmac.compute(S1_SALT, m.encodeToByteArray())

    /**
     * k1: Key derivation function.
     *
     * k1(N, salt, P) = AES-CMAC(AES-CMAC(salt, N), P)
     *
     * Used for identity key, beacon key, and provisioning session keys.
     *
     * @param n 16-byte input (e.g., NetKey).
     * @param salt 16-byte salt (e.g., IDENTITY_SALT, BEACON_SALT).
     * @param p Variable-length parameter P.
     * @return 16-byte derived key.
     */
    fun k1(n: ByteArray, salt: ByteArray, p: ByteArray): ByteArray {
        val t = AesCmac.compute(salt, n)
        return AesCmac.compute(t, p)
    }

    /**
     * k2: Network key derivation.
     *
     * Derives the NID, encryption key, and privacy key from a NetKey.
     *
     * k2(NetKey, P) produces:
     * - T = AES-CMAC(NetKey, P)
     * - NID = T[15] & 0x7F (7-bit value)
     * - EncryptionKey = T[0..15]
     * - PrivacyKey = AES-CMAC(NetKey, T[0..15] || 0x01)
     *
     * @param n 16-byte NetKey.
     * @param p Variable-length parameter P (e.g., 0x00 for encryption, 0x01 for privacy).
     * @return Triple of (NID, EncryptionKey, PrivacyKey).
     */
    fun k2(n: ByteArray, p: ByteArray): K2Result {
        val t = AesCmac.compute(n, p)
        val nid = t[15].toInt() and 0x7F
        val encryptionKey = t.copyOf(16)

        // PrivacyKey = AES-CMAC(NetKey, encryptionKey || 0x01)
        val privacyInput = encryptionKey + byteArrayOf(0x01)
        val privacyKey = AesCmac.compute(n, privacyInput)

        return K2Result(nid, encryptionKey, privacyKey)
    }

    /**
     * k3: Derive a 64-bit value.
     *
     * k3(N) = AES-CMAC(N, 0x69643a6e) & 0x00000000FFFFFFFF
     *
     * Used for the proxy identity resolution.
     *
     * @param n 16-byte input.
     * @return 64-bit value as a Long.
     */
    fun k3(n: ByteArray): Long {
        val p = byteArrayOf(0x69, 0x64, 0x3a, 0x6E) // "id:n" in ASCII
        val t = AesCmac.compute(n, p)
        return ((t[12].toLong() and 0xFF) shl 24) or
            ((t[13].toLong() and 0xFF) shl 16) or
            ((t[14].toLong() and 0xFF) shl 8) or
            (t[15].toLong() and 0xFF)
    }

    /**
     * Generate the Identity Key for proxy advertising.
     *
     * IdentityKey = k1(NetKey, IDENTITY_SALT, DeviceKey)
     */
    fun generateIdentityKey(netKey: ByteArray, deviceKey: ByteArray): ByteArray =
        k1(netKey, IDENTITY_SALT, deviceKey)

    /**
     * Generate the Beacon Key for secure network beacons.
     *
     * BeaconKey = k1(NetKey, BEACON_SALT, 0x00)
     */
    fun generateBeaconKey(netKey: ByteArray): ByteArray =
        k1(netKey, BEACON_SALT, byteArrayOf(0x00))

    /**
     * Derive a virtual address hash from a UUID label.
     *
     * VirtualAddress = AES-CMAC(VTAD_SALT, labelBytes)[14..15]
     */
    fun deriveVirtualAddress(labelBytes: ByteArray): UShort {
        val hash = AesCmac.compute(VTAD_SALT, labelBytes)
        val value = ((hash[14].toInt() and 0xFF) shl 8) or
            (hash[15].toInt() and 0xFF)
        return value.toUShort()
    }
}

/** Result of the k2 key derivation function. */
internal data class K2Result(
    /** Network ID — 7-bit value identifying the NetKey. */
    val nid: Int,
    /** 16-byte encryption key. */
    val encryptionKey: ByteArray,
    /** 16-byte privacy key. */
    val privacyKey: ByteArray,
)
