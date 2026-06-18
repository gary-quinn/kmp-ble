@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.server

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothProfile
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
 * BluetoothGattServerCallback that dispatches all Binder-thread callbacks
 * onto [state.scope] for serialized processing on [state.dispatcher].
 *
 * Handles connection state changes, read/write requests, descriptor operations,
 * prepared write execution, notification sent, service added, and MTU changes.
 */
internal class AndroidGattServerCallback(
    private val state: AndroidGattServerState,
) {
    val callback: BluetoothGattServerCallback =
        object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(
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

            override fun onCharacteristicReadRequest(
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

            override fun onCharacteristicWriteRequest(
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

            override fun onDescriptorReadRequest(
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
                        // Return the actual CCCD value the device wrote, or disabled
                        val cccdValue =
                            state.subscriptionModes[key]
                                ?: BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                        sendResponseSafe(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, cccdValue)
                    } else {
                        sendResponseSafe(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, byteArrayOf())
                    }
                }
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

            override fun onExecuteWrite(
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

            override fun onNotificationSent(
                device: BluetoothDevice,
                status: Int,
            ) {
                // Called on Binder thread - ConcurrentHashMap + CompletableDeferred are both thread-safe
                state.pendingNotifySent.remove(device.address)?.complete(status)
            }

            override fun onServiceAdded(
                status: Int,
                service: android.bluetooth.BluetoothGattService,
            ) {
                // Called on Binder thread - @Volatile + CompletableDeferred.complete is thread-safe
                state.pendingServiceAdd?.complete(status)
            }

            override fun onMtuChanged(
                device: BluetoothDevice,
                mtu: Int,
            ) {
                state.scope.launch {
                    val deviceId = Identifier(device.address)
                    state.updateMtu(deviceId, mtu)
                }
            }
        }

    // --- CCCD handling ---

    private fun handleCccdWrite(
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

    // --- Prepared write handling ---

    private fun handlePreparedWrite(
        device: BluetoothDevice,
        deviceId: Identifier,
        requestId: Int,
        charUuid: Uuid,
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

    // --- Execute write dispatch ---

    private suspend fun dispatchAssembledWrites(
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

    // --- Safe response helper ---

    private fun sendResponseSafe(
        device: BluetoothDevice,
        requestId: Int,
        status: Int,
        offset: Int,
        value: ByteArray?,
    ) {
        if (!state.isOpen.get()) return
        try {
            state.nativeServer?.sendResponse(device, requestId, status, offset, value)
        } catch (_: SecurityException) {
            // Ignore - device disconnected or permission lost
        }
    }
}
