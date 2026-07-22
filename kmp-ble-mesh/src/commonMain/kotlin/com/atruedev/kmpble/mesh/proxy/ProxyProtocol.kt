package com.atruedev.kmpble.mesh.proxy

import com.atruedev.kmpble.mesh.ProxyMessageType
import com.atruedev.kmpble.mesh.ProxyPdu
import com.atruedev.kmpble.mesh.ProxySarType

/**
 * Proxy Protocol -- PDUs, SAR, and message formatting for the GATT Proxy bearer.
 *
 * The GATT Proxy bearer carries Proxy PDUs over the Mesh Proxy Service
 * (UUID 1828). Each Proxy PDU consists of a SAR byte, a message type byte,
 * and the payload data.
 *
 * ## Double SAR Architecture
 *
 * There are TWO independent SAR layers in the mesh stack:
 *
 * 1. **Proxy SAR** (this file): Splits Network PDUs across GATT writes
 *    when the PDU exceeds (MTU - 3) bytes. Reassembles on the receiver.
 *
 * 2. **Mesh Transport SAR** (LowerTransportLayer): Splits Access messages
 *    across Network PDUs when the payload exceeds 15 bytes.
 *
 * These must not be conflated. Proxy SAR is transparent to the mesh stack.
 */
internal object ProxyProtocol {
    /** GATT MTU overhead: SAR byte + message type byte = 2 bytes. */
    const val PROXY_HEADER_SIZE: Int = 2

    /**
     * Encode a complete Proxy PDU for transmission.
     *
     * @param messageType Type of the payload (Network PDU, Beacon, Config, Provisioning).
     * @param sarType SAR type for this segment (Complete, First, Continuation, Last).
     * @param payload The payload data.
     * @return The encoded Proxy PDU bytes.
     */
    fun encode(
        messageType: ProxyMessageType,
        sarType: ProxySarType,
        payload: ByteArray,
    ): ByteArray = byteArrayOf(sarType.code.toByte(), messageType.code.toByte()) + payload

    /**
     * Decode a received Proxy PDU from raw bytes.
     *
     * @param data Raw bytes from GATT notification/write.
     * @return The decoded ProxyPdu, or null if the data is too short.
     */
    fun decode(data: ByteArray): ProxyPdu? {
        if (data.size < PROXY_HEADER_SIZE) return null

        val sarType = when (data[0].toInt() and 0xFF) {
            0x00 -> ProxySarType.COMPLETE
            0x01 -> ProxySarType.FIRST
            0x02 -> ProxySarType.CONTINUATION
            0x03 -> ProxySarType.LAST
            else -> return null
        }

        val messageType = when (data[1].toInt() and 0xFF) {
            0x00 -> ProxyMessageType.NETWORK_PDU
            0x01 -> ProxyMessageType.MESH_BEACON
            0x02 -> ProxyMessageType.PROXY_CONFIGURATION
            0x03 -> ProxyMessageType.PROVISIONING_PDU
            else -> return null
        }

        return ProxyPdu(
            sar = sarType,
            messageType = messageType,
            data = data.copyOfRange(PROXY_HEADER_SIZE, data.size),
        )
    }

    /**
     * Segment a Network PDU into Proxy SAR segments based on GATT MTU.
     *
     * @param networkPdu The Network PDU to segment.
     * @param mtu The negotiated ATT MTU (minus 3 bytes for ATT header).
     * @return List of Proxy PDU segments.
     */
    fun segmentForGatt(networkPdu: ByteArray, mtu: Int): List<ByteArray> {
        val maxPayloadPerSegment = mtu - PROXY_HEADER_SIZE
        if (networkPdu.size <= maxPayloadPerSegment) {
            return listOf(encode(ProxyMessageType.NETWORK_PDU,
                ProxySarType.COMPLETE, networkPdu))
        }

        val segments = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < networkPdu.size) {
            val remaining = networkPdu.size - offset
            val segmentSize = minOf(remaining, maxPayloadPerSegment)
            val segmentData = networkPdu.copyOfRange(offset, offset + segmentSize)
            val sarType = when {
                offset == 0 -> ProxySarType.FIRST
                offset + segmentSize >= networkPdu.size -> ProxySarType.LAST
                else -> ProxySarType.CONTINUATION
            }
            segments.add(encode(ProxyMessageType.NETWORK_PDU, sarType,
                segmentData))
            offset += segmentSize
        }
        return segments
    }

    /**
     * Reassemble Proxy SAR segments into a complete payload.
     *
     * @param segments Ordered list of Proxy PDU bytes.
     * @return The reassembled payload, or null if segments are incomplete.
     */
    fun reassemble(segments: List<ByteArray>): ByteArray? {
        if (segments.isEmpty()) return null
        if (segments.size == 1) {
            val pdu = decode(segments[0]) ?: return null
            return if (pdu.sar == ProxySarType.COMPLETE) pdu.data else null
        }

        val decoded = segments.mapNotNull { decode(it) }
        val firstPdu = decoded.firstOrNull()
        val lastPdu = decoded.lastOrNull()

        if (firstPdu?.sar != ProxySarType.FIRST ||
            lastPdu?.sar != ProxySarType.LAST) return null

        val buffer = ByteArray(decoded.sumOf { it.data.size })
        var offset = 0
        decoded.forEach { pdu ->
            pdu.data.copyInto(buffer, offset)
            offset += pdu.data.size
        }
        return buffer
    }
}
