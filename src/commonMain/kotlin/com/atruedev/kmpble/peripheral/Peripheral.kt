package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.ConnectionOptions
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.bonding.BondManager
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.DataLengthParameters
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.peripheral.internal.PeripheralContext
import com.atruedev.kmpble.peripheral.internal.PeripheralEventHandler
import com.atruedev.kmpble.peripheral.internal.PeripheralGATT
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstract interface for Bluetooth Low Energy peripherals.
 *
 * A peripheral is a remote device that can be connected to and interacted with
 * via GATT operations such as reading, writing, and subscribing to notifications.
 *
 * This interface defines the core functionality for peripheral interaction,
 * including connection management, service discovery, and GATT operations.
 */
@OptIn(ExperimentalBleApi::class)
internal abstract class Peripheral(
    val identifier: Identifier,
) {
    /**
     * Connection lifecycle management.
     */
    internal val connection: PeripheralConnection by lazy {
        PeripheralConnection(identifier, context)
    }

    /**
     * Event handling and discovery.
     */
    internal val eventHandler: PeripheralEventHandler by lazy {
        PeripheralEventHandler(context, gattQueue)
    }

    /**
     * GATT operations (read, write, observe).
     */
    internal val gatt: PeripheralGATT by lazy {
        PeripheralGATT(context, gattQueue)
    }

    /**
     * Bond manager for pair/bond operations.
     */
    internal val bondManager: BondManager by lazy { createBondManager() }

    /**
     * Get the peripheral context.
     */
    protected abstract val context: PeripheralContext

    /**
     * Get the GATT operation queue.
     */
    protected abstract val gattQueue: GattOperationQueue

    /**
     * Create the bond manager.
     */
    protected abstract fun createBondManager(): BondManager

    /**
     * Connect to the peripheral with the given options.
     */
    suspend fun connect(options: ConnectionOptions = ConnectionOptions()) {
        connection.connect(options)
    }

    /**
     * Disconnect from the peripheral.
     */
    suspend fun disconnect() {
        connection.disconnect()
    }

    /**
     * Close the peripheral and release resources.
     */
    fun close() {
        context.close()
    }

    /**
     * Remove bond with this peripheral.
     */
    fun removeBond(): BondManager.BondRemovalResult {
        return bondManager.removeBond()
    }

    /**
     * Find a characteristic by UUID.
     */
    fun findCharacteristic(
        serviceUuid: java.util.UUID,
        characteristicUuid: java.util.UUID,
    ): Characteristic? {
        return connection.findCharacteristic(serviceUuid, characteristicUuid)
    }

    /**
     * Find a descriptor by UUID.
     */
    fun findDescriptor(
        serviceUuid: java.util.UUID,
        characteristicUuid: java.util.UUID,
        descriptorUuid: java.util.UUID,
    ): Descriptor? {
        return connection.findDescriptor(serviceUuid, characteristicUuid, descriptorUuid)
    }

    /**
     * Read a characteristic value.
     */
    suspend fun read(characteristic: Characteristic): ByteArray {
        return gatt.read(characteristic)
    }

    /**
     * Write to a characteristic.
     */
    suspend fun write(
        characteristic: Characteristic,
        data: ByteArray,
        writeType: WriteType,
    ) {
        gatt.write(characteristic, data, writeType)
    }

    /**
     * Observe notifications/indications from a characteristic.
     */
    fun observe(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
    ): Flow<Observation> = gatt.observe(characteristic, backpressure)

    /**
     * Observe raw notification/indication values from a characteristic.
     */
    fun observeValues(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
    ): Flow<ByteArray> = gatt.observeValues(characteristic, backpressure)

    /**
     * Negotiate the ATT Maximum Transmission Unit.
     */
    suspend fun requestMtu(mtu: Int): Int {
        return gatt.requestMtu(mtu)
    }

    /**
     * Refresh discovered services.
     */
    suspend fun refreshServices(): List<DiscoveredService> {
        return eventHandler.refreshServices()
    }

    /**
     * Read a descriptor value.
     */
    suspend fun readDescriptor(descriptor: Descriptor): ByteArray {
        return eventHandler.readDescriptor(descriptor)
    }

    /**
     * Write to a descriptor.
     */
    suspend fun writeDescriptor(
        descriptor: Descriptor,
        data: ByteArray,
    ) {
        eventHandler.writeDescriptor(descriptor, data)
    }
}
