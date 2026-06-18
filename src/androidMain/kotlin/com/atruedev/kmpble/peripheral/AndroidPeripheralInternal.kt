@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.peripheral

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.StaleGattHandle
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.internal.NotConnectedException
import kotlin.time.Duration.Companion.seconds

/**
 * Internal helpers for [AndroidPeripheral] extracted to keep the facade
 * under 300 lines.
 *
 * Extracted during AndroidPeripheral decomposition (PR #?).
 */

internal fun AndroidPeripheral.checkNotClosed() {
    check(!closed) { "Peripheral is closed" }
}

internal fun AndroidPeripheral.requireNativeChar(c: Characteristic): BluetoothGattCharacteristic =
    nativeCharMap[c]
        ?: throw BleException(StaleGattHandle("characteristic", c.uuid.toString()))

internal fun AndroidPeripheral.requireNativeDesc(d: Descriptor): BluetoothGattDescriptor =
    nativeDescMap[d]
        ?: throw BleException(StaleGattHandle("descriptor", d.uuid.toString()))

internal fun AndroidPeripheral.onDisconnectCleanup() {
    nativeCharMap.clear()
    nativeDescMap.clear()
    closeL2capChannels()
    observationManager.onDisconnect()
    pendingOps.cancelAll(NotConnectedException())
}

internal val DISCONNECT_TIMEOUT = 5.seconds
internal val SERVICE_DISCOVERY_TIMEOUT = 10.seconds
