package com.atruedev.kmpble.mesh

import com.atruedev.kmpble.mesh.models.generic.GenericOnOffClient
import com.atruedev.kmpble.mesh.testing.FakeMeshNetwork
import com.atruedev.kmpble.mesh.testing.FakeProvisioner
import com.atruedev.kmpble.mesh.testing.SentMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Conformance tests for [MeshNetwork] using fake implementations.
 *
 * These tests validate the core mesh API contract without requiring
 * real BLE hardware. Platform-specific runners can extend this class.
 */
open class MeshNetworkConformanceTest {

    /**
     * Create a fake mesh network for testing.
     * Override in platform-specific tests if needed.
     */
    protected open fun createNetwork(): FakeMeshNetwork = FakeMeshNetwork(
        initialNetKeys = listOf(NetworkKey(KeyIndex(0u), ByteArray(16), "Test NetKey")),
        initialAppKeys = listOf(
            ApplicationKey(KeyIndex(0u), ByteArray(16), KeyIndex(0u), "Test AppKey")),
    )

    @Test
    fun testAddAndFindNode() = kotlinx.coroutines.test.runTest {
        val network = createNetwork()
        val node = MeshNode(
            unicastAddress = MeshAddress.UnicastAddress(0x0005u),
            deviceKey = DeviceKey(ByteArray(16)),
            elements = listOf(MeshElement(0, MeshAddress.UnicastAddress(0x0005u),
                ElementLocation.MAIN)),
        )

        network.addNode(node)
        val found = network.findNode(MeshAddress.UnicastAddress(0x0005u))
        assertNotNull(found)
        assertEquals(0x0005u.toUShort(), found.unicastAddress.value)
    }

    @Test
    fun testSendMessageRecorded() = kotlinx.coroutines.test.runTest {
        val network = createNetwork()
        val appKey = ApplicationKey(KeyIndex(0u), ByteArray(16), KeyIndex(0u))

        network.send(
            destination = MeshAddress.UnicastAddress(0x0005u),
            modelId = MeshModelId.GenericOnOffServer,
            opcode = MeshOpcode(0x8202u),
            payload = byteArrayOf(0x01),
            appKey = appKey,
            acknowledged = true,
        )

        val sent = network.getSentMessages()
        assertEquals(1, sent.size)
        val msg = sent.first()
        assertEquals(MeshModelId.GenericOnOffServer, msg.modelId)
        assertEquals(MeshOpcode(0x8202u), msg.opcode)
    }

    @Test
    fun testRemoveNode() = kotlinx.coroutines.test.runTest {
        val network = createNetwork()
        val node = MeshNode(
            unicastAddress = MeshAddress.UnicastAddress(0x0005u),
            deviceKey = DeviceKey(ByteArray(16)),
            elements = listOf(MeshElement(0, MeshAddress.UnicastAddress(0x0005u),
                ElementLocation.MAIN)),
        )

        network.addNode(node)
        assertEquals(1, network.nodes.value.size)

        network.removeNode(MeshAddress.UnicastAddress(0x0005u))
        assertEquals(0, network.nodes.value.size)
    }

    @Test
    fun testKeyManagement() = kotlinx.coroutines.test.runTest {
        val network = createNetwork()
        val initialCount = network.networkKeys.size
        assertTrue(initialCount >= 1, "Should have at least one initial network key")
    }

    @Test
    fun testProxyConnection() = kotlinx.coroutines.test.runTest {
        val network = createNetwork()
        assertEquals(false, network.isProxyConnected.value)

        // Can't actually connect without a real peripheral, but state transitions
        // should work with the fake
        network.simulateProxyConnect()
        assertEquals(true, network.isProxyConnected.value)

        network.disconnectProxy()
        assertEquals(false, network.isProxyConnected.value)
    }
}
