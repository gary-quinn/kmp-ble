@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.peripheral

import android.annotation.SuppressLint
import com.atruedev.kmpble.connection.State
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
import com.atruedev.kmpble.gatt.internal.PendingOp
import com.atruedev.kmpble.peripheral.internal.ObservationToBytes
import com.atruedev.kmpble.peripheral.internal.ObservationToObservation
import com.atruedev.kmpble.peripheral.internal.awaitGatt
import com.atruedev.kmpble.peripheral.internal.buildObservationFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * GATT read / write / notification / refresh implementations for
 * [AndroidPeripheral].
 *
 * Extracted during second-pass decomposition (380 -> ~260) to keep the facade
 * under 300 lines.  Companion event dispatch lives in
 * [AndroidPeripheralGattHandler].
 */

internal suspend fun AndroidPeripheral.refreshServicesGatt(): List<DiscoveredService> {
    checkNotClosed()
    return withContext(peripheralContext.dispatcher) {
        bridge.refreshDeviceCache()
        val deferred = slots.armDiscovery()
        if (!bridge.discoverServices()) {
            slots.clearDiscovery()
            throw BleException(OperationFailed("discoverServices initiation failed"))
        }
        try {
            withTimeout(currentTimeouts.serviceDiscovery) { deferred.await() }
        } finally {
            slots.clearDiscovery()
        }
    }
}

internal suspend fun AndroidPeripheral.readCharacteristicGatt(characteristic: Characteristic): ByteArray {
    checkNotClosed()
    return peripheralContext.gattQueue.enqueue(timeout = currentTimeouts.read) {
        val native = requireNativeChar(characteristic)
        val result =
            pendingOps.awaitGatt(PendingOp.CharacteristicRead, "read") {
                bridge.readCharacteristic(native)
            }
        if (!result.status.isSuccess()) throw BleException(GattError("read", result.status))
        result.value
    }
}

internal suspend fun AndroidPeripheral.writeCharacteristicGatt(
    characteristic: Characteristic,
    data: ByteArray,
    writeType: WriteType,
) {
    checkNotClosed()
    LargeWriteHandler.validateForWriteType(data, maximumWriteValueLength.value, writeType)

    val native = requireNativeChar(characteristic)
    val androidWriteType = writeType.toAndroidWriteType()
    val chunks = LargeWriteHandler.chunk(data, maximumWriteValueLength.value)

    peripheralContext.gattQueue.enqueue(timeout = currentTimeouts.write) {
        for (chunk in chunks) {
            val status =
                pendingOps.awaitGatt(PendingOp.CharacteristicWrite, "write") {
                    bridge.writeCharacteristic(native, chunk, androidWriteType)
                }
            if (!status.isSuccess()) throw BleException(GattError("write", status))
        }
    }
}

internal fun AndroidPeripheral.observeGatt(
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

internal fun AndroidPeripheral.observeValuesGatt(
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

internal suspend fun AndroidPeripheral.readDescriptorGatt(descriptor: Descriptor): ByteArray {
    checkNotClosed()
    return peripheralContext.gattQueue.enqueue(timeout = currentTimeouts.read) {
        val native = requireNativeDesc(descriptor)
        val result =
            pendingOps.awaitGatt(PendingOp.DescriptorRead, "readDescriptor") {
                bridge.readDescriptor(native)
            }
        if (!result.status.isSuccess()) throw BleException(GattError("descriptorRead", result.status))
        result.value
    }
}

internal suspend fun AndroidPeripheral.writeDescriptorGatt(
    descriptor: Descriptor,
    data: ByteArray,
) {
    checkNotClosed()
    peripheralContext.gattQueue.enqueue(timeout = currentTimeouts.write) {
        val native = requireNativeDesc(descriptor)
        val status =
            pendingOps.awaitGatt(PendingOp.DescriptorWrite, "writeDescriptor") {
                bridge.writeDescriptor(native, data)
            }
        if (!status.isSuccess()) throw BleException(GattError("descriptorWrite", status))
    }
}

internal suspend fun AndroidPeripheral.readRssiGatt(): Int {
    checkNotClosed()
    return peripheralContext.gattQueue.enqueue {
        pendingOps.awaitGatt(PendingOp.RssiRead, "readRssi") { bridge.readRemoteRssi() }
    }
}

internal suspend fun AndroidPeripheral.requestMtuGatt(mtu: Int): Int {
    checkNotClosed()
    return peripheralContext.gattQueue.enqueue(timeout = currentTimeouts.mtuNegotiation) {
        pendingOps.awaitGatt(PendingOp.MtuRequest, "requestMtu") { bridge.requestMtu(mtu) }
    }
}
