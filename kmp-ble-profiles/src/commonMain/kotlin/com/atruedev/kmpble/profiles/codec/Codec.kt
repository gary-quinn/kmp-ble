package com.atruedev.kmpble.profiles.codec

/**
 * Decodes a BLE characteristic value (or framed stream payload) into a typed value.
 *
 * Returns `null` on parse failure — matches the convention used by the built-in
 * SIG profile parsers (e.g. heart rate, glucose).
 */
public fun interface Decoder<out T> {
    public fun decode(bytes: ByteArray): T?
}

/**
 * Encodes a typed value into bytes suitable for a BLE characteristic write,
 * notification payload, or L2CAP CoC frame.
 */
public fun interface Encoder<in T> {
    public fun encode(value: T): ByteArray
}

/**
 * Round-trip codec: pair of [Encoder] and [Decoder] sharing the same wire format.
 *
 * Use a [Codec] when both directions are needed at one call site (e.g. a fake
 * GATT server that round-trips state, or a peer-to-peer stream over L2CAP CoC).
 * For one-way characteristics, prefer the narrower [Encoder] or [Decoder] alone.
 */
public interface Codec<T> : Encoder<T>, Decoder<T>
