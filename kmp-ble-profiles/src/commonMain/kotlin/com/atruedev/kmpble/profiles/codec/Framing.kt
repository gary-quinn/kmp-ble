package com.atruedev.kmpble.profiles.codec

import com.atruedev.kmpble.profiles.parsing.BleByteWriter

/**
 * Wraps a payload with a frame header so multiple payloads can travel safely
 * over a stream transport (L2CAP CoC, an MTU-spanning concatenation, etc.).
 *
 * Pair with [Unframer] to recover payloads on the receive side.
 */
public interface Framer {
    public fun frame(payload: ByteArray): ByteArray
}

/**
 * Stateful inverse of [Framer]. Implementations buffer partial frames across
 * [feed] calls; each call returns zero or more complete payloads.
 *
 * Not thread-safe - one [Unframer] per stream.
 */
public interface Unframer {
    public fun feed(bytes: ByteArray): List<ByteArray>
}

/**
 * Thrown when [Unframer.feed] reads a length prefix that exceeds the configured
 * [LengthPrefixFramer] cap. Indicates either a malformed stream or a buggy /
 * malicious peer; the unframer should not be reused afterwards.
 */
public class FrameTooLargeException(
    public val size: Long,
    public val maxSize: Int,
) : RuntimeException("Frame too large: $size > $maxSize")

/**
 * Length-prefix framing: each frame is `[length: uint32 LE][payload: bytes]`.
 *
 * - Length is the payload size, not including the 4-byte header
 * - Frames larger than [maxFrameSize] cause [Unframer.feed] to throw
 *   [FrameTooLargeException] to prevent unbounded buffering by a hostile peer
 *
 * The default 1 MiB cap is conservative for BLE L2CAP CoC (typical MTU 2-64 KB,
 * typical application frames a few KB).
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
        return BleByteWriter(initialCapacity = payload.size + HEADER_SIZE)
            .writeUInt32(payload.size.toLong())
            .writeBytes(payload)
            .toByteArray()
    }

    public fun unframer(): Unframer = LengthPrefixUnframer(maxFrameSize)

    public companion object {
        public const val HEADER_SIZE: Int = 4
        public const val DEFAULT_MAX_FRAME_SIZE: Int = 1 shl 20
    }
}

private class LengthPrefixUnframer(private val maxFrameSize: Int) : Unframer {
    private var pending: ByteArray = EMPTY

    override fun feed(bytes: ByteArray): List<ByteArray> {
        if (bytes.isEmpty()) return emptyList()
        pending = if (pending.isEmpty()) bytes.copyOf() else pending + bytes

        val out = mutableListOf<ByteArray>()
        var cursor = 0
        while (true) {
            val remaining = pending.size - cursor
            if (remaining < LengthPrefixFramer.HEADER_SIZE) break
            val length = readLengthAt(cursor)
            if (length > maxFrameSize) {
                throw FrameTooLargeException(length, maxFrameSize)
            }
            val total = LengthPrefixFramer.HEADER_SIZE + length.toInt()
            if (remaining < total) break
            out.add(
                pending.copyOfRange(
                    cursor + LengthPrefixFramer.HEADER_SIZE,
                    cursor + total,
                ),
            )
            cursor += total
        }
        pending = if (cursor == pending.size) EMPTY else pending.copyOfRange(cursor, pending.size)
        return out
    }

    private fun readLengthAt(offset: Int): Long {
        val b0 = (pending[offset].toInt() and 0xFF).toLong()
        val b1 = (pending[offset + 1].toInt() and 0xFF).toLong()
        val b2 = (pending[offset + 2].toInt() and 0xFF).toLong()
        val b3 = (pending[offset + 3].toInt() and 0xFF).toLong()
        return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    }

    private companion object {
        val EMPTY = ByteArray(0)
    }
}
