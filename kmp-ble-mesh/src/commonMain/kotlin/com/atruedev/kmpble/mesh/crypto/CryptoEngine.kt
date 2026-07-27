package com.atruedev.kmpble.mesh.crypto

/**
 * Platform crypto primitives for BLE Mesh.
 *
 * BLE Mesh requires a specific set of cryptographic primitives. This expect
 * object provides platform-specific implementations using hardware-backed
 * crypto where available.
 *
 * All methods operate on [ByteArray] with standard crypto conventions.
 * Keys are 16 bytes (AES-128). Nonces vary by protocol layer.
 */
internal expect object CryptoEngine {
    /**
     * AES-128-ECB encrypt a single 16-byte block.
     *
     * Used for the privacy layer obfuscation and as a building block
     * for CCM mode.
     *
     * @param key 16-byte AES key.
     * @param data 16-byte plaintext block.
     * @return 16-byte ciphertext block.
     */
    fun aes128EcbEncrypt(key: ByteArray, data: ByteArray): ByteArray

    /**
     * AES-128-CCM encrypt with authentication.
     *
     * Counter with CBC-MAC mode. Used for network and transport layer
     * encryption with 32-bit or 64-bit MIC.
     *
     * @param key 16-byte AES key.
     * @param nonce 13-byte CCM nonce.
     * @param plaintext Data to encrypt.
     * @param aad Additional Authenticated Data (not encrypted, but authenticated).
     * @param micSize Size of the MIC in bytes (4 or 8).
     * @return [CcmResult] containing ciphertext and MIC.
     */
    fun aesCcmEncrypt(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray,
        micSize: Int,
    ): CcmResult

    /**
     * AES-128-CCM decrypt with authentication verification.
     *
     * @param key 16-byte AES key.
     * @param nonce 13-byte CCM nonce.
     * @param ciphertext Encrypted data (without MIC appended).
     * @param aad Additional Authenticated Data.
     * @param mic The MIC to verify.
     * @return Plaintext if MIC verifies, or null if authentication fails.
     */
    fun aesCcmDecrypt(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        aad: ByteArray,
        mic: ByteArray,
    ): ByteArray?

    /**
     * AES-128-CMAC (RFC 4493).
     *
     * Used for key derivation functions (k1, k2, k3) and provisioning
     * confirmation calculation.
     *
     * @param key 16-byte AES key.
     * @param data Data to authenticate.
     * @return 16-byte CMAC tag.
     */
    fun aesCmac(key: ByteArray, data: ByteArray): ByteArray

    /**
     * SHA-256 hash.
     *
     * @param data Data to hash.
     * @return 32-byte hash digest.
     */
    fun sha256(data: ByteArray): ByteArray

    /**
     * ECDH P-256 key pair generation.
     *
     * Used during provisioning for elliptic-curve Diffie-Hellman key exchange.
     *
     * @return A new P-256 key pair.
     */
    fun ecdhP256GenerateKeyPair(): EcdhKeyPair

    /**
     * ECDH P-256 shared secret computation.
     *
     * @param privateKey 32-byte private key.
     * @param publicKey 64-byte uncompressed public key (X || Y).
     * @return 32-byte shared secret.
     */
    fun ecdhP256SharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray

    /**
     * Generate cryptographically secure random bytes.
     *
     * Used for nonces, key generation, and provisioning random values.
     *
     * @param size Number of random bytes to generate.
     * @return Random bytes.
     */
    fun secureRandomBytes(size: Int): ByteArray
}

/** Result of AES-128-CCM encryption. */
internal data class CcmResult(
    /** Encrypted ciphertext (same length as plaintext). */
    val ciphertext: ByteArray,
    /** Message Integrity Check (4 or 8 bytes). */
    val mic: ByteArray,
)

/** ECDH P-256 key pair. */
internal data class EcdhKeyPair(
    /** 32-byte private key. */
    val privateKey: ByteArray,
    /** 64-byte uncompressed public key (X || Y, each 32 bytes). */
    val publicKey: ByteArray,
)
