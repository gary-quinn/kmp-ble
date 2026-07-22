package com.atruedev.kmpble.mesh

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.mesh.config.ConfigurationClient
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * A BLE Mesh network.
 *
 * [MeshNetwork] is the central entry point for BLE Mesh operations. It manages
 * the network state (keys, nodes, IV Index), provides connectivity via GATT Proxy,
 * and enables sending and receiving mesh messages.
 *
 * ## Lifecycle
 *
 * - Created via [MeshNetwork] factory function with a builder DSL.
 * - [connectProxy] establishes mesh connectivity through a proxy node.
 * - [send] transmits mesh messages to any address on the network.
 * - [close] disconnects the proxy and releases all resources.
 *
 * ## Usage
 *
 * ```kotlin
 * val network = MeshNetwork {
 *     networkKey(myNetKey)
 *     applicationKey(myAppKey)
 *     element(MeshElement(0, UnicastAddress(0x0001u), ElementLocation.MAIN,
 *         listOf(MeshModelId.GenericOnOffClient)))
 * }
 *
 * network.connectProxy(proxyPeripheral)
 * network.send(destination, MeshModelId.GenericOnOffServer,
 *     MeshOpcode(0x8202u), byteArrayOf(0x01), myAppKey)
 * network.close()
 * ```
 */
public interface MeshNetwork : AutoCloseable {
    // --- Identity ---

    /** Our unicast address on this mesh network. */
    public val ownUnicastAddress: MeshAddress.UnicastAddress

    // --- Observable State ---

    /** All provisioned nodes in this network. */
    public val nodes: StateFlow<List<MeshNode>>

    /** Current IV Index of the network. */
    public val ivIndex: StateFlow<IvIndex>

    /** Whether we are currently connected to a proxy node. */
    public val isProxyConnected: StateFlow<Boolean>

    // --- Keys ---

    /** Network keys registered in this network. */
    public val networkKeys: List<NetworkKey>

    /** Application keys registered in this network. */
    public val applicationKeys: List<ApplicationKey>

    /** Add a network key to this network. */
    public suspend fun addNetworkKey(key: NetworkKey)

    /** Add an application key to this network. */
    public suspend fun addApplicationKey(key: ApplicationKey)

    // --- Node Management ---

    /** Add a provisioned node to this network. */
    public suspend fun addNode(node: MeshNode)

    /** Remove a node from this network. */
    public suspend fun removeNode(address: MeshAddress.UnicastAddress)

    /** Find a node by its primary unicast address. */
    public fun findNode(address: MeshAddress.UnicastAddress): MeshNode?

    // --- Connectivity ---

    /**
     * Connect to the mesh network via a GATT Proxy node.
     *
     * The [Peripheral] must be connected to a proxy-enabled mesh node
     * that exposes the Mesh Proxy Service (UUID 1828).
     *
     * @param peripheral A connected peripheral that supports the proxy feature.
     * @return A [ProxyConnection] for sending and receiving mesh PDUs.
     */
    public suspend fun connectProxy(peripheral: Peripheral): ProxyConnection

    /** Disconnect from the current proxy node. */
    public suspend fun disconnectProxy()

    // --- Messaging ---

    /**
     * Send a mesh message to a destination address.
     *
     * @param destination Where to send the message (unicast, group, or virtual).
     * @param modelId The target model identifier.
     * @param opcode The operation code for the model operation.
     * @param payload The operation parameters.
     * @param appKey The application key for encryption.
     * @param acknowledged Whether to wait for a response (true) or fire-and-forget (false).
     * @param ttl Time-To-Live hop limit (default 5).
     * @return The response if acknowledged, or null if unacknowledged or timeout.
     */
    public suspend fun send(
        destination: MeshAddress,
        modelId: MeshModelId,
        opcode: MeshOpcode,
        payload: ByteArray,
        appKey: ApplicationKey,
        acknowledged: Boolean = true,
        ttl: UByte = 5u,
    ): MeshMessageResponse?

    /**
     * Flow of incoming mesh messages addressed to our models.
     *
     * Messages are dispatched based on destination address (our unicast
     * address or subscribed group/virtual addresses).
     */
    public val incomingMessages: Flow<MeshMessage>

    // --- Configuration ---

    /** Client for configuring nodes post-provisioning. */
    public val configurationClient: ConfigurationClient

    // --- Lifecycle ---

    /**
     * Close the network and release all resources.
     *
     * Disconnects the proxy, stops message processing, and clears
     * internal state. Safe to call multiple times.
     */
    override fun close()
}

/**
 * Builder DSL for [MeshNetwork] configuration.
 *
 * ```kotlin
 * val network = MeshNetwork {
 *     networkKey(myNetKey)
 *     applicationKey(myAppKey)
 *     element(myElement)
 *     stateStore(platformStateStore)
 * }
 * ```
 */
@MeshDsl
public class MeshNetworkBuilder {
    internal val _networkKeys = mutableListOf<NetworkKey>()
    internal val _applicationKeys = mutableListOf<ApplicationKey>()
    internal val _elements = mutableListOf<MeshElement>()
    internal var _stateStore: MeshStateStore = InMemoryMeshStateStore()
    internal var _ownUnicastAddress: MeshAddress.UnicastAddress? = null

    /** Register a network key. */
    public fun networkKey(key: NetworkKey) {
        _networkKeys.add(key)
    }

    /** Register an application key. */
    public fun applicationKey(key: ApplicationKey) {
        _applicationKeys.add(key)
    }

    /** Add an element (our own addressable entities). */
    public fun element(element: MeshElement) {
        _elements.add(element)
        if (_ownUnicastAddress == null) {
            _ownUnicastAddress = element.unicastAddress
        }
    }

    /** Set the persistence backend for network state. */
    public fun stateStore(store: MeshStateStore) {
        _stateStore = store
    }
}

/**
 * DSL marker to prevent invalid nesting in [MeshNetworkBuilder].
 */
@DslMarker
public annotation class MeshDsl

/**
 * A connection to a GATT Proxy node.
 *
 * [ProxyConnection] provides raw Network PDU access for advanced use cases.
 * Most users should use [MeshNetwork.send] and [MeshNetwork.incomingMessages]
 * instead.
 *
 * ## Lifecycle
 *
 * Obtained via [MeshNetwork.connectProxy]. [close] disconnects from the
 * proxy node but does not close the underlying [Peripheral].
 */
public interface ProxyConnection : AutoCloseable {
    /** Whether the proxy connection is currently active. */
    public val isConnected: StateFlow<Boolean>

    /** Raw Network PDU stream from the mesh network. */
    public val incomingPdus: Flow<NetworkPdu>

    /** Send a raw Network PDU into the mesh network. */
    public suspend fun sendPdu(pdu: NetworkPdu)

    /** Close the proxy connection. Does NOT close the underlying Peripheral. */
    override fun close()
}
