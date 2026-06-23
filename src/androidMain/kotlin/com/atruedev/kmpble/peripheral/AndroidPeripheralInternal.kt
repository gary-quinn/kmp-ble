@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.peripheral

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.atruedev.kmpble.connection.ConnectionParameters
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.StaleGattHandle
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.internal.NotConnectedException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Internal helpers for [AndroidPeripheral] extracted to keep the facade
 * under 300 lines.
 *
 * Extracted during AndroidPeripheral decomposition (PR #?).
 */

internal fun AndroidPeripheral.checkNotClosed() {
    check(!_closed.value) { "Peripheral is closed" }
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

/**
 * Map [ConnectionParameters.intervalRange] to the closest Android
 * [BluetoothGatt] connection priority constant.
 *
 * - <= 15ms midpoint -> CONNECTION_PRIORITY_HIGH
 * - >= 100ms midpoint -> CONNECTION_PRIORITY_LOW_POWER
 * - Otherwise -> CONNECTION_PRIORITY_BALANCED
 */
internal fun ClosedRange<Duration>.toAndroidConnectionPriority(): Int {
    val midpoint =
        (start.inWholeMicroseconds + endInclusive.inWholeMicroseconds) / 2
    return when {
        midpoint <= 15_000L -> BluetoothGatt.CONNECTION_PRIORITY_HIGH
        midpoint >= 100_000L -> BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER
        else -> BluetoothGatt.CONNECTION_PRIORITY_BALANCED
    }
}
