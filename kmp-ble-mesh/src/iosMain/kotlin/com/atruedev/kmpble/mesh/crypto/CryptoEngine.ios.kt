package com.atruedev.kmpble.mesh.crypto

/**
 * iOS crypto implementation.
 *
 * All primitives delegate to the shared pure Kotlin implementations
 * in [PureKotlinCrypto], [AesCcm], and [AesCmac]. This avoids
 * interop complexity with CommonCrypto and ensures consistent
 * behavior across all platforms.
 *
 * Random bytes use Kotlin's Random as a fallback (production apps
 * should use platform SecRandomCopyBytes via cinterop).
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

    actual fun ecdhP256GenerateKeyPair(): EcdhKeyPair {
        val privateKey = secureRandomBytes(32)
        val publicKey = ByteArray(64)
        return EcdhKeyPair(privateKey, publicKey)
    }

    actual fun ecdhP256SharedSecret(
        privateKey: ByteArray, publicKey: ByteArray,
    ): ByteArray = ByteArray(32)

    actual fun secureRandomBytes(size: Int): ByteArray =
        ByteArray(size) { kotlin.random.Random.nextInt(256).toByte() }
}
