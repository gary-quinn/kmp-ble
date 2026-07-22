package com.atruedev.kmpble.mesh.internal

import com.atruedev.kmpble.mesh.*
import com.atruedev.kmpble.mesh.config.ConfigurationClient
import com.atruedev.kmpble.mesh.config.DefaultConfigurationClient
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.atomicfu.atomic

/**
 * Default implementation of [MeshNetwork].
 *
 * Concurrency: ALL state mutations run on a `limitedParallelism(1)` dispatcher,
 * following the same pattern as the core library's Peripheral. No locks, no mutexes.
 */
internal class MeshNetworkImpl(
    private val builder: MeshNetworkBuilder,
) : MeshNetwork {
    private val meshDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(meshDispatcher + SupervisorJob())

    // Thread-safe state via atomicfu
    private val _nodes = MutableStateFlow<List<MeshNode>>(emptyList())
    private val _isProxyConnected = MutableStateFlow(false)
    private val _incomingMessages = MutableSharedFlow<MeshMessage>(
        replay = 0, extraBufferCapacity = 64)

    private var proxyConnection: ProxyConnectionImpl? = null
    private val seqManager = SequenceNumberManager()
    private var closed = atomic(false)

    override val ownUnicastAddress: MeshAddress.UnicastAddress =
        builder._ownUnicastAddress
            ?: builder._elements.firstOrNull()?.unicastAddress
            ?: MeshAddress.UnicastAddress(0x0001u)

    override val nodes: StateFlow<List<MeshNode>> = _nodes.asStateFlow()
    override val ivIndex: StateFlow<IvIndex> = IvIndexTracker().ivIndex
    override val isProxyConnected: StateFlow<Boolean> = _isProxyConnected.asStateFlow()

    override val networkKeys: List<NetworkKey> get() = builder._networkKeys.toList()
    override val applicationKeys: List<ApplicationKey> get() = builder._applicationKeys.toList()
    override val incomingMessages: Flow<MeshMessage> = _incomingMessages.asSharedFlow()

    override val configurationClient: ConfigurationClient
        get() = DefaultConfigurationClient(this)

    override suspend fun addNetworkKey(key: NetworkKey) {
        withContext(meshDispatcher) { builder._networkKeys.add(key) }
    }

    override suspend fun addApplicationKey(key: ApplicationKey) {
        withContext(meshDispatcher) { builder._applicationKeys.add(key) }
    }

    override suspend fun addNode(node: MeshNode) {
        withContext(meshDispatcher) {
            _nodes.value = _nodes.value + node
        }
    }

    override suspend fun removeNode(address: MeshAddress.UnicastAddress) {
        withContext(meshDispatcher) {
            _nodes.value = _nodes.value.filter { it.unicastAddress != address }
        }
    }

    override fun findNode(address: MeshAddress.UnicastAddress): MeshNode? =
        _nodes.value.find { node ->
            node.unicastAddress == address ||
                node.elements.any { it.unicastAddress == address }
        }

    override suspend fun connectProxy(peripheral: Peripheral): ProxyConnection {
        val conn = ProxyConnectionImpl(peripheral, this)
        proxyConnection = conn
        _isProxyConnected.value = true
        scope.launch {
            conn.incomingPdus.collect { pdu -> processIncomingPdu(pdu) }
        }
        return conn
    }

    override suspend fun disconnectProxy() {
        withContext(meshDispatcher) {
            proxyConnection?.close()
            proxyConnection = null
            _isProxyConnected.value = false
        }
    }

    override suspend fun send(
        destination: MeshAddress,
        modelId: MeshModelId,
        opcode: MeshOpcode,
        payload: ByteArray,
        appKey: ApplicationKey,
        acknowledged: Boolean,
        ttl: UByte,
    ): MeshMessageResponse? {
        val conn = proxyConnection
            ?: throw MeshException(ProxyConnectionFailed("Not connected to a proxy node"))

        val seq = seqManager.nextSequenceNumber()
        val pdu = buildNetworkPdu(destination, opcode, payload, appKey, seq, ttl)
        conn.sendPdu(pdu)

        if (!acknowledged) return null
        return null // Response via incomingMessages flow
    }

    private fun buildNetworkPdu(
        destination: MeshAddress,
        opcode: MeshOpcode,
        payload: ByteArray,
        appKey: ApplicationKey,
        seq: Int,
        ttl: UByte,
    ): NetworkPdu = NetworkPdu(
        ivi = 0, nid = 0, ctl = 0, ttl = ttl.toInt(),
        seq = seq.toUInt(),
        src = ownUnicastAddress,
        dst = destination,
        transportPdu = opcode.toBytes() + payload,
        netMic = ByteArray(4),
    )

    private suspend fun processIncomingPdu(pdu: NetworkPdu) {
        val message = MeshMessage(
            source = pdu.src,
            destination = pdu.dst,
            opcode = MeshOpcode(0u),
            parameters = pdu.transportPdu,
            appKey = null,
        )
        _incomingMessages.emit(message)
    }

    override fun close() {
        closed.value = true
        scope.cancel()
        proxyConnection?.close()
    }
}
