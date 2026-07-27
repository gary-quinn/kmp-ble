package com.atruedev.kmpble.mesh.network

import com.atruedev.kmpble.mesh.*
import com.atruedev.kmpble.mesh.crypto.CryptoEngine
import kotlin.test.*

/**
 * End-to-end round-trip tests for the mesh network encryption pipeline.
 *
 * Verifies that a message encrypted through Access → Upper Transport →
 * Network Layer can be decrypted back through Network → Upper Transport →
 * Access Layer, producing identical content.
 */
class NetworkLayerRoundTripTest {

    // Test keys (randomly generated for testing only)
    private val netKeyBytes = CryptoEngine.secureRandomBytes(16)
    private val appKeyBytes = CryptoEngine.secureRandomBytes(16)
    private val deviceKeyBytes = CryptoEngine.secureRandomBytes(16)

    private val netKey = NetworkKey(KeyIndex(0u), netKeyBytes)
    private val appKey = ApplicationKey(KeyIndex(0u), appKeyBytes, KeyIndex(0u))
    private val deviceKey = DeviceKey(deviceKeyBytes)

    private val src = MeshAddress.UnicastAddress(0x0001u)
    private val dst = MeshAddress.UnicastAddress(0x000Au)

    @Test
    fun roundTripAppKeyEncryptedMessage() {
        val networkLayer = NetworkLayer(listOf(netKey))
        val upperTransport = UpperTransportLayer()

        // 1. Create original message
        val opcode = OnOffOpcodes.GENERIC_ONOFF_SET
        val payload = byteArrayOf(0x01) // ON
        val original = AccessLayer.encode(opcode, payload)

        // 2. Encrypt: Upper Transport (AppKey) → Network Layer (NetKey)
        val seq = 1
        val ivIndex = IvIndex.INITIAL
        val upperPdu = upperTransport.encryptWithAppKey(
            original, appKey, src, dst, seq, ivIndex)
        assertNotEquals(0, upperPdu.size, "Encrypted transport PDU should not be empty")

        val netPdu = networkLayer.encrypt(
            upperPdu, src, dst, netKey, ttl = 5, seq = seq, ivIndex = ivIndex)

        // 3. Decrypt: Network Layer → Upper Transport (AppKey)
        val decryptedTransport = networkLayer.decrypt(netPdu, ivIndex)
        assertNotNull(decryptedTransport, "Network layer decrypt should succeed")

        val decryptedAccess = upperTransport.decryptWithAppKey(
            decryptedTransport, appKey, src, dst, seq, ivIndex)
        assertNotNull(decryptedAccess, "Upper transport decrypt should succeed")

        // 4. Verify round-trip integrity
        val decoded = AccessLayer.decode(decryptedAccess)
        assertEquals(opcode.value, decoded.opcode.value, "Opcode mismatch")
        assertTrue(payload.contentEquals(decoded.parameters), "Payload mismatch")
    }

    @Test
    fun roundTripDeviceKeyEncryptedMessage() {
        val networkLayer = NetworkLayer(listOf(netKey))
        val upperTransport = UpperTransportLayer()

        // Config message (DeviceKey path)
        val opcode = MeshOpcode(0x8000u) // Config AppKey Add
        val payload = byteArrayOf(0x00, 0x00, 0x00, 0x00) + appKey.key
        val original = AccessLayer.encode(opcode, payload)

        val seq = 2
        val ivIndex = IvIndex.INITIAL

        // Encrypt with DeviceKey
        val upperPdu = upperTransport.encryptWithDeviceKey(
            original, deviceKey, src, dst, seq, ivIndex)
        val netPdu = networkLayer.encrypt(
            upperPdu, src, dst, netKey, ttl = 5, seq = seq, ivIndex = ivIndex)

        // Decrypt with DeviceKey
        val decryptedTransport = networkLayer.decrypt(netPdu, ivIndex)
        assertNotNull(decryptedTransport)

        val decryptedAccess = upperTransport.decryptWithDeviceKey(
            decryptedTransport, deviceKey, src, dst, seq, ivIndex)
        assertNotNull(decryptedAccess)

        val decoded = AccessLayer.decode(decryptedAccess)
        assertEquals(opcode.value, decoded.opcode.value)
        assertTrue(payload.contentEquals(decoded.parameters))
    }

