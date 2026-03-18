@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.server

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import com.atruedev.kmpble.peripheral.toAndroidGattStatus
import com.atruedev.kmpble.peripheral.toGattStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/**
 * Android implementation of [GattServer] using [BluetoothGattServer].
 *
 * ## Architecture
 *
 * - Uses [BluetoothManager.openGattServer] to create the native server
 * - Implements [BluetoothGattServerCallback] for all client interactions
 * - Handlers (onRead/onWrite) are dispatched on a serialized dispatcher
 *   (same limitedParallelism(1) pattern as AndroidPeripheral)
 * - CCCD tracking: maintains a map of which devices are subscribed
 *   to which characteristics, for notify/indicate delivery
 *
 * ## Threading
 *
 * All mutable state is accessed exclusively on the serialized [dispatcher]
 * (limitedParallelism(1)). Binder-thread callbacks dispatch to [scope]
 * which runs on the same dispatcher. [close] also routes through the
 * dispatcher via [runBlocking] to ensure safe teardown.
 *
 * The only exception is [pendingNotifySent] — a [ConcurrentHashMap]
 * of per-device [CompletableDeferred] instances. The map is thread-safe
 * and [CompletableDeferred.complete] is thread-safe, so
 * [onNotificationSent] can safely call it from a Binder thread.
 *
 * ## Service Setup
 *
 * On [open], the server:
 * 1. Opens BluetoothGattServer
 * 2. Converts each ServiceDefinition -> BluetoothGattService
 * 3. Adds CCCD (0x2902) descriptor automatically for notify/indicate chars
 * 4. Calls addService() for each (must wait for onServiceAdded callback)
 *
 * ## CCCD Handling
 *
 * When a client writes to a CCCD descriptor (0x2902):
 * - [0x01, 0x00] = enable notifications
 * - [0x02, 0x00] = enable indications
 * - [0x00, 0x00] = disable
 * The server tracks subscription mode per device per characteristic.
 * notify()/indicate() only send to subscribed devices.
 */
