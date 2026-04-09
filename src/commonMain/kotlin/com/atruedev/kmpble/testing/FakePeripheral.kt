package com.atruedev.kmpble.testing

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.connection.internal.ConnectionEvent
import com.atruedev.kmpble.error.BleError
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.ConnectionFailed
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.error.OperationFailed
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.gatt.internal.ObservationManager
import com.atruedev.kmpble.gatt.internal.observationFlow
import com.atruedev.kmpble.gatt.internal.observationValuesFlow
import com.atruedev.kmpble.gatt.internal.resubscribe
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.l2cap.L2capException
import com.atruedev.kmpble.peripheral.Peripheral
import com.atruedev.kmpble.peripheral.internal.PeripheralContext
import com.atruedev.kmpble.gatt.internal.applyBackpressure
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
public class FakePeripheral internal constructor(
    override val identifier: Identifier,
    private var fakeServices: List<DiscoveredService>,
    private val characteristicConfigs: List<FakeCharacteristicConfig>,
    private val onConnectHandler: suspend () -> Result<Unit>,
    private val onDisconnectHandler: suspend () -> Result<Unit>,
    private val onL2capHandler: L2capHandler?,
) : Peripheral {
    private val context = PeripheralContext(identifier)
    private val observationManager = ObservationManager()
    private var closed = false
    private val cccdWrites = mutableListOf<CccdWrite>()

    override val state: StateFlow<State> get() = context.state
    override val bondState: StateFlow<com.atruedev.kmpble.bonding.BondState> get() = context.bondState
    override val services: StateFlow<List<DiscoveredService>?> get() = context.services
    override val maximumWriteValueLength: StateFlow<Int> get() = context.maximumWriteValueLength

    /**
     * Record of CCCD writes for test verification.
     */
    public data class CccdWrite(
        val serviceUuid: Uuid,
        val charUuid: Uuid,
        val enabled: Boolean,
    )

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
            val error =
                ConnectionFailed(
                    result.exceptionOrNull()?.message ?: "Connection failed",
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

    @com.atruedev.kmpble.ExperimentalBleApi
    override fun removeBond(): com.atruedev.kmpble.bonding.BondRemovalResult =
        com.atruedev.kmpble.bonding.BondRemovalResult
            .NotSupported("FakePeripheral")

    override fun close() {
        if (closed) return
        closed = true
        observationManager.clear()
        context.close()
    }

    override suspend fun refreshServices(): List<DiscoveredService> {
        checkNotClosed()
        context.updateServices(fakeServices)
        return fakeServices
    }

    // --- GATT Operations ---

    override suspend fun read(characteristic: Characteristic): ByteArray {
        checkNotClosed()
        checkConnected()
        // config null → no delay/error injection, fall through to handler requirement
        val config = findConfig(characteristic)
        applyDelay(config)
        checkFailWith(config)
        val handler =
            config?.readHandler
                ?: throw UnsupportedOperationException("No onRead handler for ${characteristic.uuid}")
        return handler()
    }

    override suspend fun write(
        characteristic: Characteristic,
        data: ByteArray,
        writeType: WriteType,
    ) {
        checkNotClosed()
        checkConnected()
        val config = findConfig(characteristic)
        applyDelay(config)
        checkFailWith(config)
        val handler = config?.writeHandler
        if (handler != null) {
            handler(data, writeType)
        } else {
            val hasWriteProperty =
                characteristic.properties.write ||
                    characteristic.properties.writeWithoutResponse ||
                    characteristic.properties.signedWrite
            if (!hasWriteProperty) {
                throw BleException(
                    GattError("write", GattStatus.WriteNotPermitted),
                )
            }
            // No handler but writable — succeed silently
        }
    }

    override fun observe(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy,
    ): Flow<Observation> {
        checkNotClosed()
        val config = findConfig(characteristic)
        failWithFlow<Observation>(config)?.let { return it }

        val handler = config?.observeHandler
        return if (handler != null) {
            handler()
                .map<ByteArray, Observation> { Observation.Value(it) }
                .applyBackpressure(backpressure)
        } else {
            observationManager.observationFlow(
                characteristic = characteristic,
                backpressure = backpressure,
                isReady = { context.state.value is State.Connected.Ready },
                enableNotifications = { recordCccdWrite(characteristic, enabled = true) },
                disableNotifications = { recordCccdDisable(characteristic) },
            )
        }
    }

    override fun observeValues(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy,
    ): Flow<ByteArray> {
        checkNotClosed()
        val config = findConfig(characteristic)
        failWithFlow<ByteArray>(config)?.let { return it }

        val handler = config?.observeHandler
        return if (handler != null) {
            handler().applyBackpressure(backpressure)
        } else {
            observationManager.observationValuesFlow(
                characteristic = characteristic,
                backpressure = backpressure,
                isReady = { context.state.value is State.Connected.Ready },
                enableNotifications = { recordCccdWrite(characteristic, enabled = true) },
                disableNotifications = { recordCccdDisable(characteristic) },
            )
        }
    }

    private fun recordCccdWrite(characteristic: Characteristic, enabled: Boolean) {
        cccdWrites.add(CccdWrite(characteristic.serviceUuid, characteristic.uuid, enabled))
    }

    private fun recordCccdDisable(characteristic: Characteristic) {
        if (context.state.value is State.Connected) {
            cccdWrites.add(CccdWrite(characteristic.serviceUuid, characteristic.uuid, enabled = false))
        }
    }

    override suspend fun readDescriptor(descriptor: Descriptor): ByteArray {
        checkNotClosed()
        checkConnected()
        return byteArrayOf()
    }

    override suspend fun writeDescriptor(
        descriptor: Descriptor,
        data: ByteArray,
    ) {
        checkNotClosed()
        checkConnected()
    }

    override suspend fun openL2capChannel(
        psm: Int,
        secure: Boolean,
    ): L2capChannel {
        checkNotClosed()
        if (context.state.value !is State.Connected) {
            throw L2capException.NotConnected("Peripheral is not connected (state: ${context.state.value})")
        }
        val handler =
            onL2capHandler
                ?: throw L2capException.NotSupported("No onOpenL2capChannel handler configured")
        return handler(psm)
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

    private suspend fun applyDelay(config: FakeCharacteristicConfig?) {
        val duration = config?.respondAfterDuration ?: return
        if (duration > Duration.ZERO) {
            delay(duration)
        }
    }

    private fun checkFailWith(config: FakeCharacteristicConfig?) {
        val error = config?.failWithError ?: return
        throw BleException(error)
    }

    private fun <T> failWithFlow(config: FakeCharacteristicConfig?): Flow<T>? {
        val error = config?.failWithError ?: return null
        return flow { throw BleException(error) }
    }

    private fun findConfig(characteristic: Characteristic): FakeCharacteristicConfig? =
        characteristicConfigs.firstOrNull {
            it.characteristic.serviceUuid == characteristic.serviceUuid &&
                it.characteristic.uuid == characteristic.uuid
        }

    private fun checkNotClosed() {
        check(!closed) { "Peripheral is closed" }
    }

    private fun checkConnected() {
        check(context.state.value is State.Connected) {
            "Peripheral is not connected (state: ${context.state.value})"
        }
    }

    // --- Test Simulation Methods ---

    /**
     * Simulate a disconnect event. This will emit [Observation.Disconnected] to all active
     * observations but NOT complete them — they persist for reconnection.
     */
    public suspend fun simulateDisconnect(error: BleError = ConnectionLost("Simulated disconnect")) {
        checkNotClosed()
        context.processEvent(ConnectionEvent.ConnectionLost(error))
        observationManager.onDisconnect()
    }

    /**
     * Simulate a reconnection. This will:
     * 1. Transition through connecting states
     * 2. Re-discover services
     * 3. Re-enable CCCD for all active observations
     */
    public suspend fun simulateReconnect(newServices: List<DiscoveredService>? = null) {
        checkNotClosed()
        // Update services if provided (simulates service change on reconnect)
        if (newServices != null) {
            fakeServices = newServices
        }

        context.processEvent(ConnectionEvent.ConnectRequested)
        context.gattQueue.start()
        context.processEvent(ConnectionEvent.LinkEstablished)
        context.processEvent(ConnectionEvent.ServicesDiscovered)
        context.updateServices(fakeServices)

        // Re-enable CCCD for observations that survived the disconnect
        resubscribeObservations()

        context.processEvent(ConnectionEvent.ConfigurationComplete)
    }

    private suspend fun resubscribeObservations() {
        observationManager.resubscribe(
            findCharacteristic = ::findCharacteristic,
            enableNotifications = { char -> recordCccdWrite(char, enabled = true) },
        )
    }

    /**
     * Simulate permanent disconnect (max reconnection attempts exhausted).
     * This will complete all observation flows.
     */
    public suspend fun simulatePermanentDisconnect() {
        checkNotClosed()
        context.processEvent(ConnectionEvent.ConnectionLost(ConnectionLost("Max attempts exhausted")))
        observationManager.onPermanentDisconnect()
    }

    /**
     * Emit a value to an observation. Use this to simulate characteristic notifications.
     */
    public suspend fun emitObservationValue(
        serviceUuid: Uuid,
        charUuid: Uuid,
        value: ByteArray,
    ) {
        checkNotClosed()
        observationManager.emitValue(serviceUuid, charUuid, value)
    }

    /**
     * Emit a value to an observation using short UUID strings.
     */
    public suspend fun emitObservationValue(
        serviceUuid: String,
        charUuid: String,
        value: ByteArray,
    ) {
        emitObservationValue(
            com.atruedev.kmpble.scanner
                .uuidFrom(serviceUuid),
            com.atruedev.kmpble.scanner
                .uuidFrom(charUuid),
            value,
        )
    }

    /**
     * Get all CCCD writes that have occurred. Useful for verifying that
     * CCCD is enabled/disabled at the correct times.
     */
    public fun getCccdWrites(): List<CccdWrite> = cccdWrites.toList()

    /**
     * Clear recorded CCCD writes.
     */
    public fun clearCccdWrites() {
        cccdWrites.clear()
    }

    /**
     * Check if there are any active collectors for a characteristic.
     */
    public suspend fun hasCollectors(
        serviceUuid: Uuid,
        charUuid: Uuid,
    ): Boolean = observationManager.hasCollectors(serviceUuid, charUuid)
}
