package com.atruedev.kmpble.mesh.internal

import com.atruedev.kmpble.mesh.TransportPdu
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Reassembles segmented mesh messages at the Lower Transport layer.
 *
 * Segmented messages are identified by (source address, SeqZero) pairs.
 * Segments may arrive out of order. The assembler collects segments
 * until all have arrived or a timeout expires.
 *
 * Max 32 segments × 12 bytes payload = 384 bytes per message.
 */
internal class SegmentedMessageAssembler(
    private val timeout: Duration = 20.seconds,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {
    private val pending = mutableMapOf<Long, PendingAssembly>()

    data class PendingAssembly(
        val src: Int,
        val seqZero: Int,
        val totalSegments: Int,
        val segments: Array<ByteArray?>,
        val receivedMask: BooleanArray,
        val createdAt: kotlin.time.TimeMark = kotlin.time.TimeSource.Monotonic.markNow(),
    ) {
        val isComplete: Boolean get() = receivedMask.all { it }
    }

    /**
     * Add a segment to the assembly buffer.
     *
     * @param src Source address.
     * @param pdu The lower transport PDU segment.
     * @return The fully reassembled payload if complete, or null if still pending.
     */
    fun addSegment(src: Int, pdu: TransportPdu): ByteArray? {
        val key = assemblyKey(src, pdu.seqZero)
        val assembly = pending.getOrPut(key) {
            val totalSegments = pdu.segN + 1
            require(totalSegments in 1..32) {
                "Invalid segment count: $totalSegments"
            }
            PendingAssembly(
                src = src,
                seqZero = pdu.seqZero,
                totalSegments = totalSegments,
                segments = arrayOfNulls(totalSegments),
                receivedMask = BooleanArray(totalSegments),
            )
        }

        // Store this segment
        if (pdu.segO < assembly.totalSegments) {
            assembly.segments[pdu.segO] = pdu.payload
            assembly.receivedMask[pdu.segO] = true
        }

        // Check if complete
        return if (assembly.isComplete) {
            pending.remove(key)
            val totalSize = assembly.segments.sumOf { it?.size ?: 0 }
            val result = ByteArray(totalSize)
            var offset = 0
            for (segment in assembly.segments) {
                segment!!.copyInto(result, offset)
                offset += segment.size
            }
            result
        } else {
            null
        }
    }

    /** Clean up expired assemblies. */
    fun cleanup() {
        val now = kotlin.time.TimeSource.Monotonic.markNow()
        pending.entries.removeAll { (_, assembly) ->
            assembly.createdAt.elapsedNow() > timeout
        }
    }

    /** Cancel all pending assemblies. */
    fun cancelAll() { pending.clear() }

    private fun assemblyKey(src: Int, seqZero: Int): Long =
        ((src.toLong() and 0xFFFF) shl 16) or (seqZero.toLong() and 0x1FFF)
}
