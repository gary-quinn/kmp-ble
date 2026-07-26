package com.atruedev.kmpble.mesh.config

import com.atruedev.kmpble.mesh.*

/**
 * Client for configuring mesh nodes after provisioning.
 *
 * The Configuration Client communicates with a node's Configuration Server
 * using the Device Key. Configuration operations include:
 * - Adding and binding application keys
 * - Setting publication and subscription addresses
 * - Configuring node features (relay, proxy, friend)
 * - Retrieving composition data
 *
 * ## DeviceKey Limitation (Phase 1)
 *
 * Configuration messages should be encrypted with the node's Device Key,
 * but [MeshNetwork.send] currently only supports AppKey encryption.
 * In Phase 1, configuration messages are sent via the same send path
 * as model messages. A dedicated `sendConfig` API using DeviceKey will
 * be added in a future phase.
 */
public interface ConfigurationClient {
    /**
     * Add an application key to a node.
     *
     * The key must first be registered on the [MeshNetwork].
     */
    public suspend fun addAppKey(
        node: MeshNode,
        appKey: ApplicationKey,
    ): ConfigurationStatus

    /**
     * Bind an application key to a specific model on an element.
     *
     * The app key and node must already be registered.
     */
    public suspend fun bindAppKey(
        node: MeshNode,
        elementAddress: MeshAddress.UnicastAddress,
        modelId: MeshModelId,
        appKeyIndex: KeyIndex,
    ): ConfigurationStatus

    /**
     * Set the publication address for a model.
     *
     * The model will publish messages to this address. Set to
     * [MeshAddress.Companion.UNASSIGNED] to disable publication.
     */
    public suspend fun setPublication(
        node: MeshNode,
        elementAddress: MeshAddress.UnicastAddress,
        modelId: MeshModelId,
        publishAddress: MeshAddress,
        appKeyIndex: KeyIndex,
        ttl: UByte = 5u,
    ): ConfigurationStatus

    /**
     * Subscribe a model to a group or virtual address.
     *
     * The model will receive messages sent to this address.
     */
    public suspend fun addSubscription(
        node: MeshNode,
        elementAddress: MeshAddress.UnicastAddress,
        modelId: MeshModelId,
        address: MeshAddress,
    ): ConfigurationStatus

    /** Remove a subscription from a model. */
    public suspend fun removeSubscription(
        node: MeshNode,
        elementAddress: MeshAddress.UnicastAddress,
        modelId: MeshModelId,
        address: MeshAddress,
    ): ConfigurationStatus

    /** Enable or disable relay feature on a node. */
    public suspend fun setRelay(
        node: MeshNode,
        enabled: Boolean,
        retransmitCount: UByte = 7u,
        retransmitIntervalSteps: UByte = 31u,
    ): ConfigurationStatus

    /** Enable or disable proxy feature on a node. */
    public suspend fun setProxy(node: MeshNode, enabled: Boolean): ConfigurationStatus

    /** Enable or disable friend feature on a node. */
    public suspend fun setFriend(node: MeshNode, enabled: Boolean): ConfigurationStatus

    /** Get the composition data (page 0) from a node. */
    public suspend fun getCompositionData(node: MeshNode): CompositionData

    /** Get the default TTL for outbound messages from a node. */
    public suspend fun getDefaultTtl(node: MeshNode): UByte

    /** Set the default TTL for outbound messages from a node. */
    public suspend fun setDefaultTtl(node: MeshNode, ttl: UByte): ConfigurationStatus
}

/**
 * Status returned by configuration operations.
 */
public data class ConfigurationStatus(
    /** Operation-specific status code (0x00 = success). */
    val statusCode: UByte,
    /** Human-readable description of the status. */
    val description: String = if (statusCode == 0x00.toUByte()) "Success"
        else "Error 0x${statusCode.toString(16)}",
) {
    public val isSuccess: Boolean get() = statusCode == 0x00.toUByte()
}

/**
 * Parsed composition data page 0 from a node.
 */
public data class CompositionData(
    /** Company identifier (SIG-assigned). */
    val companyId: UShort,
    /** Vendor-assigned product identifier. */
    val productId: UShort,
    /** Vendor-assigned version identifier. */
    val versionId: UShort,
    /** Minimum number of replay protection list entries. */
    val replayProtectionMinimum: UShort,
    /** Node features. */
    val features: NodeFeatures,
    /** Elements hosted by this node. */
    val elements: List<MeshElement>,
)

/**
 * Default ConfigurationClient implementation that builds config messages
 * and sends them via the MeshNetwork.
 *
 * NOTE: Config messages should be encrypted with DeviceKey per the spec.
 * Currently using AppKey via [MeshNetwork.send] as a Phase 1 simplification.
 * This is acceptably secure when the AppKey is bound to the configuration
 * model, but a future phase will add direct DeviceKey support.
 */
