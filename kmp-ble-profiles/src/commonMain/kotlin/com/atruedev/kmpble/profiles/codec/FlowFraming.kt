package com.atruedev.kmpble.profiles.codec

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Adapts a stream of byte chunks (e.g. from an L2CAP CoC `incoming` flow) into
 * a stream of complete framed payloads.
 *
 * The [unframer] is consumed lazily - partial frames are buffered until the
 * next chunk completes them. If the source flow completes while a partial
 * frame is still buffered, the partial frame is dropped silently.
 *
 * Pass a fresh [Unframer] per collection - the unframer is stateful and not
 * safe to share across concurrent collectors.
 */
public fun Flow<ByteArray>.unframedBy(unframer: Unframer): Flow<ByteArray> = flow {
    collect { chunk ->
        for (frame in unframer.feed(chunk)) {
            emit(frame)
        }
    }
}

/**
 * Combines [unframedBy] and [Decoder] into one operator: byte chunks in,
 * typed values out. Frames that fail to decode (decoder returns `null`) are
 * dropped.
 *
 * The default [LengthPrefixFramer] uses the standard 1 MiB frame cap.
 */
public fun <T> Flow<ByteArray>.decodeFramed(
    decoder: Decoder<T>,
    framer: LengthPrefixFramer = LengthPrefixFramer(),
): Flow<T> {
    val unframer = framer.unframer()
    return flow {
        collect { chunk ->
            for (frame in unframer.feed(chunk)) {
                val decoded = decoder.decode(frame)
                if (decoded != null) emit(decoded)
            }
        }
    }
}
