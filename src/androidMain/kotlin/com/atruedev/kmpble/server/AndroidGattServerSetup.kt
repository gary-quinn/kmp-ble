@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.server

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import com.atruedev.kmpble.peripheral.toGattStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

// =============================================================================
// Setup helpers
// =============================================================================

/**
 * Opens the native [BluetoothGattServer], registers handlers, and adds services.
 * Called once from [AndroidGattServer.open] on the serial dispatcher.
 */
internal suspend fun AndroidGattServerState.openInternal(instanceLock: AtomicBoolean) {
    val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: throw ServerException.NotSupported("BluetoothManager not available")

    val adapter =
        bluetoothManager.adapter
            ?: throw ServerException.NotSupported("Bluetooth adapter not available")

    if (!adapter.isEnabled) {
        throw ServerException.OpenFailed("Bluetooth is not enabled")
    }

    val server =
        try {
            bluetoothManager.openGattServer(context, callback.callback)
        } catch (e: SecurityException) {
            throw ServerException.OpenFailed("Missing BLUETOOTH_CONNECT permission", e)
        } ?: throw ServerException.OpenFailed("openGattServer returned null")

    nativeServer.update { server }

    // Register handlers from service definitions
    for (serviceDef in serviceDefinitions) {
        for (charDef in serviceDef.characteristics) {
            charDef.readHandler?.let { readHandlers[charDef.uuid] = it }
            charDef.writeHandler?.let { writeHandlers[charDef.uuid] = it }
        }
    }

    // Add services sequentially (must wait for onServiceAdded for each)
    for (serviceDef in serviceDefinitions) {
        val nativeService = buildNativeService(serviceDef)
        val deferred = CompletableDeferred<Int>()
        pendingServiceAdd.update { deferred }
        if (!server.addService(nativeService)) {
            pendingServiceAdd.update { null }
            throw ServerException.OpenFailed("addService returned false for ${serviceDef.uuid}")
        }
        val addStatus = deferred.await()
        pendingServiceAdd.update { null }
        if (addStatus != BluetoothGatt.GATT_SUCCESS) {
            throw ServerException.OpenFailed(
                "addService failed for ${serviceDef.uuid} with status ${addStatus.toGattStatus()}",
            )
        }
        logEvent(BleLogEvent.ServerLifecycle("service added: ${serviceDef.uuid}"))
    }

    // Build O(1) characteristic lookup cache
    for (service in server.services) {
        for (char in service.characteristics) {
            characteristicCache[char.uuid.toKotlinUuid()] = char
        }
    }

    isOpen.set(true)
    logEvent(BleLogEvent.ServerLifecycle("open (${serviceDefinitions.size} services)"))
}

/**
 * Converts a [ServiceDefinition] to an Android [BluetoothGattService]
 * with auto-added CCCD descriptors and user-defined descriptors.
 */
internal fun buildNativeService(definition: ServiceDefinition): BluetoothGattService {
    val service =
        BluetoothGattService(
            definition.uuid.toJavaUuid(),
            BluetoothGattService.SERVICE_TYPE_PRIMARY,
        )

    for (charDef in definition.characteristics) {
        val characteristic =
            BluetoothGattCharacteristic(
                charDef.uuid.toJavaUuid(),
                charDef.properties.toAndroidProperties(),
                charDef.permissions.toAndroidPermissions(),
            )

        // Auto-add CCCD if notify or indicate
        if (charDef.properties.notify || charDef.properties.indicate) {
            val cccd =
                BluetoothGattDescriptor(
                    AndroidGattServer.CCCD_UUID.toJavaUuid(),
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
                )
            characteristic.addDescriptor(cccd)
        }

        // Add user-defined descriptors
        for (descDef in charDef.descriptors) {
            characteristic.addDescriptor(
                BluetoothGattDescriptor(descDef.uuid.toJavaUuid(), BluetoothGattDescriptor.PERMISSION_READ),
            )
        }

        service.addCharacteristic(characteristic)
    }

    return service
}

// =============================================================================
// Notify/indicate delivery
// =============================================================================

/**
 * Send a notification or indication to a device and await `onNotificationSent`.
 *
 * Android's BLE stack requires waiting for onNotificationSent before sending
 * the next notification to the same device. This function serializes per-device
 * and handles both notifications (confirm=false) and indications (confirm=true).
 *
 * @return The GATT status from onNotificationSent
 */
internal suspend fun AndroidGattServerState.awaitNotifySend(
    server: BluetoothGattServer,
    device: BluetoothDevice,
    characteristic: BluetoothGattCharacteristic,
    data: ByteArray,
    confirm: Boolean,
): Int {
    val deferred = CompletableDeferred<Int>()
    pendingNotifySent[device.address] = deferred

    try {
        notifyDevice(server, device, characteristic, data, confirm)
    } catch (e: Exception) {
        pendingNotifySent.remove(device.address)
        throw e
    }

    return try {
        withTimeout(AndroidGattServer.NOTIFY_TIMEOUT_MS) { deferred.await() }
    } catch (_: TimeoutCancellationException) {
        pendingNotifySent.remove(device.address)
        throw ServerException.NotifyFailed(
            if (confirm) "Indication timed out" else "Notification timed out",
        )
    }
}

/** Calls [BluetoothGattServer.notifyCharacteristicChanged] on the native server. */
internal fun notifyDevice(
    server: BluetoothGattServer,
    device: BluetoothDevice,
    characteristic: BluetoothGattCharacteristic,
    data: ByteArray,
    confirm: Boolean,
) {
    server.notifyCharacteristicChanged(device, characteristic, confirm, data)
}
