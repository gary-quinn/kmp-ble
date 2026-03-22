package com.atruedev.kmpble.codec

import com.atruedev.kmpble.BleData

/** Encodes a value of type [T] into a [ByteArray] for BLE transmission. */
public fun interface BleEncoder<in T> {
    public fun encode(value: T): ByteArray
}

/**
 * Decodes a [ByteArray] into a value of type [T].
 *
 * For zero-copy decoding of [BleData] from scanner advertisements or
 * server write handlers, use [BleDataDecoder] instead.
 */
public fun interface BleDecoder<out T> {
    public fun decode(data: ByteArray): T
}

/** Bidirectional codec combining [BleEncoder] and [BleDecoder]. */
public interface BleCodec<T> : BleEncoder<T>, BleDecoder<T>

/**
 * Decodes a [BleData] into a value of type [T] without copying.
 *
 * On iOS, [BleData] wraps `NSData` from CoreBluetooth with zero-copy.
 * A [BleDataDecoder] reads directly from the platform-native buffer via
 * indexed access ([BleData.get]), avoiding the allocation and `memcpy`
 * that [BleData.toByteArray] would incur.
 *
 * ```kotlin
 * val decoder = BleDataDecoder<HeartRate> { data ->
 *     val flags = data[0].toInt()
 *     val bpm = if (flags and 0x01 == 0) data[1].toInt() and 0xFF
 *               else (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
 *     HeartRate(bpm)
 * }
 * ```
 */
public fun interface BleDataDecoder<out T> {
    public fun decode(data: BleData): T
}

/** Combine a standalone [BleEncoder] and [BleDecoder] into a [BleCodec]. */
public fun <T> bleCodec(encoder: BleEncoder<T>, decoder: BleDecoder<T>): BleCodec<T> =
    object : BleCodec<T> {
        override fun encode(value: T): ByteArray = encoder.encode(value)
        override fun decode(data: ByteArray): T = decoder.decode(data)
    }

/** Transform the output of this decoder. */
public fun <A, B> BleDecoder<A>.map(transform: (A) -> B): BleDecoder<B> =
    BleDecoder { data -> transform(decode(data)) }

/** Transform the input of this encoder. */
public fun <A, B> BleEncoder<B>.contramap(transform: (A) -> B): BleEncoder<A> =
    BleEncoder { value -> encode(transform(value)) }

/** Transform both directions of this codec. */
public fun <A, B> BleCodec<A>.bimap(
    encode: (B) -> A,
    decode: (A) -> B,
): BleCodec<B> = bleCodec(
    encoder = contramap(encode),
    decoder = (this as BleDecoder<A>).map(decode),
)

/** Transform the output of this BleData decoder. */
public fun <A, B> BleDataDecoder<A>.map(transform: (A) -> B): BleDataDecoder<B> =
    BleDataDecoder { data -> transform(decode(data)) }

/**
 * Bridge a [BleDecoder] to work with [BleData] input.
 *
 * Incurs a copy via [BleData.toByteArray]. Prefer implementing
 * [BleDataDecoder] directly for zero-copy decoding.
 */
public fun <T> BleDecoder<T>.asBleDataDecoder(): BleDataDecoder<T> =
    BleDataDecoder { data -> decode(data.toByteArray()) }

/** Bridge a [BleDataDecoder] to work with [ByteArray] input. */
public fun <T> BleDataDecoder<T>.asBleDecoder(): BleDecoder<T> =
    BleDecoder { bytes -> decode(BleData(bytes)) }
