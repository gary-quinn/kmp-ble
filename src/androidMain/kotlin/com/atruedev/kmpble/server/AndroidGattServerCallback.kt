@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.server

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService

/**
 * [BluetoothGattServerCallback] that dispatches Binder-thread callbacks
 * onto [state.scope] for serialized processing on [state.dispatcher].
 *
 * Logic is extracted into focused handler files:
 * - [AndroidGattServerConnectionHandlers.kt] - connection, MTU
 * - [AndroidGattServerReadHandlers.kt] - read, descriptor read, prepared writes
 * - [AndroidGattServerWriteHandlers.kt] - write, descriptor write, execute write, CCCD
 *
 * This file wires the callback overrides to their handlers.
 */
internal class AndroidGattServerCallback(
    internal val state: AndroidGattServerState,
) {
    val callback: BluetoothGattServerCallback =
        object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(
                device: BluetoothDevice,
                status: Int,
                newState: Int,
            ) {
                handleConnectionStateChange(device, status, newState)
            }

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic,
            ) {
                handleCharacteristicReadRequest(device, requestId, offset, characteristic)
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?,
            ) {
                handleCharacteristicWriteRequest(
                    device,
                    requestId,
                    characteristic,
                    preparedWrite,
                    responseNeeded,
                    offset,
                    value,
                )
            }

            override fun onDescriptorReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                descriptor: BluetoothGattDescriptor,
            ) {
                handleDescriptorReadRequest(device, requestId, offset, descriptor)
            }

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                descriptor: BluetoothGattDescriptor,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?,
            ) {
                handleDescriptorWriteRequest(
                    device,
                    requestId,
                    descriptor,
                    preparedWrite,
                    responseNeeded,
                    offset,
                    value,
                )
            }

            override fun onExecuteWrite(
                device: BluetoothDevice,
                requestId: Int,
                execute: Boolean,
            ) {
                handleExecuteWrite(device, requestId, execute)
            }

            override fun onNotificationSent(
                device: BluetoothDevice,
                status: Int,
            ) {
                state.pendingNotifySent.remove(device.address)?.complete(status)
            }

            override fun onServiceAdded(
                status: Int,
                service: BluetoothGattService,
            ) {
                state.pendingServiceAdd.get()?.complete(status)
            }

            override fun onMtuChanged(
                device: BluetoothDevice,
                mtu: Int,
            ) {
                handleMtuChanged(device, mtu)
            }
        }

    internal fun sendResponseSafe(
        device: BluetoothDevice,
        requestId: Int,
        status: Int,
        offset: Int,
        value: ByteArray?,
    ) {
        if (!state.isOpen.get()) return
        try {
            state.nativeServer.get()?.sendResponse(device, requestId, status, offset, value)
        } catch (_: SecurityException) {
            // Ignore - device disconnected or permission lost
        }
    }
}
