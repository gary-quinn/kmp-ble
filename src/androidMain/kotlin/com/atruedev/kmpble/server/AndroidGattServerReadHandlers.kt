@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.server

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.uuid.toKotlinUuid

/**
 * Extension functions for [AndroidGattServerCallback] that handle read requests,
 * descriptor read requests, and prepared-write buffering.
 *
 * Extracted from [AndroidGattServerCallback] during decomposition (PR #243).
 */

internal fun AndroidGattServerCallback.handleCharacteristicReadRequest(
    device: BluetoothDevice,
    requestId: Int,
    offset: Int,
    characteristic: BluetoothGattCharacteristic,
) {
    state.scope.launch {
        val deviceId = Identifier(device.address)
        val charUuid = characteristic.uuid.toKotlinUuid()
        val handler = state.readHandlers[charUuid]

        if (handler == null) {
            logEvent(
                BleLogEvent.ServerRequest(
                    deviceId,
                    "read-rejected (no handler)",
                    charUuid,
                    GattStatus.ReadNotPermitted,
                ),
            )
            sendResponseSafe(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
            return@launch
        }

        try {
            val bleData = handler(deviceId)
            val responseData =
                if (offset > 0 && offset < bleData.size) {
                    bleData.slice(offset, bleData.size).toByteArray()
                } else if (offset >= bleData.size && offset > 0) {
                    byteArrayOf()
                } else {
                    bleData.toByteArray()
                }
            logEvent(
                BleLogEvent.ServerRequest(
                    deviceId,
                    "read (${responseData.size}B, offset=$offset)",
                    charUuid,
                    GattStatus.Success,
                ),
            )
            sendResponseSafe(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, responseData)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            logEvent(
                BleLogEvent.ServerRequest(
                    deviceId,
                    "read-failed (handler threw)",
                    charUuid,
                    GattStatus.Failure,
                ),
            )
            sendResponseSafe(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
        }
    }
}

internal fun AndroidGattServerCallback.handleDescriptorReadRequest(
    device: BluetoothDevice,
    requestId: Int,
    offset: Int,
    descriptor: BluetoothGattDescriptor,
) {
    state.scope.launch {
        val descUuid = descriptor.uuid.toKotlinUuid()
        if (descUuid == AndroidGattServer.CCCD_UUID) {
            val deviceId = Identifier(device.address)
            val charUuid = descriptor.characteristic.uuid.toKotlinUuid()
            val key = AndroidGattServerState.SubscriptionKey(charUuid, deviceId)
            val cccdValue =
                state.subscriptionModes[key]
                    ?: BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            sendResponseSafe(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, cccdValue)
        } else {
            sendResponseSafe(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, byteArrayOf())
        }
    }
}

internal fun AndroidGattServerCallback.handlePreparedWrite(
    device: BluetoothDevice,
    deviceId: Identifier,
    requestId: Int,
    charUuid: kotlin.uuid.Uuid,
    offset: Int,
    responseNeeded: Boolean,
    value: ByteArray?,
) {
    if (state.writeHandlers[charUuid] == null) {
        logEvent(
            BleLogEvent.ServerRequest(
                deviceId,
                "prepared-write-rejected (no handler)",
                charUuid,
                GattStatus.WriteNotPermitted,
            ),
        )
        if (responseNeeded) {
            sendResponseSafe(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, offset, null)
        }
        return
    }

    val buffer = state.preparedWriteBuffer.getOrPut(device.address) { mutableListOf() }
    val projectedSize = projectedBufferSize(buffer, offset, value?.size ?: 0)
    if (projectedSize > MAX_PREPARED_WRITE_BUFFER_BYTES) {
        state.preparedWriteBuffer.remove(device.address)
        logEvent(
            BleLogEvent.ServerRequest(
                deviceId,
                "prepared-write-rejected (${projectedSize}B exceeds limit)",
                charUuid,
                GattStatus.InvalidAttributeLength,
            ),
        )
        if (responseNeeded) {
            sendResponseSafe(device, requestId, BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH, offset, null)
        }
        return
    }

    buffer.add(WriteFragment(charUuid, offset, value ?: byteArrayOf()))
    logEvent(
        BleLogEvent.ServerRequest(
            deviceId,
            "prepared-write (${value?.size ?: 0}B @$offset)",
            charUuid,
            GattStatus.Success,
        ),
    )
    if (responseNeeded) {
        sendResponseSafe(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
    }
}
