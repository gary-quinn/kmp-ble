package com.atruedev.kmpble.codec

/**
 * Wraps and recovers payloads on a stream transport (L2CAP CoC, an MTU-spanning
 * concatenation, etc.).
 *
 * A single [Framer] instance is both the encoder ([frame]) and the factory for
 * fresh decoders ([unframer]). Decoders are stateful, so each receive stream
 * needs its own; one [Framer] may produce many.
 */
public interface Framer {
    public fun frame(payload: ByteArray): ByteArray

    public fun unframer(): Unframer
}

/**
 * Stateful inverse of [Framer.frame]. Implementations buffer partial frames
 * across [feed] calls; each call returns zero or more complete payloads.
 *
 * If the source stream ends with [pendingBytes] > 0, a partial frame was in
 * flight when the stream closed. Callers that care about completeness should
 * check this after the stream completes.
 *
 * Not thread-safe.
 */
public interface Unframer {
    public fun feed(bytes: ByteArray): List<ByteArray>

    public fun pendingBytes(): Int
}

/**
 * Thrown from [Unframer.feed] when a length prefix exceeds the configured cap.
 * Indicates a malformed stream or hostile peer; the [Unframer] should not be
 * reused.
 *
 * When propagated through [unframedBy] / [decodeFramed], this exception
 * terminates the downstream [kotlinx.coroutines.flow.Flow] with an error.
 * Consumers that want to recover should wrap the Flow with `.catch { }`.
 */
public class FrameTooLargeException(
    public val size: Long,
    public val maxSize: Int,
) : RuntimeException("Frame too large: $size > $maxSize")

/**
 * Length-prefix framing: each frame is `[length: uint32 LE][payload: bytes]`.
 *
 * Length is the payload size, not including the 4-byte header. Frames larger
 * than [maxFrameSize] cause [Unframer.feed] to throw [FrameTooLargeException]
 * to prevent unbounded buffering by a hostile peer.
 *
 * The default cap is 64 KiB, deliberately small for BLE-scale workloads where
 * a single frame typically encodes one event. Raise it explicitly when a use
 * case demands larger payloads.
 */
public class LengthPrefixFramer(
    private val maxFrameSize: Int = DEFAULT_MAX_FRAME_SIZE,
) : Framer {
    init {
        require(maxFrameSize > 0) { "maxFrameSize must be positive: $maxFrameSize" }
    }

    override fun frame(payload: ByteArray): ByteArray {
        require(payload.size <= maxFrameSize) {
            "payload too large: ${payload.size} > $maxFrameSize"
        }
        val out = ByteArray(HEADER_SIZE + payload.size)
        val length = payload.size
        out[0] = (length and 0xFF).toByte()
        out[1] = ((length shr 8) and 0xFF).toByte()
        out[2] = ((length shr 16) and 0xFF).toByte()
        out[3] = ((length shr 24) and 0xFF).toByte()
        payload.copyInto(out, HEADER_SIZE)
        return out
    }

    override fun unframer(): Unframer = LengthPrefixUnframer(maxFrameSize)

    public companion object {
        public const val HEADER_SIZE: Int = 4
        public const val DEFAULT_MAX_FRAME_SIZE: Int = 64 * 1024
    }
}

private class LengthPrefixUnframer(private val maxFrameSize: Int) : Unframer {
    private var buffer: ByteArray = ByteArray(INITIAL_CAPACITY)
    private var writePos: Int = 0
    private var readPos: Int = 0

    override fun feed(bytes: ByteArray): List<ByteArray> {
        if (bytes.isEmpty()) return emptyList()
        append(bytes)
        return drainFrames()
    }

    override fun pendingBytes(): Int = writePos - readPos

    private fun append(bytes: ByteArray) {
        val needed = writePos + bytes.size
        if (needed > buffer.size && readPos > 0) {
            compact()
        }
        if (writePos + bytes.size > buffer.size) {
            var newCap = buffer.size
            while (newCap < writePos + bytes.size) newCap *= 2
            buffer = buffer.copyOf(newCap)
        }
        bytes.copyInto(buffer, writePos)
        writePos += bytes.size
    }

    private fun drainFrames(): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        while (true) {
            val remaining = writePos - readPos
            if (remaining < LengthPrefixFramer.HEADER_SIZE) break
            val length = readLengthAt(readPos)
            if (length > maxFrameSize) {
                throw FrameTooLargeException(length, maxFrameSize)
            }
            val total = LengthPrefixFramer.HEADER_SIZE + length.toInt()
            if (remaining < total) break
            out.add(buffer.copyOfRange(readPos + LengthPrefixFramer.HEADER_SIZE, readPos + total))
            readPos += total
        }
        if (readPos == writePos) {
            readPos = 0
            writePos = 0
        } else if (readPos >= COMPACT_THRESHOLD) {
            compact()
        }
        return out
    }

    private fun compact() {
        buffer.copyInto(buffer, 0, readPos, writePos)
        writePos -= readPos
        readPos = 0
    }

    private fun readLengthAt(offset: Int): Long {
        val b0 = (buffer[offset].toInt() and 0xFF).toLong()
        val b1 = (buffer[offset + 1].toInt() and 0xFF).toLong()
        val b2 = (buffer[offset + 2].toInt() and 0xFF).toLong()
        val b3 = (buffer[offset + 3].toInt() and 0xFF).toLong()
        return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    }

    private companion object {
        const val INITIAL_CAPACITY = 256
        const val COMPACT_THRESHOLD = 4096
    }
}
