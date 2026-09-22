package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.OperationFailed
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.gatt.internal.LargeWriteHandler
import com.atruedev.kmpble.peripheral.internal.ObservationToBytes
import com.atruedev.kmpble.peripheral.internal.ObservationToObservation
import com.atruedev.kmpble.peripheral.internal.buildObservationFlow
import com.atruedev.kmpble.peripheral.state.State
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal suspend fun BlueZPeripheral.refreshServicesGatt(): List<DiscoveredService> {
    checkNotClosed()
    return withContext(peripheralContext.dispatcher) {
        val session = requireSession()
        val deferred = slots.armDiscovery()
        session.refreshGattServices()
        val discovered = session.getGattServices().map { it.toDiscoveredService(this@refreshServicesGatt) }
        if (discovered.isEmpty()) {
            slots.clearDiscovery()
            throw BleException(OperationFailed("No GATT services returned after refresh"))
        }
        peripheralContext.updateServices(discovered)
        slots.completeDiscovery(discovered)
        peripheralContext.scope.launch {
            resubscribeObservations()
        }
        try {
            withTimeout(currentTimeouts.serviceDiscovery) { deferred.await() }
        } finally {
            slots.clearDiscovery()
        }
    }
}

internal suspend fun BlueZPeripheral.readCharacteristicGatt(characteristic: Characteristic): ByteArray {
    checkNotClosed()
    return peripheralContext.gattQueue.enqueue(timeout = currentTimeouts.read) {
        val charPath = requireNativeCharPath(characteristic)
        requireSession().readCharacteristic(charPath).unwrapBlueZRead("read")
    }
}

internal suspend fun BlueZPeripheral.writeCharacteristicGatt(
    characteristic: Characteristic,
    data: ByteArray,
    writeType: WriteType,
) {
    checkNotClosed()
    LargeWriteHandler.validateForWriteType(data, maximumWriteValueLength.value, writeType)
    val charPath = requireNativeCharPath(characteristic)
    val bluezType = writeType.toBlueZWriteType()
    val chunks = LargeWriteHandler.chunk(data, maximumWriteValueLength.value)
    peripheralContext.gattQueue.enqueue(timeout = currentTimeouts.write) {
        for (chunk in chunks) {
            requireSession().writeCharacteristic(charPath, chunk, bluezType).unwrapBlueZWrite("write")
        }
    }
}

internal fun BlueZPeripheral.observeGatt(
    characteristic: Characteristic,
    backpressure: BackpressureStrategy,
): Flow<Observation> {
    checkNotClosed()
    return buildObservationFlow(
        characteristic = characteristic,
        backpressure = backpressure,
        observationManager = observationManager,
        isReady = { peripheralContext.state.value is State.Connected.Ready },
        enable = ::enableNotifications,
        disable = ::disableNotificationsBestEffort,
        mapper = ObservationToObservation,
    )
}

internal fun BlueZPeripheral.observeValuesGatt(
    characteristic: Characteristic,
    backpressure: BackpressureStrategy,
): Flow<ByteArray> {
    checkNotClosed()
    return buildObservationFlow(
        characteristic = characteristic,
        backpressure = backpressure,
        observationManager = observationManager,
        isReady = { peripheralContext.state.value is State.Connected.Ready },
        enable = ::enableNotifications,
        disable = ::disableNotificationsBestEffort,
        mapper = ObservationToBytes,
    )
}

internal suspend fun BlueZPeripheral.readDescriptorGatt(descriptor: Descriptor): ByteArray {
    checkNotClosed()
    return peripheralContext.gattQueue.enqueue(timeout = currentTimeouts.read) {
        val descPath = requireNativeDescPath(descriptor)
        requireSession().readDescriptor(descPath).unwrapBlueZRead("readDescriptor")
    }
}

internal suspend fun BlueZPeripheral.writeDescriptorGatt(
    descriptor: Descriptor,
    data: ByteArray,
) {
    checkNotClosed()
    peripheralContext.gattQueue.enqueue(timeout = currentTimeouts.write) {
        val descPath = requireNativeDescPath(descriptor)
        requireSession().writeDescriptor(descPath, data).unwrapBlueZWrite("writeDescriptor")
    }
}

internal suspend fun BlueZPeripheral.readRssiGatt(): Int {
    checkNotClosed()
    return withContext(peripheralContext.dispatcher) {
        requireSession().readRssi()
            ?: throw BleException(GattError("readRssi", com.atruedev.kmpble.error.GattStatus.Failure))
    }
}

internal fun WriteType.toBlueZWriteType(): String =
    when (this) {
        WriteType.WithResponse -> "request"
        WriteType.WithoutResponse -> "command"
        WriteType.Signed -> throw UnsupportedOperationException("Signed writes are not supported on BlueZ JVM")
    }