    @Test
    fun roundTripWithDifferentSequenceNumbers() {
        val networkLayer = NetworkLayer(listOf(netKey))
        val upperTransport = UpperTransportLayer()
        val ivIndex = IvIndex.INITIAL

        val opcode = LevelOpcodes.GENERIC_LEVEL_GET
        val original = AccessLayer.encode(opcode, ByteArray(0))

        for (seq in 1..5) {
            val upperPdu = upperTransport.encryptWithAppKey(
                original, appKey, src, dst, seq, ivIndex)
            val netPdu = networkLayer.encrypt(
                upperPdu, src, dst, netKey, ttl = 5, seq = seq, ivIndex = ivIndex)

            val decryptedTransport = networkLayer.decrypt(netPdu, ivIndex)
            assertNotNull(decryptedTransport, "Seq $seq: network decrypt failed")

            val decryptedAccess = upperTransport.decryptWithAppKey(
                decryptedTransport, appKey, src, dst, seq, ivIndex)
            assertNotNull(decryptedAccess, "Seq $seq: transport decrypt failed")

            val decoded = AccessLayer.decode(decryptedAccess)
            assertEquals(opcode.value, decoded.opcode.value, "Seq $seq: opcode mismatch")
        }
    }

    @Test
    fun decryptWithWrongAppKeyFails() {
        val networkLayer = NetworkLayer(listOf(netKey))
        val upperTransport = UpperTransportLayer()

        val opcode = OnOffOpcodes.GENERIC_ONOFF_GET
        val original = AccessLayer.encode(opcode, ByteArray(0))
        val seq = 1
        val ivIndex = IvIndex.INITIAL

        val upperPdu = upperTransport.encryptWithAppKey(
            original, appKey, src, dst, seq, ivIndex)
        val netPdu = networkLayer.encrypt(
            upperPdu, src, dst, netKey, ttl = 5, seq = seq, ivIndex = ivIndex)

        // Try to decrypt with a different AppKey
        val wrongAppKey = ApplicationKey(
            KeyIndex(1u), CryptoEngine.secureRandomBytes(16), KeyIndex(0u))
        val decryptedTransport = networkLayer.decrypt(netPdu, ivIndex)
        assertNotNull(decryptedTransport)

        val result = upperTransport.decryptWithAppKey(
            decryptedTransport, wrongAppKey, src, dst, seq, ivIndex)
        assertNull(result, "Decrypt with wrong AppKey should fail (MIC mismatch)")
    }

    @Test
    fun decryptWithWrongNetKeyFails() {
        val networkLayer = NetworkLayer(listOf(netKey))
        val wrongNetworkLayer = NetworkLayer(listOf(
            NetworkKey(KeyIndex(1u), CryptoEngine.secureRandomBytes(16))))
        val upperTransport = UpperTransportLayer()

        val opcode = OnOffOpcodes.GENERIC_ONOFF_GET
        val original = AccessLayer.encode(opcode, ByteArray(0))
        val seq = 1
        val ivIndex = IvIndex.INITIAL

        val upperPdu = upperTransport.encryptWithAppKey(
            original, appKey, src, dst, seq, ivIndex)
        val netPdu = networkLayer.encrypt(
            upperPdu, src, dst, netKey, ttl = 5, seq = seq, ivIndex = ivIndex)

        // Try to decrypt with a different NetKey
        val result = wrongNetworkLayer.decrypt(netPdu, ivIndex)
        assertNull(result, "Decrypt with wrong NetKey should fail (NID mismatch)")
    }
}
