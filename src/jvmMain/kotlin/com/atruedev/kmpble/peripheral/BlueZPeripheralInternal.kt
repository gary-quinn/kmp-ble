package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.StaleGattHandle
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.internal.NotConnectedException
import kotlin.time.Duration.Companion.seconds

internal fun BlueZPeripheral.checkNotClosed() {
    check(!closed) { "Peripheral is closed" }
}

internal fun BlueZPeripheral.requireNativeCharPath(characteristic: Characteristic): String =
    nativeCharPathMap[characteristic]
        ?: throw BleException(StaleGattHandle("characteristic", characteristic.uuid.toString()))

internal fun BlueZPeripheral.requireNativeDescPath(descriptor: Descriptor): String =
    nativeDescPathMap[descriptor]
        ?: throw BleException(StaleGattHandle("descriptor", descriptor.uuid.toString()))

internal fun BlueZPeripheral.onDisconnectCleanup() {
    nativeCharPathMap.clear()
    nativeDescPathMap.clear()
    observationManager.onDisconnect()
    pendingOps.cancelAll(NotConnectedException())
}

internal val BLUEZ_DISCONNECT_TIMEOUT = 5.seconds
