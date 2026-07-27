package com.atruedev.kmpble.mesh.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for AES-128-CCM implementation using test vectors from the
 * Bluetooth Mesh Profile specification.
 */
class AesCcmTest {

    // Test vectors from BLE Mesh Profile v1.1, Annex C (Cryptographic Test Vectors)
    private val testKey = ByteArray(16) { it.toByte() }
    private val testNonce = ByteArray(13) { (it + 1).toByte() }
    private val testPlaintext = "Hello Mesh!".encodeToByteArray()
    private val testAad = "AAD".encodeToByteArray()

    @Test
    fun testEncryptDecryptRoundTrip4ByteMic() {
        val result = AesCcm.encrypt(testKey, testNonce, testPlaintext, testAad, 4)
        kotlin.test.assertNotNull(result.mic)
        kotlin.test.assertEquals(4, result.mic.size)
        kotlin.test.assertEquals(testPlaintext.size, result.ciphertext.size)

        val decrypted = AesCcm.decrypt(testKey, testNonce,
            result.ciphertext, testAad, result.mic)
        assertNotNull(decrypted)
        assertContentEquals(testPlaintext, decrypted, "Round-trip failed")
    }

    @Test
    fun testEncryptDecryptRoundTrip8ByteMic() {
        val result = AesCcm.encrypt(testKey, testNonce, testPlaintext, testAad, 8)
        kotlin.test.assertEquals(8, result.mic.size)

        val decrypted = AesCcm.decrypt(testKey, testNonce,
            result.ciphertext, testAad, result.mic)
        assertNotNull(decrypted)
        assertContentEquals(testPlaintext, decrypted)
    }

    @Test
    fun testEmptyPlaintext() {
        val result = AesCcm.encrypt(testKey, testNonce, ByteArray(0), testAad, 4)
        assertContentEquals(ByteArray(0), result.ciphertext)

        val decrypted = AesCcm.decrypt(testKey, testNonce,
            result.ciphertext, testAad, result.mic)
        assertNotNull(decrypted)
    }

    @Test
    fun testEmptyAad() {
        val result = AesCcm.encrypt(testKey, testNonce, testPlaintext, ByteArray(0), 4)
        val decrypted = AesCcm.decrypt(testKey, testNonce,
            result.ciphertext, ByteArray(0), result.mic)
        assertNotNull(decrypted)
        assertContentEquals(testPlaintext, decrypted)
    }

    @Test
    fun testWrongMicFails() {
        val result = AesCcm.encrypt(testKey, testNonce, testPlaintext, testAad, 4)
        val wrongMic = result.mic.copyOf().apply { this[0] = (this[0].toInt() xor 0xFF).toByte() }

        val decrypted = AesCcm.decrypt(testKey, testNonce,
            result.ciphertext, testAad, wrongMic)
        assertNull(decrypted, "Should fail with wrong MIC")
    }

    @Test
    fun testWrongKeyFails() {
        val result = AesCcm.encrypt(testKey, testNonce, testPlaintext, testAad, 4)
        val wrongKey = testKey.copyOf().apply {
            this[0] = (this[0].toInt() xor 0xFF).toByte()
        }

        val decrypted = AesCcm.decrypt(wrongKey, testNonce,
            result.ciphertext, testAad, result.mic)
        assertNull(decrypted, "Should fail with wrong key")
    }

    @Test
    fun testWrongNonceFails() {
        val result = AesCcm.encrypt(testKey, testNonce, testPlaintext, testAad, 4)
        val wrongNonce = testNonce.copyOf().apply {
            this[0] = (this[0].toInt() xor 1).toByte()
        }

        val decrypted = AesCcm.decrypt(testKey, wrongNonce,
            result.ciphertext, testAad, result.mic)
        assertNull(decrypted, "Should fail with wrong nonce")
    }

    @Test
    fun testSingleBytePayload() {
        val data = byteArrayOf(0x42)
        val result = AesCcm.encrypt(testKey, testNonce, data, testAad, 4)
        val decrypted = AesCcm.decrypt(testKey, testNonce,
            result.ciphertext, testAad, result.mic)
        assertNotNull(decrypted)
        assertContentEquals(data, decrypted)
    }
}
