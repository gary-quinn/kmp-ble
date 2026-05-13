package com.atruedev.kmpble.codec

import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.l2cap.L2capListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Decode each incoming L2CAP packet into a typed value. */
public fun <T> L2capChannel.incoming(decoder: BleDecoder<T>): Flow<T> =
    incoming.map(decoder::decode)

/** Encode a value and write it to the L2CAP channel. */
public suspend fun <T> L2capChannel.write(value: T, encoder: BleEncoder<T>): Unit =
    write(encoder.encode(value))

/**
 * Decode framed payloads from the L2CAP incoming stream.
 *
 * Pairs with [writeFramed]. The remote side frames each logical message with
 * [framer], and this side recovers the framed bytes back to typed values.
 * Use when one L2CAP packet does not correspond 1:1 to one logical message -
 * for example continuous telemetry streams where the OS may coalesce or
 * split frames at the transport layer.
 *
 * The framer configuration (especially `maxFrameSize`) must match the
 * producer's; a sender that frames a 100 KiB payload against a receiver
 * holding the default 64 KiB cap raises [FrameTooLargeException] here.
 *
 * Decoder failures on individual frames are routed to [onDecodeFailure] and
 * dropped from the output stream. A malformed length prefix
 * ([FrameTooLargeException]) terminates the flow; wrap with `.catch { }` to
 * recover.
 */
public fun <T> L2capChannel.framedIncoming(
    decoder: BleDecoder<T>,
    framer: Framer = LengthPrefixFramer(),
    onDecodeFailure: (ByteArray) -> Unit = {},
): Flow<T> = incoming.decodeFramed(decoder, framer, onDecodeFailure)

/**
 * Encode a value, frame the bytes, and write the framed packet.
 *
 * Pairs with [framedIncoming]. The framer prefixes the encoded payload with
 * a length header so the receiver can recover boundaries independent of how
 * the transport delivers chunks. Throws if the encoded payload exceeds the
 * framer's `maxFrameSize`; no bytes are written in that case.
 */
public suspend fun <T> L2capChannel.writeFramed(
    value: T,
    encoder: BleEncoder<T>,
    framer: Framer = LengthPrefixFramer(),
): Unit = write(framer.frame(encoder.encode(value)))

/**
 * Accept incoming L2CAP connections and pre-wrap each one as a
 * [TypedL2capChannel] using the supplied [codec] and [framer].
 *
 * Symmetric server-side counterpart to client-side [framedIncoming] +
 * [writeFramed]: instead of accepting raw [L2capChannel] connections from
 * [L2capListener.incoming] and wiring framing manually for each, callers
 * receive ready-to-use typed channels. Each accepted channel runs its own
 * unframer; per-channel decoder failures route to [onDecodeFailure].
 *
 * Listener lifecycle is unchanged - this is a view over [L2capListener.incoming].
 *
 * Returns a plain [Flow], not the [kotlinx.coroutines.flow.SharedFlow] that
 * [L2capListener.incoming] exposes. Callers that need `subscriptionCount`
 * or `onSubscription` for race-free wiring should collect
 * [L2capListener.incoming] directly and wrap each channel themselves.
 */
public fun <T> L2capListener.framedConnections(
    codec: BleCodec<T>,
    framer: Framer = LengthPrefixFramer(),
    onDecodeFailure: (ByteArray) -> Unit = {},
): Flow<TypedL2capChannel<T>> =
    incoming.map { channel ->
        TypedL2capChannel(
            channel = channel,
            incoming = channel.framedIncoming(codec, framer, onDecodeFailure),
            codec = codec,
            framer = framer,
        )
    }
