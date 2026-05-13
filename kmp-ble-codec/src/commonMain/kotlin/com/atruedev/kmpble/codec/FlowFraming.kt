package com.atruedev.kmpble.codec

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.coroutines.cancellation.CancellationException

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
 * Each collection allocates its own fresh [Unframer] via [framer], so the
 * same [Framer] instance is safe to share across multiple collectors and
 * re-collecting the same returned [Flow] starts fresh (no leftover buffered
 * tail from a prior collection).
 *
 * A frame is sent through [decoder]. If the decoder throws (the
 * [BleDecoder] contract for parse failure), the frame's raw bytes are
 * routed to [onDecodeFailure] (default no-op) and dropped from the output
 * stream. Pass a logger or metrics sink in production to surface corrupted
 * payloads.
 *
 * A malformed length prefix is a different class of failure: it throws
 * [FrameTooLargeException] downstream and cancels the flow. Wrap with
 * `.catch { }` to recover.
 */
public fun <T> Flow<ByteArray>.decodeFramed(
    decoder: BleDecoder<T>,
    framer: Framer = LengthPrefixFramer(),
    onDecodeFailure: (ByteArray) -> Unit = {},
): Flow<T> = flow {
    val unframer = framer.unframer()
    collect { chunk ->
        for (frame in unframer.feed(chunk)) {
            val decoded = try {
                decoder.decode(frame)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onDecodeFailure(frame)
                continue
            }
            emit(decoded)
        }
    }
}
