package com.atruedev.kmpble.mesh

/**
 * Protocol Data Units for BLE Mesh networking.
 *
 * This file defines the PDU types for each protocol layer:
 * - Bearer layer (ADV + GATT Proxy)
 * - Network layer
 * - Transport layers (Lower + Upper)
 * - Access layer
 */

/**
 * A complete Network PDU ready for transmission over a bearer.
 *
 * Network PDUs are max 29 bytes for non-segmented access messages on
 * the ADV bearer. Larger PDUs may be carried over the GATT proxy bearer
 * (up to the negotiated MTU).
 */
public data class NetworkPdu(
    /** IV Index least significant bit (0 = normal, 1 = IV update in progress). */
    val ivi: Int,

    /** Network ID — 7-bit value identifying the NetKey (derived via K3). */
    val nid: Int,

    /** Control (1) or Access (0) message indicator. */
    val ctl: Int,

    /** Time-To-Live — remaining hop count before message is dropped. */
    val ttl: Int,

    /** 24-bit sequence number for replay protection. */
    val seq: UInt,

    /** Source unicast address. */
    val src: MeshAddress.UnicastAddress,

    /** Destination address (unicast, group, or virtual). */
    val dst: MeshAddress,

    /** Encrypted transport PDU payload. */
    val transportPdu: ByteArray,

    /** Network MIC (32-bit for access, 64-bit for control messages). */
    val netMic: ByteArray,
) {
    /** Whether this is a control message (vs access message). */
    val isControlMessage: Boolean get() = ctl == 1
}

/**
 * Represents the current IV Index state of the mesh network.
 *
 * The IV Index is a 32-bit value that changes during IV Update procedures.
 * It is used as part of the nonce for network-layer encryption to prevent
 * nonce reuse when sequence numbers wrap.
 */
@JvmInline
public value class IvIndex(public val value: UInt) {
    public val isNormalOperation: Boolean get() = true

    public companion object {
        /** Initial IV Index value (provisioning default). */
        public val INITIAL: IvIndex = IvIndex(0u)
    }
}

/**
 * A Transport PDU before network-layer encryption.
 */
public data class TransportPdu(
    /** Whether this is a segmented message. */
    val isSegmented: Boolean,

    /** Application Key Flag — true if encrypted with AppKey, false for DeviceKey. */
    val akf: Boolean,

    /** Application Identifier — 6-bit value derived from AppKey (via K4). */
    val aid: Int,

    /** Sequence number (24-bit, lower portion). */
    val seqZero: Int,

    /** Segment offset (which segment this is, 0-based). */
    val segO: Int,

    /** Last segment number (total segments - 1). */
    val segN: Int,

    /** Payload (encrypted upper transport PDU or segment thereof). */
    val payload: ByteArray,

    /** Transport MIC (32-bit for unsegmented, 64-bit for segmented). */
    val transportMic: ByteArray,
) {
    /** Whether this segment is the final one. */
    val isLastSegment: Boolean get() = segO == segN

    /** Total number of segments. */
    val totalSegments: Int get() = segN + 1
}

/**
 * An Access Layer message for application-level communication.
 *
 * Access messages carry the opcode and parameters for model operations.
 * They are encrypted at the Upper Transport layer with the AppKey.
 */
public data class AccessMessage(
    /** Operation code (1, 2, or 3 bytes depending on value). */
    val opcode: MeshOpcode,

    /** Operation parameters (model-specific). */
    val parameters: ByteArray,

    /** Whether this message requires an acknowledgment. */
    val isAcknowledged: Boolean = true,
)

/**
 * Operation code for mesh model messages.
 *
 * Opcodes are encoded as:
 * - 1 byte: 0x00-0x7F (standard SIG models, no company ID)
 * - 2 bytes: 0x8000-0xBFFF (standard SIG models)
 * - 3 bytes: 0xC00000-0xFFFFFF (vendor-specific models)
 */
@JvmInline
public value class MeshOpcode(public val value: UInt) {
    /** Number of bytes needed to encode this opcode (1, 2, or 3). */
    public val byteCount: Int get() = when {
        value <= 0x7Fu -> 1
        value <= 0xBFFFu -> 2
        else -> 3
    }

    /** Encode this opcode to bytes. */
    public fun toBytes(): ByteArray = when (byteCount) {
        1 -> byteArrayOf(value.toByte())
        2 -> byteArrayOf(
            ((value shr 8) and 0xFFu).toByte(),
            (value and 0xFFu).toByte(),
        )
        3 -> byteArrayOf(
            ((value shr 16) and 0xFFu).toByte(),
            ((value shr 8) and 0xFFu).toByte(),
            (value and 0xFFu).toByte(),
        )
        else -> throw IllegalStateException("Invalid opcode byteCount: $byteCount")
    }
}

/**
 * A complete mesh message sent or received via the public API.
 */
public data class MeshMessage(
    /** Source unicast address. */
    val source: MeshAddress.UnicastAddress,

    /** Destination address. */
    val destination: MeshAddress,

    /** The model operation code. */
    val opcode: MeshOpcode,

    /** Raw operation parameters. */
    val parameters: ByteArray,

    /** Application key used (null for configuration messages sent with DeviceKey). */
    val appKey: ApplicationKey?,
)

/**
 * Response to an acknowledged mesh message.
 */
public data class MeshMessageResponse(
    /** The status opcode from the server. */
    val opcode: MeshOpcode,

    /** Status parameters from the server. */
    val parameters: ByteArray,
)

/**
 * A Proxy PDU as carried over the GATT Proxy bearer.
 */
public data class ProxyPdu(
    /** SAR field: 0x00=Complete, 0x01=First, 0x02=Continuation, 0x03=Last. */
    val sar: ProxySarType,

    /** Message type: Network PDU, Mesh Beacon, Proxy Config, or Provisioning PDU. */
    val messageType: ProxyMessageType,

    /** The payload data. */
    val data: ByteArray,
)

/** Proxy SAR (Segmentation and Reassembly) type. */
public enum class ProxySarType(public val code: Int) {
    COMPLETE(0x00),
    FIRST(0x01),
    CONTINUATION(0x02),
    LAST(0x03),
}

/** Message type carried in a Proxy PDU. */
public enum class ProxyMessageType(public val code: Int) {
    NETWORK_PDU(0x00),
    MESH_BEACON(0x01),
    PROXY_CONFIGURATION(0x02),
    PROVISIONING_PDU(0x03),
}
