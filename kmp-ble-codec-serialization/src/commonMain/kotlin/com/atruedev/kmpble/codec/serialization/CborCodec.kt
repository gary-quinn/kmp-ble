@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.atruedev.kmpble.codec.serialization

import com.atruedev.kmpble.codec.BleCodec
import kotlinx.serialization.KSerializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.serializer

/**
 * Encodes and decodes [T] as CBOR bytes via kotlinx-serialization.
 *
 * Use for typed payloads on streaming transports (L2CAP CoC, framed GATT
 * notifications) where binary efficiency and schema evolution matter more
 * than human readability. Pairs naturally with
 * [com.atruedev.kmpble.codec.LengthPrefixFramer] for stream framing.
 *
 * ## Example
 *
 * ```kotlin
 * @Serializable
 * data class Reading(val timestampMs: Long, val celsius: Double)
 *
 * val codec = CborCodec(Reading.serializer())
 *
 * val bytes = codec.encode(Reading(1_700_000_000_000, 21.5))
 * val decoded = codec.decode(bytes)
 * ```
 *
 * For ergonomics on `@Serializable` types, prefer the reified
 * [cborCodec] factory.
 *
 * ## Failure modes
 *
 * [decode] throws on malformed CBOR (kotlinx-serialization raises
 * `SerializationException` and subclasses). Callers using
 * [com.atruedev.kmpble.codec.decodeFramed] or
 * [com.atruedev.kmpble.codec.L2capChannel.framedIncoming] receive the
 * thrown bytes through their `onDecodeFailure` callback and the stream
 * continues with the next frame.
 *
 * @property serializer the kotlinx-serialization [KSerializer] for [T].
 * @property cbor the [Cbor] instance controlling format options (tagged values,
 *   field names vs ids, etc.). Defaults to [Cbor.Default].
 */
public class CborCodec<T>(
    private val serializer: KSerializer<T>,
    private val cbor: Cbor = Cbor.Default,
) : BleCodec<T> {
    override fun encode(value: T): ByteArray = cbor.encodeToByteArray(serializer, value)

    override fun decode(data: ByteArray): T = cbor.decodeFromByteArray(serializer, data)
}

/**
 * Reified factory for [CborCodec] on `@Serializable` types.
 *
 * ```kotlin
 * @Serializable data class Reading(val v: Int)
 * val codec = cborCodec<Reading>()
 * ```
 *
 * Pass a custom [cbor] to control format options:
 *
 * ```kotlin
 * val codec = cborCodec<Reading>(Cbor { ignoreUnknownKeys = true })
 * ```
 */
public inline fun <reified T> cborCodec(cbor: Cbor = Cbor.Default): CborCodec<T> =
    CborCodec(serializer(), cbor)
