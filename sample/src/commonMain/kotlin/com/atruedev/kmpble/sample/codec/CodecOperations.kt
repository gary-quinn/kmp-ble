package com.atruedev.kmpble.sample.codec

import com.atruedev.kmpble.codec.BleDecoder
import com.atruedev.kmpble.codec.BleEncoder
import com.atruedev.kmpble.codec.read
import com.atruedev.kmpble.codec.write
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class CodecOperations(
    private val peripheral: Peripheral,
) {
    suspend fun <T> readTyped(
        characteristic: Characteristic,
        decoder: BleDecoder<T>,
    ): TypedValue {
        val raw = peripheral.read(characteristic)
        return try {
            val decoded = decoder.decode(raw)
            TypedValue.Parsed(value = decoded, raw = raw)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            TypedValue.Raw(data = raw)
        }
    }

    fun <T> observeTyped(
        characteristic: Characteristic,
        decoder: BleDecoder<T>,
        backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
    ): Flow<TypedValue> =
        peripheral.observeValues(characteristic, backpressure).map { raw ->
            try {
                val decoded = decoder.decode(raw)
                TypedValue.Parsed(value = decoded, raw = raw)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                TypedValue.Raw(data = raw)
            }
        }

    suspend fun <T> writeTyped(
        characteristic: Characteristic,
        value: T,
        encoder: BleEncoder<T>,
        writeType: WriteType = WriteType.WithResponse,
    ) {
        val encoded = encoder.encode(value)
        peripheral.write(characteristic, encoded, writeType)
    }
}
