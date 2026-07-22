package com.atruedev.kmpble.mesh.crypto

import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Android crypto implementation using Java Cryptography Architecture (JCA).
 *
 * Uses javax.crypto for AES, javax.crypto.KeyAgreement for ECDH,
 * and java.security.MessageDigest for SHA-256.
 */
internal actual object CryptoEngine {
    private const val AES_ALGORITHM = "AES"
    private const val AES_ECB_TRANSFORM = "AES/ECB/NoPadding"
    private const val AES_CMAC_ALGORITHM = "AESCMAC"
    private const val EC_ALGORITHM = "EC"
    private const val SHA256_ALGORITHM = "SHA-256"

    private val secureRandom = SecureRandom()

    actual fun aes128EcbEncrypt(key: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_ECB_TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, AES_ALGORITHM))
        return cipher.doFinal(data)
    }

    actual fun aesCcmEncrypt(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray,
        micSize: Int,
    ): CcmResult {
        // Fall back to pure Kotlin CCM implementation.
        // javax.crypto CCM support is API 23+ but inconsistent across OEMs.
        // The pure Kotlin implementation in AesCcm is more reliable.
        return AesCcm.encrypt(key, nonce, plaintext, aad, micSize)
    }

    actual fun aesCcmDecrypt(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        aad: ByteArray,
        mic: ByteArray,
    ): ByteArray? = AesCcm.decrypt(key, nonce, ciphertext, aad, mic)

    actual fun aesCmac(key: ByteArray, data: ByteArray): ByteArray {
        return try {
            val mac = Mac.getInstance(AES_CMAC_ALGORITHM)
            mac.init(SecretKeySpec(key, AES_ALGORITHM))
            mac.doFinal(data)
        } catch (_: Exception) {
            // Fall back to pure Kotlin CMAC if platform provider unavailable
            AesCmac.compute(key, data)
        }
    }

    actual fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance(SHA256_ALGORITHM).digest(data)

    actual fun ecdhP256GenerateKeyPair(): EcdhKeyPair {
        val generator = KeyPairGenerator.getInstance(EC_ALGORITHM)
        generator.initialize(256, secureRandom)
        val keyPair = generator.generateKeyPair()
        val publicKey = keyPair.public.encoded
        val privateKey = keyPair.private.encoded
        return EcdhKeyPair(privateKey, publicKey)
    }

    actual fun ecdhP256SharedSecret(
        privateKey: ByteArray,
        publicKey: ByteArray,
    ): ByteArray {
        val keyFactory = java.security.KeyFactory.getInstance(EC_ALGORITHM)
        val privSpec = java.security.spec.PKCS8EncodedKeySpec(privateKey)
        val pubSpec = java.security.spec.X509EncodedKeySpec(publicKey)
        val privateKeyObj = keyFactory.generatePrivate(privSpec)
        val publicKeyObj = keyFactory.generatePublic(pubSpec)

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKeyObj)
        keyAgreement.doPhase(publicKeyObj, true)
        return keyAgreement.generateSecret()
    }

    actual fun secureRandomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        secureRandom.nextBytes(bytes)
        return bytes
    }
}
