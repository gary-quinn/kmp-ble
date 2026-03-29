package com.atruedev.kmpble.lincheck

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.bonding.BondRemovalResult
import com.atruedev.kmpble.bonding.BondState
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Minimal [Peripheral] stub for Lincheck tests.
 * Only [identifier] and [close] are functional — all other members
 * throw because Lincheck tests only exercise registry operations.
 */
@OptIn(ExperimentalUuidApi::class)
internal class StubPeripheral(
    override val identifier: Identifier,
) : Peripheral {
    override val state: StateFlow<State> = MutableStateFlow(State.Disconnected.ByRequest)
    override val bondState: StateFlow<BondState> = MutableStateFlow(BondState.Unknown)
    override val services: StateFlow<List<DiscoveredService>?> = MutableStateFlow(null)
    override val maximumWriteValueLength: StateFlow<Int> = MutableStateFlow(20)

    override suspend fun connect(options: ConnectionOptions) = unsupported()

    override suspend fun disconnect() = unsupported()

    override fun close() {}

    @com.atruedev.kmpble.ExperimentalBleApi
    override fun removeBond(): BondRemovalResult = unsupported()

    override suspend fun refreshServices(): List<DiscoveredService> = unsupported()

    override fun findCharacteristic(
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
    ): Characteristic? = unsupported()

    override fun findDescriptor(
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
        descriptorUuid: Uuid,
    ): Descriptor? = unsupported()

    override suspend fun read(characteristic: Characteristic): ByteArray = unsupported()

    override suspend fun write(
        characteristic: Characteristic,
        data: ByteArray,
        writeType: WriteType,
    ) = unsupported()

    override fun observe(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy,
    ): Flow<Observation> = unsupported()

    override fun observeValues(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy,
    ): Flow<ByteArray> = unsupported()

    override suspend fun readDescriptor(descriptor: Descriptor): ByteArray = unsupported()

    override suspend fun writeDescriptor(
        descriptor: Descriptor,
        data: ByteArray,
    ) = unsupported()

    override suspend fun readRssi(): Int = unsupported()

    override suspend fun requestMtu(mtu: Int): Int = unsupported()

    override suspend fun openL2capChannel(
        psm: Int,
        secure: Boolean,
    ): L2capChannel = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("StubPeripheral")
}
