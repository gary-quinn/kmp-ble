@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.server

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import com.atruedev.kmpble.Identifier
import kotlinx.coroutines.launch

/**
 * Extension functions for [AndroidGattServerCallback] that handle connection
 * lifecycle callbacks (connect, disconnect, MTU change).
 *
 * Extracted from [AndroidGattServerCallback] during decomposition (PR #243).
 */
internal fun AndroidGattServerCallback.handleConnectionStateChange(
    device: BluetoothDevice,
    status: Int,
    newState: Int,
) {
    state.scope.launch {
        val deviceId = Identifier(device.address)
        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                state.addConnection(deviceId, device)
            }

            BluetoothProfile.STATE_DISCONNECTED -> {
                state.removeConnection(deviceId, device.address)
                state.removeAllSubscriptions(deviceId)
                state.cancelPendingNotify(device.address, "Device disconnected")
            }
        }
    }
}

internal fun AndroidGattServerCallback.handleMtuChanged(
    device: BluetoothDevice,
    mtu: Int,
) {
    state.scope.launch {
        val deviceId = Identifier(device.address)
        state.updateMtu(deviceId, mtu)
    }
}
