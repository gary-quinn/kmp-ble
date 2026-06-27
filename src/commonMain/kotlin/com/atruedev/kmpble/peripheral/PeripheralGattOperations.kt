package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import kotlinx.coroutines.flow.Flow

public interface PeripheralGattOperations {
    public suspend fun read(characteristic: Characteristic): ByteArray

    public suspend fun write(
        characteristic: Characteristic,
        data: ByteArray,
        writeType: WriteType,
    )

    public fun observe(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
    ): Flow<Observation>

    public fun observeValues(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
    ): Flow<ByteArray>

    public suspend fun readDescriptor(descriptor: Descriptor): ByteArray

    public suspend fun writeDescriptor(
        descriptor: Descriptor,
        data: ByteArray,
    )
}
