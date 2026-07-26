package com.atruedev.kmpble.mesh.internal

import com.atruedev.kmpble.mesh.*
import com.atruedev.kmpble.mesh.config.ConfigurationClient
import com.atruedev.kmpble.mesh.config.DefaultConfigurationClient
import com.atruedev.kmpble.mesh.network.*
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Default implementation of [MeshNetwork].
 *
 * ## Architecture
 *
 * The send/receive pipeline flows through four layers:
 *
 * **Sending:**
 *   [MeshNetwork.send] → AccessLayer.encode → UpperTransportLayer.encryptWithAppKey
 *   → NetworkLayer.encrypt → [ProxyConnection.sendPdu]
 *
 * **Receiving:**
 *   [ProxyConnection.incomingPdus] → NetworkLayer.decrypt
 *   → UpperTransportLayer.decryptWithAppKey → AccessLayer.decode
 *   → [MeshNetwork.incomingMessages]
 *
 * ## Concurrency
 *
 * ALL state mutations run on a `limitedParallelism(1)` dispatcher,
 * following the same pattern as the core library's Peripheral.
 * No locks, no mutexes.
 */
internal class MeshNetworkImpl(
    private val builder: MeshNetworkBuilder,
) : MeshNetwork {
    private val meshDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(meshDispatcher + SupervisorJob())

    // --- Layers ---
    private val ivIndexTracker = IvIndexTracker()
    private val networkLayer = NetworkLayer(builder._networkKeys)
    private val upperTransport = UpperTransportLayer()
    private val lowerTransport = LowerTransportLayer()
    private val seqManager = SequenceNumberManager()
    private val messageCache = MessageCache()

    // --- State ---
    private val _nodes = MutableStateFlow<List<MeshNode>>(emptyList())
    private val _isProxyConnected = MutableStateFlow(false)
    private val _incomingMessages = MutableSharedFlow<MeshMessage>(
        replay = 0, extraBufferCapacity = 64,
    )

    private var proxyConnection: ProxyConnectionImpl? = null
    private var closed = atomic(false)

    /**
     * Pending acknowledged requests, keyed by destination address.
     *
     * When [send] is called with `acknowledged = true`, a deferred is stored
     * here so that [processIncomingPdu] can complete it when the status
     * response arrives from the destination node.
     */
    private val pendingRequests = mutableMapOf<Int, CompletableDeferred<MeshMessageResponse?>>()

    // --- Identity ---

    override val ownUnicastAddress: MeshAddress.UnicastAddress =
        builder._ownUnicastAddress
            ?: builder._elements.firstOrNull()?.unicastAddress
            ?: MeshAddress.UnicastAddress(0x0001u)

    // --- Observable State ---

    override val nodes: StateFlow<List<MeshNode>> = _nodes.asStateFlow()
    override val ivIndex: StateFlow<IvIndex> = ivIndexTracker.ivIndex
    override val isProxyConnected: StateFlow<Boolean> = _isProxyConnected.asStateFlow()

    // --- Keys ---

    override val networkKeys: List<NetworkKey> get() = builder._networkKeys.toList()
    override val applicationKeys: List<ApplicationKey> get() = builder._applicationKeys.toList()

    override suspend fun addNetworkKey(key: NetworkKey) {
        withContext(meshDispatcher) { builder._networkKeys.add(key) }
    }

    override suspend fun addApplicationKey(key: ApplicationKey) {
        withContext(meshDispatcher) { builder._applicationKeys.add(key) }
    }

    // --- Node Management ---

    override suspend fun addNode(node: MeshNode) {
        withContext(meshDispatcher) { _nodes.value = _nodes.value + node }
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

    // --- Connectivity ---

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

    // --- Messaging ---

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

        val netKey = networkKeys.find { it.index == appKey.boundNetKeyIndex }
            ?: throw MeshException(InvalidParameters(
                "No network key bound to AppKey index ${appKey.index.value}"))

        val seq = seqManager.nextSequenceNumber()
        val ivIdx = ivIndexTracker.currentSendIvIndex

        // 1. Encode access PDU: opcode + parameters
        val accessPdu = AccessLayer.encode(opcode, payload)

        // 2. Upper Transport: encrypt with AppKey
        val upperPdu = upperTransport.encryptWithAppKey(
            accessPdu, appKey, ownUnicastAddress, destination, seq, ivIdx,
        )

        // 3. Network Layer: encrypt with NetKey
        val networkPdu = networkLayer.encrypt(
            upperPdu, ownUnicastAddress, destination, netKey,
            ttl = ttl.toInt(), seq = seq, ivIndex = ivIdx,
        )

        // 4. Send via proxy
        conn.sendPdu(networkPdu)

        if (!acknowledged) return null

        // 5. Wait for the status response (with 5-second timeout)
        return waitForResponse(destination)
    }

    @OptIn(ExperimentalMeshApi::class)
    override suspend fun sendConfig(
        destination: MeshAddress,
        opcode: MeshOpcode,
        payload: ByteArray,
        deviceKey: DeviceKey,
        acknowledged: Boolean,
        ttl: UByte,
    ): MeshMessageResponse? {
        val conn = proxyConnection
            ?: throw MeshException(ProxyConnectionFailed("Not connected to a proxy node"))

        val netKey = networkKeys.firstOrNull()
            ?: throw MeshException(InvalidParameters("No network key registered"))

        val seq = seqManager.nextSequenceNumber()
        val ivIdx = ivIndexTracker.currentSendIvIndex

        // 1. Encode access PDU: opcode + parameters
        val accessPdu = AccessLayer.encode(opcode, payload)

        // 2. Upper Transport: encrypt with DeviceKey
        val upperPdu = upperTransport.encryptWithDeviceKey(
            accessPdu, deviceKey, ownUnicastAddress, destination, seq, ivIdx,
        )

        // 3. Network Layer: encrypt with NetKey
        val networkPdu = networkLayer.encrypt(
            upperPdu, ownUnicastAddress, destination, netKey,
            ttl = ttl.toInt(), seq = seq, ivIndex = ivIdx,
        )

        // 4. Send via proxy
        conn.sendPdu(networkPdu)

        if (!acknowledged) return null

        return waitForResponse(destination)
    }

    override val incomingMessages: Flow<MeshMessage> = _incomingMessages.asSharedFlow()

    // --- Configuration ---

    override val configurationClient: ConfigurationClient
        get() = DefaultConfigurationClient(this)

    // --- Lifecycle ---

    override fun close() {
        closed.value = true
        scope.cancel()
        proxyConnection?.close()
    }

    // --- Private helpers ---

    /**
     * Process an incoming Network PDU from the proxy connection.
     *
     * Pipeline: NetworkLayer.decrypt → UpperTransportLayer.decrypt (AppKey
     * or DeviceKey) → AccessLayer.decode → emit MeshMessage.
     */
    private suspend fun processIncomingPdu(pdu: NetworkPdu) {
        // Check duplicate cache to prevent forwarding loops
        val src = pdu.src.value.toInt()
        val seq = pdu.seq.toInt()
        if (messageCache.isDuplicate(src, seq)) return

        val ivIdx = ivIndexTracker.resolveReceiveIvIndex(pdu.ivi)

        // 1. Network Layer: decrypt with NetKey
        val transportPayload = networkLayer.decrypt(pdu, ivIdx) ?: return

        // 2. Upper Transport: try AppKey first, then DeviceKey for config messages
        var accessPdu: ByteArray? = null
        var matchedAppKey: ApplicationKey? = null
        var matchedDeviceKey: DeviceKey? = null

        // Try each known AppKey
        for (appKey in applicationKeys) {
            val decrypted = upperTransport.decryptWithAppKey(
                transportPayload, appKey, pdu.src, pdu.dst, seq, ivIdx,
            )
            if (decrypted != null) {
                accessPdu = decrypted
                matchedAppKey = appKey
                break
            }
        }

        // If no AppKey matched, try DeviceKey of known nodes
        if (accessPdu == null) {
            for (node in _nodes.value) {
                val decrypted = upperTransport.decryptWithDeviceKey(
                    transportPayload, node.deviceKey, pdu.src, pdu.dst, seq, ivIdx,
                )
                if (decrypted != null) {
                    accessPdu = decrypted
                    matchedDeviceKey = node.deviceKey
                    break
                }
            }
        }

        if (accessPdu == null) return // Could not decrypt

        // 3. Access Layer: decode opcode + parameters
        val message = AccessLayer.decode(accessPdu)

        // 4. Emit as public MeshMessage (appKey is null for DeviceKey messages)
        val meshMessage = MeshMessage(
            source = pdu.src,
            destination = pdu.dst,
            opcode = message.opcode,
            parameters = message.parameters,
            appKey = matchedAppKey,
        )
        _incomingMessages.emit(meshMessage)

        // 5. Resolve pending acknowledged request (if any)
        pendingRequests[src]?.complete(
            MeshMessageResponse(message.opcode, message.parameters))
    }

    /**
     * Set up a pending request deferred and wait for the response
     * with a 5-second timeout.
     */
    private suspend fun waitForResponse(
        destination: MeshAddress,
    ): MeshMessageResponse? {
        val deferred = CompletableDeferred<MeshMessageResponse?>()
        val dstKey = destination.value.toInt()
        pendingRequests[dstKey] = deferred
        return try {
            withTimeout(ACK_TIMEOUT_MS) { deferred.await() }
        } catch (_: TimeoutCancellationException) {
            null
        } finally {
            pendingRequests.remove(dstKey)
        }
    }

    companion object {
        /** Timeout for acknowledged message responses. */
        private const val ACK_TIMEOUT_MS = 5_000L
    }
}
