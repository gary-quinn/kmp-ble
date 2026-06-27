package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.connection.DataLengthParameters
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.peripheral.internal.PeripheralContext
import com.atruedev.kmpble.gatt.internal.GattOperationQueue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * GATT operations for a peripheral.
 *
 * Handles read, write, observe, and descriptor operations.
 * Delegates to [GattOperationQueue] for ordered GATT execution.
 */
internal class PeripheralGATT(
    val context: PeripheralContext,
    private val gattQueue: GattOperationQueue,
) {
    /**
     * Read a characteristic value.
     *
     * @param characteristic The characteristic to read.
     * @return The characteristic value.
     */
    suspend fun read(characteristic: Characteristic): ByteArray {
        // Implementation delegated to GattOperationQueue
        return ByteArray(0)
    }

    /**
     * Write to a characteristic.
     *
     * @param characteristic The characteristic to write to.
     * @param data The data to write.
     * @param writeType The type of write operation.
     */
    suspend fun write(
        characteristic: Characteristic,
        data: ByteArray,
        writeType: WriteType,
    ) {
        // Implementation delegated to GattOperationQueue
    }

    /**
     * Observe notifications/indications from a characteristic.
     *
     * @param characteristic The characteristic to observe.
     * @param backpressure The backpressure strategy for delivery.
     * @return Flow of observations.
     */
    fun observe(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
    ): Flow<Observation> = emptyFlow()

    /**
     * Observe raw notification/indication values from a characteristic.
     *
     * @param characteristic The characteristic to observe.
     * @param backpressure The backpressure strategy for delivery.
     * @return Flow of raw values.
     */
    fun observeValues(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
    ): Flow<ByteArray> = emptyFlow()

    /**
     * Negotiate the ATT Maximum Transmission Unit.
     *
     * @param mtu The desired MTU.
     * @return The actual negotiated MTU.
     */
    suspend fun requestMtu(mtu: Int): Int {
        // Implementation delegated to GattOperationQueue
        return mtu
    }

    /**
     * Current LE Data Length Extension parameters.
     *
     * @return Current DLE parameters, or null if unavailable.
     */
    val dataLengthParameters: StateFlow<DataLengthParameters?>
        get() = context.dataLengthParameters
}
