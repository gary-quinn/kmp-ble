package com.atruedev.kmpble.profiles.codec

import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlin.uuid.Uuid

/**
 * Reads a characteristic and decodes the value with [decoder].
 *
 * Returns `null` if the characteristic is not present after service discovery
 * or if [decoder] returns `null` for the read bytes.
 */
public suspend fun <T> Peripheral.readAs(
    serviceUuid: Uuid,
    characteristicUuid: Uuid,
    decoder: Decoder<T>,
): T? {
    val char = findCharacteristic(serviceUuid, characteristicUuid) ?: return null
    return decoder.decode(read(char))
}

/**
 * Encodes [value] with [encoder] and writes it to a characteristic.
 *
 * Silently no-ops if the characteristic is not present after service discovery;
 * use [Peripheral.findCharacteristic] directly when presence matters to the caller.
 */
public suspend fun <T> Peripheral.writeAs(
    serviceUuid: Uuid,
    characteristicUuid: Uuid,
    value: T,
    encoder: Encoder<T>,
    writeType: WriteType = WriteType.WithResponse,
) {
    val char = findCharacteristic(serviceUuid, characteristicUuid) ?: return
    write(char, encoder.encode(value), writeType)
}

/**
 * Observes notifications/indications from a characteristic, decoding each
 * payload with [decoder]. Values that fail to decode (decoder returns `null`)
 * are dropped from the stream.
 *
 * Returns an empty flow if the characteristic is not present.
 */
public fun <T> Peripheral.observeAs(
    serviceUuid: Uuid,
    characteristicUuid: Uuid,
    decoder: Decoder<T>,
    backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
): Flow<T> {
    val char = findCharacteristic(serviceUuid, characteristicUuid) ?: return emptyFlow()
    return observeValues(char, backpressure).mapNotNull { decoder.decode(it) }
}