internal class AndroidGattServer(
    private val context: Context,
    private val serviceDefinitions: List<ServiceDefinition>,
) : GattServer {

    private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher + CoroutineName("GattServer"))

    // --- All fields below are accessed ONLY on [dispatcher] unless noted ---

    private var nativeServer: BluetoothGattServer? = null

    private val _connections = MutableStateFlow<List<ServerConnection>>(emptyList())
    override val connections: StateFlow<List<ServerConnection>> = _connections.asStateFlow()

    private val _connectionEvents = MutableSharedFlow<ServerConnectionEvent>(extraBufferCapacity = 16)
    override val connectionEvents: Flow<ServerConnectionEvent> = _connectionEvents.asSharedFlow()

    // Track connected BluetoothDevice instances by Identifier
    private val connectedDevices = mutableMapOf<Identifier, BluetoothDevice>()

    // Track per-device per-characteristic CCCD subscription mode.
    // Value: the raw CCCD bytes the device wrote (0x01,0x00 / 0x02,0x00 / 0x00,0x00)
    private data class SubscriptionKey(val characteristicUuid: Uuid, val device: Identifier)
    private val subscriptionModes = mutableMapOf<SubscriptionKey, ByteArray>()

    // Track per-device MTU
    private val deviceMtu = mutableMapOf<Identifier, Int>()

    // Map characteristic UUID -> handler for read/write dispatch
    private val readHandlers = mutableMapOf<Uuid, suspend (Identifier) -> ByteArray>()
    private val writeHandlers = mutableMapOf<Uuid, suspend (Identifier, ByteArray, Boolean) -> GattStatus?>()

    // Per-device pending onNotificationSent — ConcurrentHashMap because
    // onNotificationSent fires on a Binder thread and completes the deferred,
    // while notify/indicate write the entry from the serialized dispatcher.
    // Used for BOTH notifications and indications because Android's
    // onNotificationSent fires for both, and you must wait for it before
    // sending the next to the same device.
    private val pendingNotifySent = ConcurrentHashMap<String, CompletableDeferred<Int>>()

    // Pending service addition (only used inside open(), always on dispatcher)
    private var pendingServiceAdd: CompletableDeferred<Int>? = null

    @Volatile
    private var isOpen = false

    private val callback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            scope.launch {
                val deviceId = Identifier(device.address)
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        connectedDevices[deviceId] = device
                        _connections.update { list ->
                            list + ServerConnection(deviceId, device.name)
                        }
                        logEvent(BleLogEvent.ServerClientEvent(deviceId, "connected"))
                        _connectionEvents.emit(ServerConnectionEvent.Connected(deviceId))
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        connectedDevices.remove(deviceId)
                        deviceMtu.remove(deviceId)
                        // Remove all subscriptions for this device
                        subscriptionModes.keys.removeAll { it.device == deviceId }
                        _connections.update { list ->
                            list.filter { it.device != deviceId }
                        }
                        // Cancel any pending notification/indication for this device
                        pendingNotifySent.remove(device.address)
                            ?.cancel(kotlinx.coroutines.CancellationException("Device disconnected"))
                        logEvent(BleLogEvent.ServerClientEvent(deviceId, "disconnected"))
                        _connectionEvents.emit(ServerConnectionEvent.Disconnected(deviceId))
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
            scope.launch {
                val deviceId = Identifier(device.address)
                val charUuid = characteristic.uuid.toKotlinUuid()
                val handler = readHandlers[charUuid]

                if (handler == null) {
                    logEvent(BleLogEvent.ServerRequest(deviceId, "read-rejected (no handler)", charUuid, GattStatus.ReadNotPermitted))
                    sendResponseSafe(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
                    return@launch
                }

                try {
                    val data = handler(deviceId)
                    // Support offset reads for characteristics > MTU
                    val responseData = if (offset > 0 && offset < data.size) {
                        data.sliceArray(offset until data.size)
                    } else if (offset >= data.size && offset > 0) {
                        byteArrayOf() // Past end of data
                    } else {
                        data
                    }
                    logEvent(BleLogEvent.ServerRequest(deviceId, "read (${responseData.size}B, offset=$offset)", charUuid, GattStatus.Success))
                    sendResponseSafe(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, responseData)
                } catch (_: Exception) {
                    logEvent(BleLogEvent.ServerRequest(deviceId, "read-failed (handler threw)", charUuid, GattStatus.Failure))
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
            scope.launch {
                val deviceId = Identifier(device.address)
                val charUuid = characteristic.uuid.toKotlinUuid()

                // Prepared writes (long writes) — not supported, respond with RequestNotSupported
                if (preparedWrite) {
                    logEvent(BleLogEvent.ServerRequest(deviceId, "prepared-write-rejected", charUuid, GattStatus.RequestNotSupported))
                    if (responseNeeded) {
                        sendResponseSafe(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null)
                    }
                    return@launch
                }

                val handler = writeHandlers[charUuid]
                if (handler == null) {
                    logEvent(BleLogEvent.ServerRequest(deviceId, "write-rejected (no handler)", charUuid, GattStatus.WriteNotPermitted))
                    if (responseNeeded) {
                        sendResponseSafe(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, offset, null)
                    }
                    return@launch
                }

                try {
                    val status = handler(deviceId, value ?: byteArrayOf(), responseNeeded)
                    logEvent(BleLogEvent.ServerRequest(deviceId, "write (${value?.size ?: 0}B)", charUuid, status))
                    if (responseNeeded) {
                        val nativeStatus = status?.toAndroidGattStatus() ?: BluetoothGatt.GATT_SUCCESS
                        sendResponseSafe(device, requestId, nativeStatus, offset, null)
                    }
                } catch (_: Exception) {
                    logEvent(BleLogEvent.ServerRequest(deviceId, "write-failed (handler threw)", charUuid, GattStatus.Failure))
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
            scope.launch {
                val descUuid = descriptor.uuid.toKotlinUuid()
                if (descUuid == CCCD_UUID) {
                    val deviceId = Identifier(device.address)
                    val charUuid = descriptor.characteristic.uuid.toKotlinUuid()
                    val key = SubscriptionKey(charUuid, deviceId)
                    // Return the actual CCCD value the device wrote, or disabled
                    val value = subscriptionModes[key] ?: BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    sendResponseSafe(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
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
            scope.launch {
                val descUuid = descriptor.uuid.toKotlinUuid()
                if (descUuid == CCCD_UUID && value != null) {
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

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            // Called on Binder thread — ConcurrentHashMap + CompletableDeferred are both thread-safe
            pendingNotifySent.remove(device.address)?.complete(status)
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            pendingServiceAdd?.complete(status)
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            scope.launch {
                val deviceId = Identifier(device.address)
                deviceMtu[deviceId] = mtu
                logEvent(BleLogEvent.ServerClientEvent(deviceId, "MTU changed to $mtu"))
            }
        }
    }

    override suspend fun open() {
        withContext(dispatcher) {
            if (isOpen) return@withContext

            logEvent(BleLogEvent.ServerLifecycle("opening"))

            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                ?: throw ServerException.NotSupported("BluetoothManager not available")

            val adapter = bluetoothManager.adapter
                ?: throw ServerException.NotSupported("Bluetooth adapter not available")

            if (!adapter.isEnabled) {
                throw ServerException.OpenFailed("Bluetooth is not enabled")
            }

            val server = try {
                bluetoothManager.openGattServer(context, callback)
            } catch (e: SecurityException) {
                throw ServerException.OpenFailed("Missing BLUETOOTH_CONNECT permission", e)
            } ?: throw ServerException.OpenFailed("openGattServer returned null")

            nativeServer = server

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
                pendingServiceAdd = deferred
                if (!server.addService(nativeService)) {
                    pendingServiceAdd = null
                    throw ServerException.OpenFailed("addService returned false for ${serviceDef.uuid}")
                }
                val status = deferred.await()
                pendingServiceAdd = null
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    throw ServerException.OpenFailed(
                        "addService failed for ${serviceDef.uuid} with status ${status.toGattStatus()}",
                    )
                }
                logEvent(BleLogEvent.ServerLifecycle("service added: ${serviceDef.uuid}"))
            }

            isOpen = true
            logEvent(BleLogEvent.ServerLifecycle("open (${serviceDefinitions.size} services)"))
        }
    }

    override suspend fun notify(characteristicUuid: Uuid, device: Identifier?, data: ByteArray) {
        withContext(dispatcher) {
            checkOpen()
            val server = nativeServer ?: throw ServerException.NotOpen()
            val nativeChar = findNativeCharacteristic(characteristicUuid)
                ?: throw ServerException.NotifyFailed("Characteristic $characteristicUuid not found")

            val targets = if (device != null) {
                // Specific device — verify connected and subscribed
                val connected = connectedDevices[device]
                    ?: throw ServerException.DeviceNotConnected("Device $device not connected")
                val key = SubscriptionKey(characteristicUuid, device)
                val mode = subscriptionModes[key]
                if (mode == null || mode.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                    return@withContext // Not subscribed, skip silently
                }
                listOf(connected)
            } else {
                // All subscribed devices for this characteristic
                subscriptionModes.entries
                    .filter { (key, value) ->
                        key.characteristicUuid == characteristicUuid &&
                            !value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
                    }
                    .mapNotNull { (key, _) -> connectedDevices[key.device] }
            }

            for (target in targets) {
                try {
                    awaitNotifySend(server, target, nativeChar, data, confirm = false)
                } catch (e: SecurityException) {
                    throw ServerException.NotifyFailed("Missing BLUETOOTH_CONNECT permission", e)
                }
            }
            logEvent(BleLogEvent.ServerRequest(
                device ?: Identifier("broadcast"),
                "notify (${data.size}B to ${targets.size} devices)",
                characteristicUuid, GattStatus.Success,
            ))
        }
    }

    override suspend fun indicate(characteristicUuid: Uuid, device: Identifier, data: ByteArray) {
        withContext(dispatcher) {
            checkOpen()
            val server = nativeServer ?: throw ServerException.NotOpen()
            val nativeChar = findNativeCharacteristic(characteristicUuid)
                ?: throw ServerException.NotifyFailed("Characteristic $characteristicUuid not found")

            val target = connectedDevices[device]
                ?: throw ServerException.DeviceNotConnected("Device $device not connected")

            // Check CCCD subscription
            val key = SubscriptionKey(characteristicUuid, device)
            val mode = subscriptionModes[key]
            if (mode == null || mode.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                throw ServerException.NotifyFailed("Device $device is not subscribed to $characteristicUuid")
            }

            try {
                val status = awaitNotifySend(server, target, nativeChar, data, confirm = true)
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    throw ServerException.NotifyFailed("Indication failed with status ${status.toGattStatus()}")
                }
            } catch (e: SecurityException) {
                throw ServerException.NotifyFailed("Missing BLUETOOTH_CONNECT permission", e)
            }
            logEvent(BleLogEvent.ServerRequest(device, "indicate (${data.size}B)", characteristicUuid, GattStatus.Success))
        }
    }

    /**
     * Send a notification or indication to a device and await [onNotificationSent].
     *
     * Android's BLE stack requires waiting for onNotificationSent before sending
     * the next notification to the same device. This method serializes per-device
     * and handles both notifications (confirm=false) and indications (confirm=true).
     *
     * @return The GATT status from onNotificationSent
     */
    private suspend fun awaitNotifySend(
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
            withTimeout(NOTIFY_TIMEOUT_MS) { deferred.await() }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            pendingNotifySent.remove(device.address)
            throw ServerException.NotifyFailed(
                if (confirm) "Indication timed out" else "Notification timed out",
            )
        }
    }

    override fun close() {
        if (!isOpen) return
        logEvent(BleLogEvent.ServerLifecycle("closing"))
        // Route teardown through the serialized dispatcher so in-flight
        // callbacks finish before we clear state.
        runBlocking(dispatcher) {
            if (!isOpen) return@runBlocking
            isOpen = false

            try {
                nativeServer?.close()
            } catch (_: SecurityException) {
                // Ignore permission errors on close
            }
            nativeServer = null
            connectedDevices.clear()
            deviceMtu.clear()
            subscriptionModes.clear()
            readHandlers.clear()
            writeHandlers.clear()
            _connections.value = emptyList()

            // Cancel all pending notifications/indications
            for ((_, deferred) in pendingNotifySent) {
                deferred.cancel(kotlinx.coroutines.CancellationException("Server closed"))
            }
            pendingNotifySent.clear()
        }
        scope.cancel()
        logEvent(BleLogEvent.ServerLifecycle("closed"))
    }

    private fun buildNativeService(definition: ServiceDefinition): BluetoothGattService {
        val service = BluetoothGattService(
            definition.uuid.toJavaUuid(),
            BluetoothGattService.SERVICE_TYPE_PRIMARY,
        )

        for (charDef in definition.characteristics) {
            val characteristic = BluetoothGattCharacteristic(
                charDef.uuid.toJavaUuid(),
                charDef.properties.toAndroidProperties(),
                charDef.permissions.toAndroidPermissions(),
            )

            // Auto-add CCCD if notify or indicate
            if (charDef.properties.notify || charDef.properties.indicate) {
                val cccd = BluetoothGattDescriptor(
                    CCCD_UUID.toJavaUuid(),
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

    @Suppress("DEPRECATION")
    private fun notifyDevice(
        server: BluetoothGattServer,
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
        data: ByteArray,
        confirm: Boolean,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            server.notifyCharacteristicChanged(device, characteristic, confirm, data)
        } else {
            characteristic.value = data
            server.notifyCharacteristicChanged(device, characteristic, confirm)
        }
    }

    private fun handleCccdWrite(device: BluetoothDevice, characteristicUuid: Uuid, value: ByteArray) {
        val deviceId = Identifier(device.address)
        val key = SubscriptionKey(characteristicUuid, deviceId)
        if (value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
            subscriptionModes.remove(key)
            logEvent(BleLogEvent.ServerClientEvent(deviceId, "unsubscribed from $characteristicUuid"))
        } else {
            subscriptionModes[key] = value.copyOf()
            val mode = when {
                value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) -> "notifications"
                value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE) -> "indications"
                else -> "unknown mode"
            }
            logEvent(BleLogEvent.ServerClientEvent(deviceId, "subscribed to $characteristicUuid ($mode)"))
        }
    }

    private fun findNativeCharacteristic(uuid: Uuid): BluetoothGattCharacteristic? {
        val server = nativeServer ?: return null
        for (service in server.services) {
            val char = service.getCharacteristic(uuid.toJavaUuid())
            if (char != null) return char
        }
        return null
    }

    private fun sendResponseSafe(
        device: BluetoothDevice,
        requestId: Int,
        status: Int,
        offset: Int,
        value: ByteArray?,
    ) {
        // Device may have disconnected before we send the response
        if (!connectedDevices.containsKey(Identifier(device.address))) return
        try {
            nativeServer?.sendResponse(device, requestId, status, offset, value)
        } catch (_: SecurityException) {
            // Ignore — device disconnected or permission lost
        }
    }

    private fun checkOpen() {
        if (!isOpen) throw ServerException.NotOpen()
    }

    internal companion object {
        val CCCD_UUID: Uuid = com.atruedev.kmpble.scanner.uuidFrom("2902")
        const val NOTIFY_TIMEOUT_MS = 10_000L
    }
}

internal fun ServerCharacteristic.Properties.toAndroidProperties(): Int {
    var flags = 0
    if (read) flags = flags or BluetoothGattCharacteristic.PROPERTY_READ
    if (write) flags = flags or BluetoothGattCharacteristic.PROPERTY_WRITE
    if (writeWithoutResponse) flags = flags or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
    if (notify) flags = flags or BluetoothGattCharacteristic.PROPERTY_NOTIFY
    if (indicate) flags = flags or BluetoothGattCharacteristic.PROPERTY_INDICATE
    return flags
}

internal fun ServerCharacteristic.Permissions.toAndroidPermissions(): Int {
    var flags = 0
    if (read) flags = flags or BluetoothGattCharacteristic.PERMISSION_READ
    if (readEncrypted) flags = flags or BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
    if (write) flags = flags or BluetoothGattCharacteristic.PERMISSION_WRITE
    if (writeEncrypted) flags = flags or BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
    return flags
}
