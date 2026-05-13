@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.atruedev.kmpble.sample

import com.atruedev.kmpble.codec.serialization.cborCodec
import kotlinx.serialization.Serializable

/**
 * Fragment of a larger blob being streamed over a framed L2CAP channel.
 *
 * Each [BlobChunk] is one CBOR-encoded, length-prefix-framed L2CAP message
 * from the server. [totalBytes] is repeated in every chunk so the receiver
 * can show progress without a separate header; [eof] flags the final chunk.
 *
 * The on-the-wire shape lets the demo expose three layers of fragmentation:
 *
 * - L2CAP SDU MTU (`channel.mtu`): negotiated by the controller; spec
 *   ceiling 65535 octets, real phones often 672-2048.
 * - OS read chunk size: the [ByteArray] sizes emitted by `channel.incoming`
 *   before any app-level framing; reflects how the OS buffers wire SDUs.
 * - App frame size: [bytes].size for each decoded [BlobChunk], i.e. the
 *   producer's chosen chunk size capped by the framer's `maxFrameSize`.
 */
@Serializable
data class BlobChunk(
    val seq: Int,
    val totalBytes: Long,
    val eof: Boolean,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BlobChunk) return false
        return seq == other.seq &&
            totalBytes == other.totalBytes &&
            eof == other.eof &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = seq
        result = 31 * result + totalBytes.hashCode()
        result = 31 * result + eof.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

val BlobChunkCodec = cborCodec<BlobChunk>()
