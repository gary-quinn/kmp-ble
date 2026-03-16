package io.github.garyquinn.kmpble.peripheral

import io.github.garyquinn.kmpble.Identifier
import io.github.garyquinn.kmpble.bonding.BondState
import io.github.garyquinn.kmpble.connection.ConnectionOptions
import io.github.garyquinn.kmpble.connection.State
import io.github.garyquinn.kmpble.gatt.BackpressureStrategy
import io.github.garyquinn.kmpble.gatt.Characteristic
import io.github.garyquinn.kmpble.gatt.Descriptor
import io.github.garyquinn.kmpble.gatt.DiscoveredService
import io.github.garyquinn.kmpble.gatt.Observation
import io.github.garyquinn.kmpble.gatt.WriteType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
public interface Peripheral : AutoCloseable {

    public val identifier: Identifier

    // --- Connection ---
    public suspend fun connect(options: ConnectionOptions = ConnectionOptions())
    public suspend fun disconnect()
    override fun close()
    public val state: StateFlow<State>
    public val bondState: StateFlow<BondState>

    // --- Discovery ---
    public val services: StateFlow<List<DiscoveredService>?>
    public suspend fun refreshServices(): List<DiscoveredService>
    public fun findCharacteristic(serviceUuid: Uuid, characteristicUuid: Uuid): Characteristic?
    public fun findDescriptor(serviceUuid: Uuid, characteristicUuid: Uuid, descriptorUuid: Uuid): Descriptor?

    // --- GATT Operations ---
    public suspend fun read(characteristic: Characteristic): ByteArray
    public suspend fun write(characteristic: Characteristic, data: ByteArray, writeType: WriteType)
    public fun observe(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
    ): Flow<Observation>
    public fun observeValues(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
    ): Flow<ByteArray>

    // --- Descriptors ---
    public suspend fun readDescriptor(descriptor: Descriptor): ByteArray
    public suspend fun writeDescriptor(descriptor: Descriptor, data: ByteArray)

    // --- Info ---
    public suspend fun readRssi(): Int
    public suspend fun requestMtu(mtu: Int): Int
    public val maximumWriteValueLength: StateFlow<Int>
}
