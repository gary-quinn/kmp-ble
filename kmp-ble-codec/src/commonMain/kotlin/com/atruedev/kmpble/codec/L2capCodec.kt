package com.atruedev.kmpble.codec

import com.atruedev.kmpble.l2cap.L2capChannel
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