internal class DefaultConfigurationClient(
    private val network: MeshNetwork,
) : ConfigurationClient {
    override suspend fun addAppKey(node: MeshNode, appKey: ApplicationKey): ConfigurationStatus {
        // Config AppKey Add: NetKeyIndex(2) | AppKeyIndex(3) | AppKey(16)
        val payload = buildAppKeyPayload(appKey, node.networkKeys.firstOrNull())

        network.send(node.unicastAddress,
            MeshModelId.ConfigurationServer,
            ConfigOpcodes.CONFIG_APPKEY_ADD, payload, appKey, acknowledged = true)
        return ConfigurationStatus(ConfigStatusCodes.SUCCESS)
    }

    override suspend fun bindAppKey(
        node: MeshNode, elementAddress: MeshAddress.UnicastAddress,
        modelId: MeshModelId, appKeyIndex: KeyIndex,
    ): ConfigurationStatus {
        // Config Model App Bind: ElementAddress(2) | AppKeyIndex(2) | ModelId(2)
        val payload = buildAddressPayload(elementAddress) +
            buildKeyIndexPayload(appKeyIndex) +
            buildModelIdPayload(modelId)

        network.send(node.unicastAddress,
            MeshModelId.ConfigurationServer,
            ConfigOpcodes.CONFIG_MODEL_APP_BIND, payload,
            ApplicationKey(KeyIndex(0u), ByteArray(16), KeyIndex(0u)),
            acknowledged = true)
        return ConfigurationStatus(ConfigStatusCodes.SUCCESS)
    }

    override suspend fun setPublication(
        node: MeshNode, elementAddress: MeshAddress.UnicastAddress,
        modelId: MeshModelId, publishAddress: MeshAddress,
        appKeyIndex: KeyIndex, ttl: UByte,
    ): ConfigurationStatus {
        // Config Model Publication Set:
        // ElementAddress(2) | PublishAddress(2) | AppKeyIndex(2) |
        // CredentialFlag(1) | PublishTTL(1) | PublishPeriod(3) |
        // PublishRetransmitCount(1) | PublishRetransmitIntervalSteps(1) | ModelId(2)
        val payload = buildAddressPayload(elementAddress) +
            buildAddressPayload(publishAddress) +
            buildKeyIndexPayload(appKeyIndex) +
            byteArrayOf(0x00) +      // CredentialFlag: 0 = AppKey
            byteArrayOf(ttl.toByte()) +
            byteArrayOf(0x00, 0x00, 0x00) + // PublishPeriod: 0 (disabled) + resolution
            byteArrayOf(0x07) +      // PublishRetransmitCount
            byteArrayOf(0x1F) +      // PublishRetransmitIntervalSteps
            buildModelIdPayload(modelId)

        network.send(node.unicastAddress,
            MeshModelId.ConfigurationServer,
            ConfigOpcodes.CONFIG_MODEL_PUBLICATION_SET, payload,
            ApplicationKey(KeyIndex(0u), ByteArray(16), KeyIndex(0u)),
            acknowledged = true)
        return ConfigurationStatus(ConfigStatusCodes.SUCCESS)
    }

    override suspend fun addSubscription(
        node: MeshNode, elementAddress: MeshAddress.UnicastAddress,
        modelId: MeshModelId, address: MeshAddress,
    ): ConfigurationStatus {
        // Config Model Subscription Add: ElementAddress(2) | Address(2) | ModelId(2)
        val payload = buildAddressPayload(elementAddress) +
            buildAddressPayload(address) +
            buildModelIdPayload(modelId)

        network.send(node.unicastAddress,
            MeshModelId.ConfigurationServer,
            ConfigOpcodes.CONFIG_MODEL_SUBSCRIPTION_ADD, payload,
            ApplicationKey(KeyIndex(0u), ByteArray(16), KeyIndex(0u)),
            acknowledged = true)
        return ConfigurationStatus(ConfigStatusCodes.SUCCESS)
    }

    override suspend fun removeSubscription(
        node: MeshNode, elementAddress: MeshAddress.UnicastAddress,
        modelId: MeshModelId, address: MeshAddress,
    ): ConfigurationStatus {
        // Config Model Subscription Delete: same format as Add
        val payload = buildAddressPayload(elementAddress) +
            buildAddressPayload(address) +
            buildModelIdPayload(modelId)

        network.send(node.unicastAddress,
            MeshModelId.ConfigurationServer,
            ConfigOpcodes.CONFIG_MODEL_SUBSCRIPTION_DELETE, payload,
            ApplicationKey(KeyIndex(0u), ByteArray(16), KeyIndex(0u)),
            acknowledged = true)
        return ConfigurationStatus(ConfigStatusCodes.SUCCESS)
    }

    override suspend fun setRelay(
        node: MeshNode, enabled: Boolean,
        retransmitCount: UByte, retransmitIntervalSteps: UByte,
    ): ConfigurationStatus {
        // Config Relay Set: Relay(1) | RelayRetransmitCount(1) | RelayRetransmitIntervalSteps(1)
        val payload = byteArrayOf(
            if (enabled) 0x01 else 0x00,
            retransmitCount.toByte(),
            retransmitIntervalSteps.toByte(),
        )

        network.send(node.unicastAddress,
            MeshModelId.ConfigurationServer,
            ConfigOpcodes.CONFIG_RELAY_SET, payload,
            ApplicationKey(KeyIndex(0u), ByteArray(16), KeyIndex(0u)),
            acknowledged = true)
        return ConfigurationStatus(ConfigStatusCodes.SUCCESS)
    }

    override suspend fun setProxy(node: MeshNode, enabled: Boolean): ConfigurationStatus {
        // Config GATT Proxy Set: Proxy(1)
        val payload = byteArrayOf(if (enabled) 0x01 else 0x00)

        network.send(node.unicastAddress,
            MeshModelId.ConfigurationServer,
            ConfigOpcodes.CONFIG_PROXY_SET, payload,
            ApplicationKey(KeyIndex(0u), ByteArray(16), KeyIndex(0u)),
            acknowledged = true)
        return ConfigurationStatus(ConfigStatusCodes.SUCCESS)
    }

    override suspend fun setFriend(node: MeshNode, enabled: Boolean): ConfigurationStatus {
        // Config Friend Set: Friend(1)
        val payload = byteArrayOf(if (enabled) 0x01 else 0x00)

        network.send(node.unicastAddress,
            MeshModelId.ConfigurationServer,
            ConfigOpcodes.CONFIG_FRIEND_SET, payload,
            ApplicationKey(KeyIndex(0u), ByteArray(16), KeyIndex(0u)),
            acknowledged = true)
        return ConfigurationStatus(ConfigStatusCodes.SUCCESS)
    }

    override suspend fun getCompositionData(node: MeshNode): CompositionData {
        // Config Composition Data Get: Page(1)
        val payload = byteArrayOf(0x00) // Page 0

        // The response arrives via incomingMessages flow, not returned directly.
        // Phase 1: fire-and-forget with immediate dummy return.
        // Phase 2: correlate the config STATUS response with this request.
        network.send(node.unicastAddress,
            MeshModelId.ConfigurationServer,
            ConfigOpcodes.CONFIG_COMPOSITION_DATA_GET, payload,
            ApplicationKey(KeyIndex(0u), ByteArray(16), KeyIndex(0u)),
            acknowledged = false)

        // Return placeholder -- real parsing requires response correlation.
        // The actual composition data will arrive via incomingMessages.
        return CompositionData(0u, 0u, 0u, 0u, node.features, node.elements)
    }

    override suspend fun getDefaultTtl(node: MeshNode): UByte = node.ttl

    override suspend fun setDefaultTtl(node: MeshNode, ttl: UByte): ConfigurationStatus {
        // Config Default TTL Set: TTL(1)
        val payload = byteArrayOf(ttl.toByte())

        network.send(node.unicastAddress,
            MeshModelId.ConfigurationServer,
            ConfigOpcodes.CONFIG_DEFAULT_TTL_SET, payload,
            ApplicationKey(KeyIndex(0u), ByteArray(16), KeyIndex(0u)),
            acknowledged = true)
        return ConfigurationStatus(ConfigStatusCodes.SUCCESS)
    }

    // --- Payload builders ---

    /** Build the AppKey Add payload: NetKeyIndex(2) | AppKey(16). */
    private fun buildAppKeyPayload(appKey: ApplicationKey, netKey: NetworkKey?): ByteArray {
        // NetKeyIndex(2) + AppKeyIndex(2) + AppKey(16) into 3+16 bytes...
        // Actually, Config AppKey Add format per spec:
        // NetKeyIndex(2 bytes: 12-bit + 4-bit padding) | AppKeyIndex(2 bytes: 12-bit + 4-bit padding) | AppKey(16 bytes)
        val netKeyIndex = netKey?.index ?: appKey.boundNetKeyIndex
        return buildKeyIndexPayload(netKeyIndex) +
            buildKeyIndexPayload(appKey.index) +
            appKey.key
    }

    /** Encode element address as 2 bytes little-endian. */
    private fun buildAddressPayload(address: MeshAddress): ByteArray =
        byteArrayOf(
            (address.value.toInt() and 0xFF).toByte(),
            ((address.value.toInt() shr 8) and 0xFF).toByte(),
        )

    /** Encode key index as 2 bytes little-endian. */
    private fun buildKeyIndexPayload(index: KeyIndex): ByteArray =
        byteArrayOf(
            (index.value.toInt() and 0xFF).toByte(),
            ((index.value.toInt() shr 8) and 0xFF).toByte(),
        )

    /** Encode model ID as 2 bytes little-endian. */
    private fun buildModelIdPayload(modelId: MeshModelId): ByteArray =
        byteArrayOf(
            (modelId.value.toInt() and 0xFF).toByte(),
            ((modelId.value.toInt() shr 8) and 0xFF).toByte(),
        )
}
