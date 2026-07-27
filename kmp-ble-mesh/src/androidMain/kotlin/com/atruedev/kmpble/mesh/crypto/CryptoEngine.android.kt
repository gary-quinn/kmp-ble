package com.atruedev.kmpble.mesh.crypto

import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Android crypto implementation using Java Cryptography Architecture (JCA).
 *
 * Uses javax.crypto for AES, javax.crypto.KeyAgreement for ECDH,
 * and java.security.MessageDigest for SHA-256.
 *
 * ## ECDH Key Format
 *
 * The BLE Mesh provisioning protocol uses raw P-256 keys:
 * - Public key: 64 bytes uncompressed (X || Y), no 0x04 prefix
 * - Private key: 32 bytes raw scalar
 *
 * JCA uses DER-encoded keys internally (X.509 for public, PKCS#8 for
 * private). We convert between raw and DER as needed.
 */
internal actual object CryptoEngine {
    private const val AES_ALGORITHM = "AES"
    private const val AES_ECB_TRANSFORM = "AES/ECB/NoPadding"
    private const val AES_CMAC_ALGORITHM = "AESCMAC"
    private const val EC_ALGORITHM = "EC"
    private const val SHA256_ALGORITHM = "SHA-256"
    private const val P256_CURVE = "secp256r1"

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
        generator.initialize(ECGenParameterSpec(P256_CURVE), secureRandom)
        val keyPair = generator.generateKeyPair()

        // Extract raw 64-byte uncompressed public key (X || Y)
        val ecPublicKey = keyPair.public as ECPublicKey
        val pubPoint = ecPublicKey.w
        val publicKey = padTo32(pubPoint.affineX) + padTo32(pubPoint.affineY)

        // Extract raw 32-byte private key scalar
        val ecPrivateKey = keyPair.private as ECPrivateKey
        val privateKey = padTo32(ecPrivateKey.s)

        return EcdhKeyPair(privateKey, publicKey)
    }

    actual fun ecdhP256SharedSecret(
        privateKey: ByteArray,
        publicKey: ByteArray,
    ): ByteArray {
        val keyFactory = KeyFactory.getInstance(EC_ALGORITHM)

        // Generate a throwaway key pair to obtain ECParameterSpec
        val generator = KeyPairGenerator.getInstance(EC_ALGORITHM)
        generator.initialize(ECGenParameterSpec(P256_CURVE), secureRandom)
        val params = (generator.generateKeyPair().private as ECPrivateKey).params

        // Reconstruct private key from raw 32-byte scalar
        val privSpec = ECPrivateKeySpec(BigInteger(1, privateKey), params)
        val privateKeyObj = keyFactory.generatePrivate(privSpec)

        // Reconstruct public key from raw 64-byte uncompressed point (X || Y)
        val pubX = BigInteger(1, publicKey.copyOfRange(0, 32))
        val pubY = BigInteger(1, publicKey.copyOfRange(32, 64))
        val pubSpec = ECPublicKeySpec(ECPoint(pubX, pubY), params)
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

    /** Pad a BigInteger to exactly 32 bytes (big-endian, unsigned). */
    private fun padTo32(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        return when {
            bytes.size == 32 -> bytes
            bytes.size > 32 -> bytes.copyOfRange(bytes.size - 32, bytes.size)
            else -> {
                // BigInteger.toByteArray() uses two's complement, so leading
                // zeros are omitted. Pad to exactly 32 bytes.
                val padded = ByteArray(32)
                bytes.copyInto(padded, 32 - bytes.size)
                padded
            }
        }
    }
}
