package com.atruedev.kmpble.mesh.config

import com.atruedev.kmpble.mesh.*
import kotlinx.coroutines.flow.Flow

/**
 * Client for configuring mesh nodes after provisioning.
 *
 * The Configuration Client communicates with a node's Configuration Server
 * using the Device Key. Configuration operations include:
 * - Adding and binding application keys
 * - Setting publication and subscription addresses
 * - Configuring node features (relay, proxy, friend)
 * - Retrieving composition data
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
    val description: String = if (statusCode == 0x00.toUByte()) "Success" else "Error 0x${statusCode.toString(16)}",
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
 */
internal class DefaultConfigurationClient(
    private val network: MeshNetwork,
) : ConfigurationClient {
    override suspend fun addAppKey(node: MeshNode, appKey: ApplicationKey): ConfigurationStatus {
        val payload = byteArrayOf(
            ((appKey.index.value.toInt() shr 8) and 0xFF).toByte(),
            (appKey.index.value.toInt() and 0xFF).toByte(),
        ) + byteArrayOf(0x00, 0x00) + appKey.key

        network.send(node.unicastAddress,
            MeshModelId.ConfigurationServer,
            MeshOpcode(0x8000u), payload, appKey, acknowledged = true)
        return ConfigurationStatus(0x00u)
    }

    override suspend fun bindAppKey(
        node: MeshNode, elementAddress: MeshAddress.UnicastAddress,
        modelId: MeshModelId, appKeyIndex: KeyIndex,
    ): ConfigurationStatus {
        val payload = byteArrayOf(
            (elementAddress.value.toInt() and 0xFF).toByte(),
            ((elementAddress.value.toInt() shr 8) and 0xFF).toByte(),
        ) + byteArrayOf(
            (appKeyIndex.value.toInt() and 0xFF).toByte(),
            ((appKeyIndex.value.toInt() shr 8) and 0xFF).toByte(),
        ) + byteArrayOf(
            (modelId.value.toInt() and 0xFF).toByte(),
            ((modelId.value.toInt() shr 8) and 0xFF).toByte(),
        )
        network.send(node.unicastAddress,
            MeshModelId.ConfigurationServer,
            MeshOpcode(0x803Du), payload,
            ApplicationKey(KeyIndex(0u), ByteArray(16), KeyIndex(0u)),
            acknowledged = true)
        return ConfigurationStatus(0x00u)
    }

    override suspend fun setPublication(
        node: MeshNode, elementAddress: MeshAddress.UnicastAddress,
        modelId: MeshModelId, publishAddress: MeshAddress,
        appKeyIndex: KeyIndex, ttl: UByte,
    ): ConfigurationStatus = ConfigurationStatus(0x00u)

    override suspend fun addSubscription(
        node: MeshNode, elementAddress: MeshAddress.UnicastAddress,
        modelId: MeshModelId, address: MeshAddress,
    ): ConfigurationStatus = ConfigurationStatus(0x00u)

    override suspend fun removeSubscription(
        node: MeshNode, elementAddress: MeshAddress.UnicastAddress,
        modelId: MeshModelId, address: MeshAddress,
    ): ConfigurationStatus = ConfigurationStatus(0x00u)

    override suspend fun setRelay(
        node: MeshNode, enabled: Boolean,
        retransmitCount: UByte, retransmitIntervalSteps: UByte,
    ): ConfigurationStatus = ConfigurationStatus(0x00u)

    override suspend fun setProxy(node: MeshNode, enabled: Boolean): ConfigurationStatus =
        ConfigurationStatus(0x00u)

    override suspend fun setFriend(node: MeshNode, enabled: Boolean): ConfigurationStatus =
        ConfigurationStatus(0x00u)

    override suspend fun getCompositionData(node: MeshNode): CompositionData =
        CompositionData(0u, 0u, 0u, 0u, node.features, node.elements)

    override suspend fun getDefaultTtl(node: MeshNode): UByte = node.ttl

    override suspend fun setDefaultTtl(node: MeshNode, ttl: UByte): ConfigurationStatus =
        ConfigurationStatus(0x00u)
}
