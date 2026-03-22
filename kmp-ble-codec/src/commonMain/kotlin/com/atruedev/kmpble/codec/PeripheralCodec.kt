package com.atruedev.kmpble.codec

import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Read a characteristic and decode the result. */
public suspend fun <T> Peripheral.read(
    characteristic: Characteristic,
    decoder: BleDecoder<T>,
): T = decoder.decode(read(characteristic))

/** Encode a value and write it to a characteristic. */
public suspend fun <T> Peripheral.write(
    characteristic: Characteristic,
    value: T,
    encoder: BleEncoder<T>,
    writeType: WriteType = WriteType.WithResponse,
): Unit = write(characteristic, encoder.encode(value), writeType)

/** Observe decoded values with transparent reconnection. */
public fun <T> Peripheral.observeValues(
    characteristic: Characteristic,
    decoder: BleDecoder<T>,
    backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
): Flow<T> = observeValues(characteristic, backpressure).map(decoder::decode)

/** Observe decoded observations, including disconnect events. */
public fun <T> Peripheral.observe(
    characteristic: Characteristic,
    decoder: BleDecoder<T>,
    backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
): Flow<DecodedObservation<T>> = observe(characteristic, backpressure).map { observation ->
    when (observation) {
        is Observation.Value -> DecodedObservation.Value(decoder.decode(observation.data))
        is Observation.Disconnected -> DecodedObservation.Disconnected
    }
}
