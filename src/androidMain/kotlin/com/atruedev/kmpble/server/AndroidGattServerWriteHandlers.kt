@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.server

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.emptyBleData
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import com.atruedev.kmpble.peripheral.toAndroidGattStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * Extension functions for [AndroidGattServerCallback] that handle write requests,
 * descriptor write requests, execute-write dispatch, and CCCD management.
 *
 * Extracted from [AndroidGattServerCallback] during decomposition (PR #243).
 */

internal fun AndroidGattServerCallback.handleCharacteristicWriteRequest(
    device: BluetoothDevice,
    requestId: Int,
    characteristic: BluetoothGattCharacteristic,
    preparedWrite: Boolean,
    responseNeeded: Boolean,
    offset: Int,
    value: ByteArray?,
) {
    state.scope.launch {
        val deviceId = Identifier(device.address)
        val charUuid = characteristic.uuid.toKotlinUuid()

        if (preparedWrite) {
            handlePreparedWrite(device, deviceId, requestId, charUuid, offset, responseNeeded, value)
            return@launch
        }

        val handler = state.writeHandlers[charUuid]
        if (handler == null) {
            logEvent(
                BleLogEvent.ServerRequest(
                    deviceId,
                    "write-rejected (no handler)",
                    charUuid,
                    GattStatus.WriteNotPermitted,
                ),
            )
            if (responseNeeded) {
                sendResponseSafe(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, offset, null)
            }
            return@launch
        }

        try {
            val bleData =
                if (value != null && value.isNotEmpty()) {
                    BleData(value)
                } else {
                    emptyBleData()
                }
            val gattStatus = handler(deviceId, bleData, responseNeeded)
            logEvent(
                BleLogEvent.ServerRequest(deviceId, "write (${value?.size ?: 0}B)", charUuid, gattStatus),
            )
            if (responseNeeded) {
                val nativeStatus =
                    gattStatus?.toAndroidGattStatus() ?: BluetoothGatt.GATT_SUCCESS
                sendResponseSafe(device, requestId, nativeStatus, offset, null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            logEvent(
                BleLogEvent.ServerRequest(
                    deviceId,
                    "write-failed (handler threw)",
                    charUuid,
                    GattStatus.Failure,
                ),
            )
            if (responseNeeded) {
                sendResponseSafe(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
            }
        }
    }
}

internal fun AndroidGattServerCallback.handleDescriptorWriteRequest(
    device: BluetoothDevice,
    requestId: Int,
    descriptor: BluetoothGattDescriptor,
    preparedWrite: Boolean,
    responseNeeded: Boolean,
    offset: Int,
    value: ByteArray?,
) {
    state.scope.launch {
        val descUuid = descriptor.uuid.toKotlinUuid()
        if (descUuid == AndroidGattServer.CCCD_UUID && value != null) {
            val charUuid = descriptor.characteristic.uuid.toKotlinUuid()
            handleCccdWrite(device, charUuid, value)
            if (responseNeeded) {
                sendResponseSafe(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
        } else {
            if (responseNeeded) {
                sendResponseSafe(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
        }
    }
}

internal fun AndroidGattServerCallback.handleExecuteWrite(
    device: BluetoothDevice,
    requestId: Int,
    execute: Boolean,
) {
    state.scope.launch {
        val fragments = state.preparedWriteBuffer.remove(device.address)
        val deviceId = Identifier(device.address)

        if (!execute || fragments.isNullOrEmpty()) {
            logEvent(
                BleLogEvent.ServerClientEvent(
                    deviceId,
                    if (execute) "execute-write (empty)" else "execute-write (cancelled)",
                ),
            )
            sendResponseSafe(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            return@launch
        }

        when (val result = assembleWriteFragments(fragments)) {
            is AssemblyResult.PayloadTooLarge -> {
                logEvent(
                    BleLogEvent.ServerRequest(
                        deviceId,
                        "execute-write-rejected (${result.actualSize}B exceeds limit)",
                        result.charUuid,
                        GattStatus.InvalidAttributeLength,
                    ),
                )
                sendResponseSafe(
                    device,
                    requestId,
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH,
                    0,
                    null,
                )
            }

            is AssemblyResult.Success -> {
                val gattStatus = dispatchAssembledWrites(deviceId, result.writes)
                sendResponseSafe(device, requestId, gattStatus, 0, null)
            }
        }
    }
}

internal fun AndroidGattServerCallback.handleCccdWrite(
    device: BluetoothDevice,
    characteristicUuid: Uuid,
    value: ByteArray,
) {
    val deviceId = Identifier(device.address)
    if (value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
        state.removeSubscription(deviceId, characteristicUuid)
    } else {
        state.addSubscription(deviceId, characteristicUuid, value)
    }
}

internal suspend fun AndroidGattServerCallback.dispatchAssembledWrites(
    deviceId: Identifier,
    writes: List<AssembledWrite>,
): Int {
    for (write in writes) {
        val handler = state.writeHandlers[write.charUuid]
        if (handler == null) {
            logEvent(
                BleLogEvent.ServerRequest(
                    deviceId,
                    "execute-write-rejected (no handler)",
                    write.charUuid,
                    GattStatus.WriteNotPermitted,
                ),
            )
            return BluetoothGatt.GATT_FAILURE
        }

        try {
            val gattStatus = handler(deviceId, BleData(write.data), true)
            logEvent(
                BleLogEvent.ServerRequest(
                    deviceId,
                    "execute-write (${write.data.size}B)",
                    write.charUuid,
                    gattStatus,
                ),
            )
            if (gattStatus != null && gattStatus != GattStatus.Success) {
                return BluetoothGatt.GATT_FAILURE
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logEvent(
                BleLogEvent.ServerRequest(
                    deviceId,
                    "execute-write-failed (handler threw)",
                    write.charUuid,
                    GattStatus.Failure,
                ),
            )
            return BluetoothGatt.GATT_FAILURE
        }
    }
    return BluetoothGatt.GATT_SUCCESS
}
