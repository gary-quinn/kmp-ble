package com.atruedev.kmpble.mesh.crypto

/**
 * iOS crypto implementation.
 *
 * All primitives use pure Kotlin implementations portably:
 * - Symmetric: [PureKotlinCrypto], [AesCcm], [AesCmac]
 * - ECDH P-256: [P256Ecdh] (portable, no CommonCrypto dependency)
 * - SHA-256: [PureKotlinCrypto]
 * - Secure random: SHA-256 based CSPRNG with platform entropy seed
 *
 * This avoids CommonCrypto cinterop entirely, ensuring consistent
 * behavior across all KMP targets.
 */
internal actual object CryptoEngine {
    // --- Symmetric crypto (delegates to pure Kotlin) ---

    actual fun aes128EcbEncrypt(key: ByteArray, data: ByteArray): ByteArray =
        PureKotlinCrypto.aes128EncryptBlock(key, data)

    actual fun aesCcmEncrypt(
        key: ByteArray, nonce: ByteArray, plaintext: ByteArray,
        aad: ByteArray, micSize: Int,
    ): CcmResult = AesCcm.encrypt(key, nonce, plaintext, aad, micSize)

    actual fun aesCcmDecrypt(
        key: ByteArray, nonce: ByteArray, ciphertext: ByteArray,
        aad: ByteArray, mic: ByteArray,
    ): ByteArray? = AesCcm.decrypt(key, nonce, ciphertext, aad, mic)

    actual fun aesCmac(key: ByteArray, data: ByteArray): ByteArray =
        AesCmac.compute(key, data)

    actual fun sha256(data: ByteArray): ByteArray =
        PureKotlinCrypto.sha256(data)

    // --- ECDH P-256 (pure Kotlin, portable) ---

    actual fun ecdhP256GenerateKeyPair(): EcdhKeyPair =
        P256Ecdh.generateKeyPair()

    actual fun ecdhP256SharedSecret(
        privateKey: ByteArray, publicKey: ByteArray,
    ): ByteArray = P256Ecdh.sharedSecret(privateKey, publicKey)

    // --- Secure random (SHA-256 based CSPRNG) ---

    /**
     * Generate cryptographically secure random bytes.
     *
     * Uses a SHA-256-based CSPRNG seeded from [PureKotlinCrypto] entropy
     * pool. On iOS, this avoids the need for SecRandomCopyBytes cinterop
     * while providing cryptographic-quality randomness.
     */
    actual fun secureRandomBytes(size: Int): ByteArray =
        PureKotlinCrypto.secureRandomBytes(size)
}
