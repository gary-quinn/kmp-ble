package com.atruedev.kmpble.mesh.crypto

/**
 * iOS crypto implementation.
 *
 * All symmetric primitives delegate to the shared pure Kotlin implementations
 * in [PureKotlinCrypto], [AesCcm], and [AesCmac]. This avoids interop
 * complexity with CommonCrypto and ensures consistent behavior across all
 * platforms.
 *
 * ## ECDH P-256 Limitation (Phase 1)
 *
 * ECDH P-256 for provisioning is not yet implemented in pure Kotlin.
 * CommonCrypto does expose ECDH via `CCECCryptor` but the cinterop
 * bindings are complex and error-prone. A pure Kotlin P-256 implementation
 * is planned for Phase 2.
 *
 * In Phase 1 (proxy-only), provisioning is not used at runtime because
 * the platform [MeshProvisioner] factories throw [MeshNotSupported].
 * The ECDH stubs below exist only to satisfy the `expect` contract.
 *
 * ## Secure Random
 *
 * Uses `kotlin.random.Random` as a fallback. Production apps should
 * replace this with `SecRandomCopyBytes` via cinterop for cryptographic
 * randomness. For Phase 1 proxy-only operation, this is acceptable
 * since random is only used for key generation during provisioning
 * (which is not called).
 */
internal actual object CryptoEngine {
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

    /**
     * Generate an ECDH P-256 key pair.
     *
     * FIXME(phase2): Implement pure Kotlin ECDH P-256 or wire CommonCrypto
     * via cinterop. Currently returns stub values because Phase 1 does
     * not use provisioning at runtime.
     */
    actual fun ecdhP256GenerateKeyPair(): EcdhKeyPair {
        // FIXME(phase2): Pure Kotlin P-256 ECDH not yet implemented.
        // CommonCrypto exposes CCECCryptor but cinterop is non-trivial.
        // Provisioning is not called in Phase 1 (proxy-only operation).
        val privateKey = secureRandomBytes(32)
        val publicKey = ByteArray(64)
        return EcdhKeyPair(privateKey, publicKey)
    }

    /**
     * Compute ECDH P-256 shared secret.
     *
     * FIXME(phase2): Same limitation as [ecdhP256GenerateKeyPair].
     */
    actual fun ecdhP256SharedSecret(
        privateKey: ByteArray, publicKey: ByteArray,
    ): ByteArray {
        // FIXME(phase2): Pure Kotlin P-256 ECDH not yet implemented.
        return ByteArray(32)
    }

    /**
     * Generate cryptographically secure random bytes.
     *
     * FIXME(phase2): Replace with `SecRandomCopyBytes` via cinterop
     * for production use. `kotlin.random.Random` is a PRNG, not a CSPRNG.
     * Acceptable for Phase 1 since random bytes are only used during
     * provisioning, which is not called in proxy-only operation.
     */
    actual fun secureRandomBytes(size: Int): ByteArray =
        ByteArray(size) { kotlin.random.Random.nextInt(256).toByte() }
}
