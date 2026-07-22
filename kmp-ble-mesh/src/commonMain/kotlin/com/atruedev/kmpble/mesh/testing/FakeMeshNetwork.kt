package com.atruedev.kmpble.mesh.testing

import com.atruedev.kmpble.mesh.*
import com.atruedev.kmpble.mesh.config.ConfigurationClient
import com.atruedev.kmpble.mesh.config.CompositionData
import com.atruedev.kmpble.mesh.config.ConfigurationStatus
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.coroutines.flow.*

/**
 * Fake [MeshNetwork] for unit testing without hardware.
 *
 * Provides an in-memory mesh network with controllable state, injected
 * nodes, and recorded sent messages for assertion.
 */
public class FakeMeshNetwork(
    initialNodes: List<MeshNode> = emptyList(),
    initialNetKeys: List<NetworkKey> = emptyList(),
    initialAppKeys: List<ApplicationKey> = emptyList(),
) : MeshNetwork {
    private val _nodes = MutableStateFlow(initialNodes)
    private val _ivIndex = MutableStateFlow(IvIndex.INITIAL)
    private val _isProxyConnected = MutableStateFlow(false)
    private val _incomingMessages = MutableSharedFlow<MeshMessage>(
        replay = 0, extraBufferCapacity = 64)
    private val sentMessages = mutableListOf<SentMessage>()

    override val ownUnicastAddress: MeshAddress.UnicastAddress = MeshAddress.UnicastAddress(0x0001u)
    override val nodes: StateFlow<List<MeshNode>> = _nodes.asStateFlow()
    override val ivIndex: StateFlow<IvIndex> = _ivIndex.asStateFlow()
    override val isProxyConnected: StateFlow<Boolean> = _isProxyConnected.asStateFlow()
    override val networkKeys: List<NetworkKey> = initialNetKeys.toList()
    override val applicationKeys: List<ApplicationKey> = initialAppKeys.toList()
    override val incomingMessages: Flow<MeshMessage> = _incomingMessages.asSharedFlow()
    override val configurationClient: ConfigurationClient = FakeConfigurationClient(this)

    override suspend fun addNetworkKey(key: NetworkKey) {}
    override suspend fun addApplicationKey(key: ApplicationKey) {}
    override suspend fun addNode(node: MeshNode) {
        _nodes.value = _nodes.value + node
    }
    override suspend fun removeNode(address: MeshAddress.UnicastAddress) {
        _nodes.value = _nodes.value.filter { it.unicastAddress != address }
    }
    override fun findNode(address: MeshAddress.UnicastAddress): MeshNode? =
        _nodes.value.find { it.unicastAddress == address }

    override suspend fun connectProxy(peripheral: Peripheral): ProxyConnection {
        _isProxyConnected.value = true
        return FakeProxyConnection()
    }
    override suspend fun disconnectProxy() { _isProxyConnected.value = false }

    override suspend fun send(
        destination: MeshAddress,
        modelId: MeshModelId,
        opcode: MeshOpcode,
        payload: ByteArray,
        appKey: ApplicationKey,
        acknowledged: Boolean,
        ttl: UByte,
    ): MeshMessageResponse? {
        sentMessages.add(SentMessage(destination, modelId, opcode, payload, appKey, acknowledged))
        return MeshMessageResponse(opcode, ByteArray(0))
    }

    override fun close() { _isProxyConnected.value = false }

    // --- Test helpers ---

    public fun getSentMessages(): List<SentMessage> = sentMessages.toList()

    public suspend fun simulateIncomingMessage(message: MeshMessage) {
        _incomingMessages.emit(message)
    }

    public fun simulateProxyDisconnect() { _isProxyConnected.value = false }
    public fun simulateProxyConnect() { _isProxyConnected.value = true }
}

public data class SentMessage(
    val destination: MeshAddress,
    val modelId: MeshModelId,
    val opcode: MeshOpcode,
    val payload: ByteArray,
    val appKey: ApplicationKey,
    val acknowledged: Boolean,
)

internal class FakeConfigurationClient(
    private val network: FakeMeshNetwork,
) : ConfigurationClient {
    override suspend fun addAppKey(node: MeshNode, appKey: ApplicationKey) =
        ConfigurationStatus(0x00u)
    override suspend fun bindAppKey(
        node: MeshNode, elementAddress: MeshAddress.UnicastAddress,
        modelId: MeshModelId, appKeyIndex: KeyIndex,
    ) = ConfigurationStatus(0x00u)
    override suspend fun setPublication(
        node: MeshNode, elementAddress: MeshAddress.UnicastAddress,
        modelId: MeshModelId, publishAddress: MeshAddress,
        appKeyIndex: KeyIndex, ttl: UByte,
    ) = ConfigurationStatus(0x00u)
    override suspend fun addSubscription(
        node: MeshNode, elementAddress: MeshAddress.UnicastAddress,
        modelId: MeshModelId, address: MeshAddress,
    ) = ConfigurationStatus(0x00u)
    override suspend fun removeSubscription(
        node: MeshNode, elementAddress: MeshAddress.UnicastAddress,
        modelId: MeshModelId, address: MeshAddress,
    ) = ConfigurationStatus(0x00u)
    override suspend fun setRelay(
        node: MeshNode, enabled: Boolean,
        retransmitCount: UByte, retransmitIntervalSteps: UByte,
    ) = ConfigurationStatus(0x00u)
    override suspend fun setProxy(node: MeshNode, enabled: Boolean) =
        ConfigurationStatus(0x00u)
    override suspend fun setFriend(node: MeshNode, enabled: Boolean) =
        ConfigurationStatus(0x00u)
    override suspend fun getCompositionData(node: MeshNode): CompositionData =
        CompositionData(0u, 0u, 0u, 0u, node.features, node.elements)
    override suspend fun getDefaultTtl(node: MeshNode): UByte = node.ttl
    override suspend fun setDefaultTtl(node: MeshNode, ttl: UByte): ConfigurationStatus =
        ConfigurationStatus(0x00u)
}

internal class FakeProxyConnection : ProxyConnection {
    private val _isConnected = MutableStateFlow(true)
    private val _incomingPdus = MutableSharedFlow<NetworkPdu>(
        replay = 0, extraBufferCapacity = 64)

    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    override val incomingPdus: Flow<NetworkPdu> = _incomingPdus.asSharedFlow()

    override suspend fun sendPdu(pdu: NetworkPdu) {}
    override fun close() { _isConnected.value = false }

    public fun simulatePdu(pdu: NetworkPdu) {
        _incomingPdus.tryEmit(pdu)
    }
}
