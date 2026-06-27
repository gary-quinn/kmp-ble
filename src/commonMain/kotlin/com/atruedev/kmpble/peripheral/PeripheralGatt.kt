package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import kotlinx.coroutines.flow.Flow

/**
 * GATT read/write and observation operations for a connected peripheral.
 *
 * Covers all GATT protocol operations: characteristic reads/writes, descriptor
 * operations, and CCCD (Client Characteristic Configuration Descriptor) management
 * for notifications/indications.
 *
 * Observations auto-resubscribe on reconnect - they are scoped to the
 * [PeripheralConnection.identifier] and persist across connection lifecycle.
 *
 * @see PeripheralConnection for lifecycle management
 * @see PeripheralDiscovery for service discovery
 * @see PeripheralInfo for connection quality and MTU negotiation
 */
public interface PeripheralGatt {
    // --- Characteristic Operations ---

    /**
     * Read a characteristic's value.
     *
     * Returns the raw bytes stored in the characteristic. The returned array
     * is a snapshot - modifications do not affect the peripheral's state.
     *
     * @param characteristic The characteristic to read
     * @throws com.atruedev.kmpble.error.BleException if read fails
     * @throws com.atruedev.kmpble.error.BleException if read times out
     * @return Raw bytes of the characteristic value
     */
    public suspend fun read(characteristic: Characteristic): ByteArray

    /**
     * Write a value to a characteristic.
     *
     * The [writeType] determines whether the write is reliable (Write Request)
     * or fire-and-forget (Write Command). Use [WriteType.WriteWithResponse]
     * for critical writes that must succeed before proceeding.
     *
     * @param characteristic The characteristic to write
     * @param data The bytes to write
     * @param writeType Write with response or write without response
     * @throws com.atruedev.kmpble.error.BleException if write fails
     * @throws com.atruedev.kmpble.error.BleException if write times out
     */
    public suspend fun write(
        characteristic: Characteristic,
        data: ByteArray,
        writeType: WriteType,
    )

    // --- Descriptor Operations ---

    /**
     * Read a descriptor's value.
     *
     * Returns the raw bytes stored in the descriptor. Used for reading
     * configuration descriptors (Presentation Format, User Description, etc.).
     *
     * @param descriptor The descriptor to read
     * @throws com.atruedev.kmpble.error.BleException if read fails
     * @throws com.atruedev.kmpble.error.BleException if read times out
     * @return Raw bytes of the descriptor value
     */
    public suspend fun readDescriptor(descriptor: Descriptor): ByteArray

    /**
     * Write a value to a descriptor.
     *
     * Used to configure descriptors such as Presentation Format (0x2904)
     * and User Description (0x2901).
     *
     * @param descriptor The descriptor to write
     * @param data The bytes to write
     * @throws com.atruedev.kmpble.error.BleException if write fails
     * @throws com.atruedev.kmpble.error.BleException if write times out
     */
    public suspend fun writeDescriptor(
        descriptor: Descriptor,
        data: ByteArray,
    )

    // --- Observations ---

    /**
     * Observe notifications/indications from a characteristic.
     *
     * The returned flow survives disconnects and auto-resubscribes on reconnect
     * -- emits [Observation.Value] with data, [Observation.Disconnected] on connection
     * loss, resumes after reconnect, and completes when retries exhaust.
     *
     * May be called before connecting; CCCD is enabled on connect.
     *
     * @param characteristic The characteristic to observe
     * @param backpressure Controls delivery when values arrive faster than
     *   consumed: [BackpressureStrategy.Latest] drops intermediate values,
     *   [BackpressureStrategy.Buffer] retains a fixed number,
     *   [BackpressureStrategy.Unbounded] for lossless delivery.
     * @return Flow of [Observation] values
     */
    public fun observe(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
    ): Flow<Observation>

    /**
     * Observe raw notification/indication values from a characteristic.
     *
     * Same lifecycle as [observe], but provides transparent reconnection --
     * suspends during disconnects and resumes on reconnect without emitting
     * disconnect events. Consumers see a gap in data during disconnects but
     * no error handling is required.
     *
     * May be called before connecting; CCCD is enabled on connect.
     *
     * @param characteristic The characteristic to observe
     * @param backpressure Controls delivery when values arrive faster than
     *   consumed (see [observe] for strategy details).
     * @return Flow of raw byte arrays
     */
    public fun observeValues(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
    ): Flow<ByteArray>
}
