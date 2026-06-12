package com.atruedev.kmpble.testing

import com.atruedev.kmpble.connection.ConnectionPriority
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.gatt.internal.ObservationEvent
import com.atruedev.kmpble.gatt.internal.ObservationManager
import com.atruedev.kmpble.gatt.internal.applyBackpressure
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.l2cap.L2capException
import com.atruedev.kmpble.peripheral.PhyResult
import com.atruedev.kmpble.peripheral.internal.PeripheralContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlin.time.Duration
import kotlin.uuid.Uuid

internal class FakeGattResponder(
    private val context: PeripheralContext,
    private val observationManager: ObservationManager,
    private val characteristicConfigs: List<FakeCharacteristicConfig>,
    private val onL2capHandler: L2capHandler?,
    private val cccdWritesState: MutableStateFlow<List<FakePeripheral.CccdWrite>>,
    private val closedFlag: () -> Boolean,
) {
    private fun checkNotClosed() {
        check(!closedFlag()) { "Peripheral is closed" }
    }

    private fun checkConnected() {
        check(context.state.value is State.Connected) {
            "Peripheral is not connected (state: ${context.state.value})"
        }
    }

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

    private fun recordCccdWrite(
        serviceUuid: Uuid,
        charUuid: Uuid,
        enabled: Boolean,
    ) {
        cccdWritesState.update { it + FakePeripheral.CccdWrite(serviceUuid, charUuid, enabled) }
    }

    suspend fun read(characteristic: Characteristic): ByteArray {
        checkNotClosed()
        checkConnected()
        val config = findConfig(characteristic)
        applyDelay(config)
        checkFailWith(config)
        val handler =
            config?.readHandler
                ?: throw UnsupportedOperationException("No onRead handler for ${characteristic.uuid}")
        return handler()
    }

    suspend fun write(
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
        }
    }

    fun observe(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy,
    ): Flow<Observation> {
        checkNotClosed()
        val config = findConfig(characteristic)
        failWithFlow<Observation>(config)?.let { return it }
        val handler = config?.observeHandler
        if (handler != null) {
            val flow: Flow<ByteArray> = handler()
            return flow
                .map { Observation.Value(it) }
                .applyBackpressure(backpressure)
        }
        return observeInternal(characteristic, backpressure) { event ->
            when (event) {
                is ObservationEvent.Value -> emit(Observation.Value(event.data))
                is ObservationEvent.Disconnected -> emit(Observation.Disconnected)
                is ObservationEvent.PermanentlyDisconnected -> emit(Observation.Disconnected)
            }
        }
    }

    fun observeValues(
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
            observeInternal(characteristic, backpressure) { event ->
                when (event) {
                    is ObservationEvent.Value -> emit(event.data)
                    // Transparent reconnection - ObservationManager re-subscribes on reconnect
                    is ObservationEvent.Disconnected -> Unit
                    is ObservationEvent.PermanentlyDisconnected -> Unit
                }
            }
        }
    }

    private fun <T> observeInternal(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy,
        mapper: suspend FlowCollector<T>.(ObservationEvent) -> Unit,
    ): Flow<T> {
        val serviceUuid = characteristic.serviceUuid
        val charUuid = characteristic.uuid

        return flow {
            val eventFlow = observationManager.subscribe(serviceUuid, charUuid, backpressure)
            eventFlow.collect { event -> mapper(event) }
        }.onStart {
            if (context.state.value is State.Connected.Ready) {
                recordCccdWrite(serviceUuid, charUuid, enabled = true)
            }
        }.applyBackpressure(backpressure)
            .onCompletion {
                val wasLastCollector = observationManager.unsubscribe(serviceUuid, charUuid)
                if (wasLastCollector && context.state.value is State.Connected) {
                    recordCccdWrite(serviceUuid, charUuid, enabled = false)
                }
            }
    }

    suspend fun readDescriptor(descriptor: Descriptor): ByteArray {
        checkNotClosed()
        checkConnected()
        return byteArrayOf()
    }

    suspend fun writeDescriptor(
        descriptor: Descriptor,
        data: ByteArray,
    ) {
        checkNotClosed()
        checkConnected()
    }

    suspend fun openL2capChannel(
        psm: Int,
        secure: Boolean,
        mtu: Int?,
    ): L2capChannel {
        checkNotClosed()
        if (mtu != null) require(mtu > 0) { "mtu must be positive, was $mtu" }
        if (context.state.value !is State.Connected) {
            throw L2capException.NotConnected("Peripheral is not connected (state: ${context.state.value})")
        }
        val handler =
            onL2capHandler
                ?: throw L2capException.NotSupported("No onOpenL2capChannel handler configured")
        return handler(psm, mtu)
    }

    suspend fun readRssi(): Int {
        checkNotClosed()
        checkConnected()
        return -50
    }

    suspend fun requestMtu(mtu: Int): Int {
        checkNotClosed()
        checkConnected()
        context.updateMtu(mtu)
        return mtu
    }

    suspend fun requestConnectionPriority(priority: ConnectionPriority): Boolean {
        checkNotClosed()
        checkConnected()
        return true
    }

    suspend fun setPreferredPhy(
        tx: Phy,
        rx: Phy,
    ): PhyResult? {
        checkNotClosed()
        checkConnected()
        return PhyResult(tx, rx)
    }
}
