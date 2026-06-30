package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.peripheral.state.ConnectionState
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.gatt.internal.LargeWriteHandler
import com.atruedev.kmpble.gatt.internal.PendingOp
import com.atruedev.kmpble.peripheral.internal.ObservationToBytes
import com.atruedev.kmpble.peripheral.internal.ObservationToObservation
import com.atruedev.kmpble.peripheral.internal.awaitGatt
import com.atruedev.kmpble.peripheral.internal.buildObservationFlow
import kotlinx.coroutines.flow.Flow
import platform.CoreBluetooth.CBCharacteristicWriteWithResponse

internal suspend fun IosPeripheral.readGatt(characteristic: Characteristic): ByteArray {
    checkNotClosed()
    return peripheralContext.gattQueue.enqueue(timeout = currentTimeouts.read) {
        val native = requireNativeCbChar(characteristic)
        val result =
            pendingOps.awaitGatt(PendingOp.CharacteristicRead, "read") {
                bridge.readCharacteristic(native)
            }
        if (!result.status.isSuccess()) throw BleException(GattError("read", result.status))
        result.value
    }
}

internal suspend fun IosPeripheral.writeGatt(
    characteristic: Characteristic,
    data: ByteArray,
    writeType: WriteType,
) {
    checkNotClosed()
    LargeWriteHandler.validateForWriteType(data, maximumWriteValueLength.value, writeType)

    val native = requireNativeCbChar(characteristic)
    val withResponse = writeType == WriteType.WithResponse || writeType == WriteType.Signed
    val chunks = LargeWriteHandler.chunk(data, maximumWriteValueLength.value)

    peripheralContext.gattQueue.enqueue(timeout = currentTimeouts.write) {
        for (chunk in chunks) {
            if (withResponse) {
                val status =
                    pendingOps.awaitGatt(PendingOp.CharacteristicWrite, "write") {
                        bridge.writeCharacteristic(native, chunk.toNSData(), withResponse = true)
                    }
                if (!status.isSuccess()) throw BleException(GattError("write", status))
            } else {
                bridge.writeCharacteristic(native, chunk.toNSData(), withResponse = false)
            }
        }
    }
}

internal fun IosPeripheral.observeGatt(
    characteristic: Characteristic,
    backpressure: BackpressureStrategy,
): Flow<Observation> {
    checkNotClosed()
    return buildObservationFlow(
        characteristic = characteristic,
        backpressure = backpressure,
        observationManager = observationManager,
        isReady = { peripheralContext.state.value is ConnectionState.Connected.Ready },
        enable = ::enableNotifications,
        disable = ::disableNotifications,
        mapper = ObservationToObservation,
    )
}

internal fun IosPeripheral.observeValuesGatt(
    characteristic: Characteristic,
    backpressure: BackpressureStrategy,
): Flow<ByteArray> {
    checkNotClosed()
    return buildObservationFlow(
        characteristic = characteristic,
        backpressure = backpressure,
        observationManager = observationManager,
        isReady = { peripheralContext.state.value is ConnectionState.Connected.Ready },
        enable = ::enableNotifications,
        disable = ::disableNotifications,
        mapper = ObservationToBytes,
    )
}

internal suspend fun IosPeripheral.readDescriptorGatt(descriptor: Descriptor): ByteArray {
    checkNotClosed()
    return peripheralContext.gattQueue.enqueue(timeout = currentTimeouts.read) {
        val native = requireNativeCbDesc(descriptor)
        val result =
            pendingOps.awaitGatt(PendingOp.DescriptorRead, "readDescriptor") {
                bridge.readDescriptor(native)
            }
        if (!result.status.isSuccess()) throw BleException(GattError("readDescriptor", result.status))
        result.value
    }
}

internal suspend fun IosPeripheral.writeDescriptorGatt(
    descriptor: Descriptor,
    data: ByteArray,
) {
    checkNotClosed()
    peripheralContext.gattQueue.enqueue(timeout = currentTimeouts.write) {
        val native = requireNativeCbDesc(descriptor)
        val status =
            pendingOps.awaitGatt(PendingOp.DescriptorWrite, "writeDescriptor") {
                bridge.writeDescriptor(native, data.toNSData())
            }
        if (!status.isSuccess()) throw BleException(GattError("writeDescriptor", status))
    }
}

internal suspend fun IosPeripheral.readRssiGatt(): Int {
    checkNotClosed()
    return peripheralContext.gattQueue.enqueue {
        pendingOps.awaitGatt(PendingOp.RssiRead, "readRssi") { bridge.readRSSI() }
    }
}

internal suspend fun IosPeripheral.requestMtuGatt(mtu: Int): Int {
    checkNotClosed()
    val actualMtu =
        cbPeripheral
            .maximumWriteValueLengthForType(CBCharacteristicWriteWithResponse)
            .toInt() + ATT_HEADER_SIZE
    peripheralContext.updateMtu(actualMtu)
    return actualMtu
}
