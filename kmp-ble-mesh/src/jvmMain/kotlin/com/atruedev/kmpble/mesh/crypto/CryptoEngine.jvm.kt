package com.atruedev.kmpble.mesh.crypto

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * JVM crypto implementation using Java Cryptography Architecture (JCA).
 *
 * Full BLE Mesh crypto support on JVM. All primitives are available
 * through standard Java crypto providers.
 */
internal actual object CryptoEngine {
    private const val AES_ALGORITHM = "AES"
    private const val AES_ECB_TRANSFORM = "AES/ECB/NoPadding"
    private const val AES_CMAC_ALGORITHM = "AESCMAC"
    private const val EC_ALGORITHM = "EC"
    private const val ECDH_ALGORITHM = "ECDH"
    private const val EC_CURVE = "secp256r1"
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
    ): CcmResult = AesCcm.encrypt(key, nonce, plaintext, aad, micSize)

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
            AesCmac.compute(key, data)
        }
    }

    actual fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance(SHA256_ALGORITHM).digest(data)

    actual fun ecdhP256GenerateKeyPair(): EcdhKeyPair {
        val generator = KeyPairGenerator.getInstance(EC_ALGORITHM)
        generator.initialize(ECGenParameterSpec(EC_CURVE), secureRandom)
        val keyPair = generator.generateKeyPair()

        val publicKey = keyPair.public.encoded
        return EcdhKeyPair(
            privateKey = keyPair.private.encoded,
            publicKey = publicKey,
        )
    }

    actual fun ecdhP256SharedSecret(
        privateKey: ByteArray,
        publicKey: ByteArray,
    ): ByteArray {
        val keyFactory = KeyFactory.getInstance(EC_ALGORITHM)
        val privateKeyObj = keyFactory.generatePrivate(
            PKCS8EncodedKeySpec(privateKey),
        )
        val publicKeyObj = keyFactory.generatePublic(
            X509EncodedKeySpec(publicKey),
        )
        val keyAgreement = KeyAgreement.getInstance(ECDH_ALGORITHM)
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
