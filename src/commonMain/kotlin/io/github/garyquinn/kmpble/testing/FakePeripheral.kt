package io.github.garyquinn.kmpble.testing

import io.github.garyquinn.kmpble.Identifier
import io.github.garyquinn.kmpble.connection.ConnectionOptions
import io.github.garyquinn.kmpble.connection.State
import io.github.garyquinn.kmpble.connection.internal.ConnectionEvent
import io.github.garyquinn.kmpble.error.BleError
import io.github.garyquinn.kmpble.error.ConnectionFailed
import io.github.garyquinn.kmpble.error.ConnectionLost
import io.github.garyquinn.kmpble.error.GattError
import io.github.garyquinn.kmpble.error.OperationFailed
import io.github.garyquinn.kmpble.gatt.BackpressureStrategy
import io.github.garyquinn.kmpble.gatt.Characteristic
import io.github.garyquinn.kmpble.gatt.Descriptor
import io.github.garyquinn.kmpble.gatt.DiscoveredService
import io.github.garyquinn.kmpble.gatt.Observation
import io.github.garyquinn.kmpble.gatt.WriteType
import io.github.garyquinn.kmpble.gatt.internal.applyBackpressure
import io.github.garyquinn.kmpble.peripheral.Peripheral
import io.github.garyquinn.kmpble.peripheral.internal.PeripheralContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
public class FakePeripheral internal constructor(
    override val identifier: Identifier,
    private val fakeServices: List<DiscoveredService>,
    private val characteristicConfigs: List<FakeCharacteristicConfig>,
    private val onConnectHandler: suspend () -> Result<Unit>,
    private val onDisconnectHandler: suspend () -> Result<Unit>,
) : Peripheral {

    private val context = PeripheralContext(identifier)
    private var closed = false

    override val state: StateFlow<State> get() = context.state
    override val bondState: StateFlow<io.github.garyquinn.kmpble.bonding.BondState> get() = context.bondState
    override val services: StateFlow<List<DiscoveredService>?> get() = context.services
    override val maximumWriteValueLength: StateFlow<Int> get() = context.maximumWriteValueLength

    override suspend fun connect(options: ConnectionOptions) {
        checkNotClosed()
        context.processEvent(ConnectionEvent.ConnectRequested)
        context.gattQueue.start()

        val result = onConnectHandler()
        if (result.isSuccess) {
            context.processEvent(ConnectionEvent.LinkEstablished)
            context.processEvent(ConnectionEvent.ServicesDiscovered)
            context.updateServices(fakeServices)
            context.processEvent(ConnectionEvent.ConfigurationComplete)
        } else {
            val error = ConnectionFailed(
                result.exceptionOrNull()?.message ?: "Connection failed"
            )
            context.processEvent(ConnectionEvent.ConnectionLost(error))
        }
    }

    internal suspend fun simulateEvent(event: ConnectionEvent): State {
        checkNotClosed()
        return context.processEvent(event)
    }

    override suspend fun disconnect() {
        checkNotClosed()
        if (context.state.value is State.Disconnected) return
        context.processEvent(ConnectionEvent.DisconnectRequested)
        onDisconnectHandler()
        context.processEvent(ConnectionEvent.ConnectionLost(OperationFailed("disconnect")))
    }

    @io.github.garyquinn.kmpble.ExperimentalBleApi
    override fun removeBond(): io.github.garyquinn.kmpble.bonding.BondRemovalResult {
        return io.github.garyquinn.kmpble.bonding.BondRemovalResult.NotSupported("FakePeripheral")
    }

    override fun close() {
        if (closed) return
        closed = true
        context.close()
    }

    override suspend fun refreshServices(): List<DiscoveredService> {
        checkNotClosed()
        context.updateServices(fakeServices)
        return fakeServices
    }

    override fun findCharacteristic(serviceUuid: Uuid, characteristicUuid: Uuid): Characteristic? {
        return services.value
            ?.firstOrNull { it.uuid == serviceUuid }
            ?.characteristics
            ?.firstOrNull { it.uuid == characteristicUuid }
    }

    override fun findDescriptor(
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
        descriptorUuid: Uuid,
    ): Descriptor? {
        val char = findCharacteristic(serviceUuid, characteristicUuid) ?: return null
        return char.descriptors.firstOrNull { it.uuid == descriptorUuid }
    }

    // --- GATT Operations ---

    override suspend fun read(characteristic: Characteristic): ByteArray {
        checkNotClosed()
        checkConnected()
        val config = findConfig(characteristic)
        val handler = config?.readHandler
            ?: throw UnsupportedOperationException("No onRead handler for ${characteristic.uuid}")
        return handler()
    }

    override suspend fun write(characteristic: Characteristic, data: ByteArray, writeType: WriteType) {
        checkNotClosed()
        checkConnected()
        val config = findConfig(characteristic)
        val handler = config?.writeHandler
            ?: throw UnsupportedOperationException("No onWrite handler for ${characteristic.uuid}")
        handler(data, writeType)
    }

    override fun observe(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy,
    ): Flow<Observation> {
        checkNotClosed()
        val config = findConfig(characteristic)
        val handler = config?.observeHandler
            ?: return emptyFlow()
        return handler()
            .map<ByteArray, Observation> { Observation.Value(it) }
            .applyBackpressure(backpressure)
    }

    override fun observeValues(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy,
    ): Flow<ByteArray> {
        checkNotClosed()
        val config = findConfig(characteristic)
        val handler = config?.observeHandler
            ?: return emptyFlow()
        return handler().applyBackpressure(backpressure)
    }

    override suspend fun readDescriptor(descriptor: Descriptor): ByteArray {
        checkNotClosed()
        checkConnected()
        return byteArrayOf()
    }

    override suspend fun writeDescriptor(descriptor: Descriptor, data: ByteArray) {
        checkNotClosed()
        checkConnected()
    }

    override suspend fun readRssi(): Int {
        checkNotClosed()
        checkConnected()
        return -50
    }

    override suspend fun requestMtu(mtu: Int): Int {
        checkNotClosed()
        checkConnected()
        context.updateMtu(mtu)
        return mtu
    }

    // --- Internal ---

    private fun findConfig(characteristic: Characteristic): FakeCharacteristicConfig? {
        return characteristicConfigs.firstOrNull {
            it.characteristic.serviceUuid == characteristic.serviceUuid &&
                it.characteristic.uuid == characteristic.uuid
        }
    }

    private fun checkNotClosed() {
        check(!closed) { "Peripheral is closed" }
    }

    private fun checkConnected() {
        check(context.state.value is State.Connected) {
            "Peripheral is not connected (state: ${context.state.value})"
        }
    }
}
