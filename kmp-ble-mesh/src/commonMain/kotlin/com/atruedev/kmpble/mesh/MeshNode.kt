package com.atruedev.kmpble.mesh

/**
 * A provisioned node on the BLE Mesh network.
 *
 * Each node is identified by its primary unicast address and contains one
 * or more [MeshElement]s. Each element has its own unicast address and
 * hosts one or more models.
 *
 * Node features (relay, proxy, friend, lowPower) define the node's
 * capabilities within the mesh network.
 */
public data class MeshNode(
    /** Primary unicast address of this node (address of element 0). */
    val unicastAddress: MeshAddress.UnicastAddress,

    /** Device key derived during provisioning — unique to this node. */
    val deviceKey: DeviceKey,

    /** Elements hosted by this node. At least one element (primary). */
    val elements: List<MeshElement>,

    /** Node feature flags. */
    val features: NodeFeatures = NodeFeatures(),

    /** Default TTL value used when this node originates messages. */
    val ttl: UByte = DEFAULT_TTL,

    /** Network keys known to this node. */
    val networkKeys: List<NetworkKey> = emptyList(),

    /** Application keys bound to models on this node. */
    val applicationKeys: List<ApplicationKey> = emptyList(),
) {
    /** The primary element (element index 0). */
    val primaryElement: MeshElement get() = elements.first()

    public companion object {
        /** Default TTL for outbound mesh messages. */
        public val DEFAULT_TTL: UByte = 5u
    }
}

/**
 * An addressable entity within a [MeshNode].
 *
 * Each element has a unique unicast address derived from the node's primary
 * address plus the element index. Elements host models that define the
 * element's behavior.
 */
public data class MeshElement(
    /** Zero-based index of this element within its parent node. */
    val index: Int,

    /** The unicast address of this element (primaryAddress + index). */
    val unicastAddress: MeshAddress.UnicastAddress,

    /** Physical or logical location descriptor. */
    val location: ElementLocation = ElementLocation.UNKNOWN,

    /** Models hosted by this element. */
    val models: List<MeshModelId> = emptyList(),
) {
    init {
        require(index >= 0) { "Element index must be non-negative: $index" }
    }
}

/**
 * Features supported by a mesh node.
 */
public data class NodeFeatures(
    /** Node can relay mesh messages (forward PDUs on ADV bearer). */
    val relay: Boolean = false,

    /** Node supports GATT Proxy protocol (bridge between GATT and ADV bearers). */
    val proxy: Boolean = false,

    /** Node acts as a Friend node (caches messages for LPNs). */
    val friend: Boolean = false,

    /** Node is a Low Power Node (polls a Friend for cached messages). */
    val lowPower: Boolean = false,
)
