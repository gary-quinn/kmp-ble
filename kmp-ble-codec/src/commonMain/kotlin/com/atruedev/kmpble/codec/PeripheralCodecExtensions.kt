package com.atruedev.kmpble.codec

import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlin.uuid.Uuid

/**
 * Sealed root of failures raised by [readAs] / [writeAs] / [observeAs] that
 * originate inside the codec layer (as opposed to the underlying BLE op).
 *
 * Match exhaustively when distinguishing codec failures from arbitrary BLE
 * errors that may also surface via [Result.failure]:
 *
 * ```
 * when (val cause = result.exceptionOrNull()) {
 *     is PeripheralCodecException -> when (cause) {
 *         is CharacteristicNotFoundException -> ...
 *         is DecodeFailureException -> ...
 *     }
 *     else -> ... // BLE error, cancellation, etc.
 * }
 * ```
 */
public sealed class PeripheralCodecException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * Thrown when a service+characteristic UUID pair does not resolve against the
 * peripheral's discovered services.
 */
public class CharacteristicNotFoundException(
    public val serviceUuid: Uuid,
    public val characteristicUuid: Uuid,
) : PeripheralCodecException("Characteristic $characteristicUuid not found in service $serviceUuid")

/**
 * Thrown when a [BleDecoder] rejects a value read from a characteristic
 * (i.e. raises any exception during decoding). The raw [bytes] are preserved
 * so callers can log or inspect the malformed payload, and the underlying
 * decoder exception is preserved as the [cause].
 */
public class DecodeFailureException(
    public val bytes: ByteArray,
    cause: Throwable,
) : PeripheralCodecException("Decoder rejected ${bytes.size} bytes", cause)

/**
 * Reads a characteristic by service+characteristic UUID and decodes the value.
 *
 * Returns a [Result] so callers can distinguish three outcomes:
 * - success: decoded value
 * - failure with [CharacteristicNotFoundException]: characteristic absent
 *   after service discovery
 * - failure with [DecodeFailureException]: read succeeded but [decoder]
 *   threw on the raw bytes
 * - failure with any other exception thrown by the underlying read (BLE
 *   error, cancellation propagation, etc.)
 *
 * Use the [Peripheral.read] overload that takes a [com.atruedev.kmpble.gatt.Characteristic]
 * directly when you already hold a reference and prefer raw exception
 * propagation.
 */
public suspend fun <T> Peripheral.readAs(
    serviceUuid: Uuid,
    characteristicUuid: Uuid,
    decoder: BleDecoder<T>,
): Result<T> {
    val char = findCharacteristic(serviceUuid, characteristicUuid)
        ?: return Result.failure(CharacteristicNotFoundException(serviceUuid, characteristicUuid))
    return runCatching {
        val bytes = read(char)
        try {
            decoder.decode(bytes)
        } catch (e: Exception) {
            throw DecodeFailureException(bytes, e)
        }
    }
}

/**
 * Encodes [value] with [encoder] and writes it to a characteristic addressed
 * by service+characteristic UUID.
 *
 * Accepts a [BleEncoder] (not a full [BleCodec]) because write paths never
 * need a decoder. Returns [Result.failure] with [CharacteristicNotFoundException]
 * if the characteristic is absent, or with any exception thrown by the
 * underlying write.
 */
public suspend fun <T> Peripheral.writeAs(
    serviceUuid: Uuid,
    characteristicUuid: Uuid,
    value: T,
    encoder: BleEncoder<T>,
    writeType: WriteType = WriteType.WithResponse,
): Result<Unit> {
    val char = findCharacteristic(serviceUuid, characteristicUuid)
        ?: return Result.failure(CharacteristicNotFoundException(serviceUuid, characteristicUuid))
    return runCatching { write(char, encoder.encode(value), writeType) }
}

/**
 * Observes notifications/indications from a characteristic addressed by
 * service+characteristic UUID, decoding each payload with [decoder].
 *
 * Returns an empty flow if the characteristic is absent, matching the lenient
 * convention used by built-in SIG profiles. Payloads that fail to decode
 * (the decoder throws) are routed to [onDecodeFailure] (default no-op) and
 * dropped from the output stream.
 */
public fun <T> Peripheral.observeAs(
    serviceUuid: Uuid,
    characteristicUuid: Uuid,
    decoder: BleDecoder<T>,
    backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
    onDecodeFailure: (ByteArray) -> Unit = {},
): Flow<T> {
    val char = findCharacteristic(serviceUuid, characteristicUuid) ?: return emptyFlow()
    return flow {
        observeValues(char, backpressure).collect { bytes ->
            val decoded = try {
                decoder.decode(bytes)
            } catch (e: Exception) {
                onDecodeFailure(bytes)
                return@collect
            }
            emit(decoded)
        }
    }
}
