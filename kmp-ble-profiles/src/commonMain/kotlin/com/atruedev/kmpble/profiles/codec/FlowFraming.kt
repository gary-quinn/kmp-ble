package com.atruedev.kmpble.profiles.codec

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Adapts a stream of byte chunks (e.g. from an L2CAP CoC `incoming` flow) into
 * a stream of complete framed payloads.
 *
 * Each collection allocates its own fresh [Unframer] via [framer], so the same
 * [Framer] instance is safe to share across multiple collectors.
 *
 * If the source flow completes while bytes are still buffered, the partial
 * tail is silently dropped. Inspect the source's terminal state or wire your
 * own diagnostics if that matters.
 *
 * A malformed length prefix throws [FrameTooLargeException] downstream, which
 * cancels the flow. Wrap with `.catch { }` to recover.
 */
public fun Flow<ByteArray>.unframedBy(framer: Framer): Flow<ByteArray> = flow {
    val unframer = framer.unframer()
    collect { chunk ->
        for (frame in unframer.feed(chunk)) {
            emit(frame)
        }
    }
}

/**
 * Combines unframing and decoding into one operator: byte chunks in, typed
 * values out.
 *
 * Frames that fail to decode (decoder returns `null`) are routed to
 * [onDecodeFailure] and dropped from the output stream. Default is a no-op;
 * pass a logger or metrics sink in production to surface corrupted payloads.
 *
 * A malformed length prefix throws [FrameTooLargeException] downstream, which
 * cancels the flow. Wrap with `.catch { }` to recover.
 */
public fun <T> Flow<ByteArray>.decodeFramed(
    decoder: Decoder<T>,
    framer: Framer = LengthPrefixFramer(),
    onDecodeFailure: (ByteArray) -> Unit = {},
): Flow<T> {
    val unframer = framer.unframer()
    return flow {
        collect { chunk ->
            for (frame in unframer.feed(chunk)) {
                val decoded = decoder.decode(frame)
                if (decoded != null) {
                    emit(decoded)
                } else {
                    onDecodeFailure(frame)
                }
            }
        }
    }
}
