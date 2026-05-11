package com.atruedev.kmpble.profiles.codec

import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlin.uuid.Uuid

/**
 * Thrown when a service+characteristic UUID pair does not resolve against the
 * peripheral's discovered services.
 */
public class CharacteristicNotFoundException(
    public val serviceUuid: Uuid,
    public val characteristicUuid: Uuid,
) : RuntimeException("Characteristic $characteristicUuid not found in service $serviceUuid")

/**
 * Thrown when a [Decoder] returns `null` for a value read from a characteristic.
 * The raw [bytes] are preserved so callers can log or inspect the malformed payload.
 */
public class DecodeFailureException(
    public val bytes: ByteArray,
) : RuntimeException("Decoder rejected ${bytes.size} bytes")

/**
 * Reads a characteristic and decodes the value with [decoder].
 *
 * Returns a [Result] so callers can distinguish three outcomes:
 * - success: decoded value
 * - failure with [CharacteristicNotFoundException]: characteristic absent
 * - failure with [DecodeFailureException]: payload rejected by [decoder]
 * - failure with any other exception thrown by the underlying read (BLE error)
 */
public suspend fun <T> Peripheral.readAs(
    serviceUuid: Uuid,
    characteristicUuid: Uuid,
    decoder: Decoder<T>,
): Result<T> {
    val char = findCharacteristic(serviceUuid, characteristicUuid)
        ?: return Result.failure(CharacteristicNotFoundException(serviceUuid, characteristicUuid))
    return runCatching {
        val bytes = read(char)
        decoder.decode(bytes) ?: throw DecodeFailureException(bytes)
    }
}

/**
 * Encodes [value] with [encoder] and writes it to a characteristic.
 *
 * Accepts an [Encoder] (not a full [Codec]) because write paths never need a
 * decoder. Returns [Result.failure] with [CharacteristicNotFoundException] if
 * the characteristic is absent, or with any exception thrown by the underlying
 * write (BLE error).
 */
public suspend fun <T> Peripheral.writeAs(
    serviceUuid: Uuid,
    characteristicUuid: Uuid,
    value: T,
    encoder: Encoder<T>,
    writeType: WriteType = WriteType.WithResponse,
): Result<Unit> {
    val char = findCharacteristic(serviceUuid, characteristicUuid)
        ?: return Result.failure(CharacteristicNotFoundException(serviceUuid, characteristicUuid))
    return runCatching { write(char, encoder.encode(value), writeType) }
}

/**
 * Observes notifications/indications from a characteristic, decoding each
 * payload with [decoder]. Returns an empty flow if the characteristic is
 * absent, matching the lenient convention used by built-in SIG profiles
 * (see [com.atruedev.kmpble.profiles.heartrate.heartRateMeasurements]).
 *
 * Payloads that fail to decode are routed to [onDecodeFailure] (default no-op)
 * and dropped from the output stream. Mirrors the [decodeFramed] error model.
 */
public fun <T> Peripheral.observeAs(
    serviceUuid: Uuid,
    characteristicUuid: Uuid,
    decoder: Decoder<T>,
    backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
    onDecodeFailure: (ByteArray) -> Unit = {},
): Flow<T> {
    val char = findCharacteristic(serviceUuid, characteristicUuid) ?: return emptyFlow()
    return observeValues(char, backpressure).mapNotNull { bytes ->
        val decoded = decoder.decode(bytes)
        if (decoded == null) onDecodeFailure(bytes)
        decoded
    }
}
