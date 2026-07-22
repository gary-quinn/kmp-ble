package com.atruedev.kmpble.mesh.network

import com.atruedev.kmpble.mesh.TransportPdu

/**
 * Lower Transport Layer — Segmentation and Reassembly (SAR).
 *
 * Segments large Upper Transport PDUs into multiple Lower Transport PDUs
 * for transmission over the ADV bearer (29-byte max per Network PDU).
 *
 * Each segment carries 12 bytes of payload. Maximum 32 segments per message,
 * giving a max payload of 384 bytes.
 */
internal class LowerTransportLayer {

    /** Max payload per segment (12 bytes). */
    companion object {
        const val SEGMENT_PAYLOAD_SIZE: Int = 12
        const val MAX_SEGMENTS: Int = 32
        const val MAX_TOTAL_PAYLOAD: Int = SEGMENT_PAYLOAD_SIZE * MAX_SEGMENTS
    }

    /**
     * Segment an Upper Transport PDU into Lower Transport PDUs.
     *
     * @param upperPdu The encrypted upper transport payload.
     * @param akf Application Key Flag (1 for AppKey, 0 for DeviceKey).
     * @param aid Application Identifier (6-bit, derived from AppKey).
     * @param seqZero Base sequence number for this segmented message (13-bit).
     * @param szmic MIC size flag (0 = 32-bit, 1 = 64-bit).
     * @return List of TransportPdu segments.
     */
    fun segment(
        upperPdu: ByteArray,
        akf: Int,
        aid: Int,
        seqZero: Int,
        szmic: Int,
    ): List<TransportPdu> {
        val totalSegments = (upperPdu.size + SEGMENT_PAYLOAD_SIZE - 1) /
            SEGMENT_PAYLOAD_SIZE
        require(totalSegments <= MAX_SEGMENTS) {
            "Payload too large for segmentation: ${upperPdu.size} bytes " +
                "requires $totalSegments segments (max $MAX_SEGMENTS)"
        }

        val segN = totalSegments - 1
        return (0 until totalSegments).map { segO ->
            val offset = segO * SEGMENT_PAYLOAD_SIZE
            val payload = upperPdu.copyOfRange(
                offset, minOf(offset + SEGMENT_PAYLOAD_SIZE, upperPdu.size))

            TransportPdu(
                isSegmented = totalSegments > 1,
                akf = akf != 0,
                aid = aid,
                seqZero = seqZero,
                segO = segO,
                segN = segN,
                payload = payload,
                transportMic = ByteArray(if (szmic != 0) 8 else 4),
            )
        }
    }
}
