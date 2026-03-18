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
import com.atruedev.kmpble.peripheral.toGattStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
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
import kotlinx.coroutines.withContext
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
 * - BluetoothGattServerCallback methods arrive on Binder threads
 * - All handler invocations and state mutations serialized via dispatcher
 * - notify/indicate use CompletableDeferred to bridge callback -> suspend
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
 * The server tracks this internally per device per characteristic.
 * notify() only sends to subscribed devices.
 */
internal class AndroidGattServer(
    private val context: Context,
    private val serviceDefinitions: List<ServiceDefinition>,
) : GattServer {

    private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private var nativeServer: BluetoothGattServer? = null

    private val _connections = MutableStateFlow<List<ServerConnection>>(emptyList())
    override val connections: StateFlow<List<ServerConnection>> = _connections.asStateFlow()

    private val _connectionEvents = MutableSharedFlow<ServerConnectionEvent>(extraBufferCapacity = 16)
    override val connectionEvents: Flow<ServerConnectionEvent> = _connectionEvents.asSharedFlow()

    // Track connected BluetoothDevice instances by Identifier
    private val connectedDevices = mutableMapOf<Identifier, BluetoothDevice>()

    // Track per-device per-characteristic CCCD subscriptions
    private val subscriptions = MutableStateFlow<Map<Uuid, Set<Identifier>>>(emptyMap())

    // Track per-device MTU
    private val deviceMtu = mutableMapOf<Identifier, Int>()

    // Map characteristic UUID -> handler for read/write dispatch
    private val readHandlers = mutableMapOf<Uuid, suspend (Identifier) -> ByteArray>()
    private val writeHandlers = mutableMapOf<Uuid, suspend (Identifier, ByteArray, Boolean) -> GattStatus?>()

    // Pending notification/indication acknowledgement
    private var pendingNotificationSent: CompletableDeferred<Int>? = null

    // Pending service addition
    private var pendingServiceAdd: CompletableDeferred<Int>? = null

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
                        _connectionEvents.emit(ServerConnectionEvent.Connected(deviceId))
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        connectedDevices.remove(deviceId)
                        deviceMtu.remove(deviceId)
                        // Remove from all subscriptions
                        subscriptions.update { current ->
                            current.mapValues { (_, subscribers) -> subscribers - deviceId }
                        }
                        _connections.update { list ->
                            list.filter { it.device != deviceId }
                        }
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
                    sendResponseSafe(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, responseData)
                } catch (e: Exception) {
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

                // Prepared writes (long writes) — not supported in v0.2
                if (preparedWrite) {
                    if (responseNeeded) {
                        sendResponseSafe(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null)
                    }
                    return@launch
                }

                val handler = writeHandlers[charUuid]
                if (handler == null) {
                    if (responseNeeded) {
                        sendResponseSafe(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, offset, null)
                    }
                    return@launch
                }

                try {
                    val status = handler(deviceId, value ?: byteArrayOf(), responseNeeded)
                    if (responseNeeded) {
                        val nativeStatus = status?.toAndroidGattStatus() ?: BluetoothGatt.GATT_SUCCESS
                        sendResponseSafe(device, requestId, nativeStatus, offset, null)
                    }
                } catch (e: Exception) {
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
                    val subs = subscriptions.value[charUuid] ?: emptySet()
                    val value = if (deviceId in subs) {
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    } else {
                        BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    }
                    sendResponseSafe(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                } else {
                    // Non-CCCD descriptors: return empty
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
            pendingNotificationSent?.complete(status)
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            pendingServiceAdd?.complete(status)
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            scope.launch {
                val deviceId = Identifier(device.address)
                deviceMtu[deviceId] = mtu
            }
        }
    }

    override suspend fun open() {
        withContext(dispatcher) {
            if (isOpen) return@withContext

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
            }

            isOpen = true
        }
    }

    override suspend fun notify(characteristicUuid: Uuid, device: Identifier?, data: ByteArray) {
        withContext(dispatcher) {
            checkOpen()
            val server = nativeServer ?: throw ServerException.NotOpen()
            val nativeChar = findNativeCharacteristic(characteristicUuid)
                ?: throw ServerException.NotifyFailed("Characteristic $characteristicUuid not found")

            val targets = if (device != null) {
                val connected = connectedDevices[device]
                    ?: throw ServerException.DeviceNotConnected("Device $device not connected")
                listOf(connected)
            } else {
                val subscribed = subscriptions.value[characteristicUuid] ?: emptySet()
                connectedDevices.filterKeys { it in subscribed }.values.toList()
            }

            for (target in targets) {
                try {
                    notifyDevice(server, target, nativeChar, data, confirm = false)
                } catch (e: SecurityException) {
                    throw ServerException.NotifyFailed("Missing BLUETOOTH_CONNECT permission", e)
                }
            }
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

            val deferred = CompletableDeferred<Int>()
            pendingNotificationSent = deferred

            try {
                notifyDevice(server, target, nativeChar, data, confirm = true)
            } catch (e: SecurityException) {
                pendingNotificationSent = null
                throw ServerException.NotifyFailed("Missing BLUETOOTH_CONNECT permission", e)
            }

            val status = deferred.await()
            pendingNotificationSent = null
            if (status != BluetoothGatt.GATT_SUCCESS) {
                throw ServerException.NotifyFailed("Indication failed with status ${status.toGattStatus()}")
            }
        }
    }

    override fun close() {
        if (!isOpen) return
        isOpen = false
        try {
            nativeServer?.close()
        } catch (_: SecurityException) {
            // Ignore permission errors on close
        }
        nativeServer = null
        connectedDevices.clear()
        deviceMtu.clear()
        readHandlers.clear()
        writeHandlers.clear()
        subscriptions.value = emptyMap()
        _connections.value = emptyList()
        scope.cancel()
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
        subscriptions.update { current ->
            val subscribers = current[characteristicUuid]?.toMutableSet() ?: mutableSetOf()
            when {
                value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                    value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE) -> {
                    subscribers.add(deviceId)
                }
                value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE) -> {
                    subscribers.remove(deviceId)
                }
            }
            current + (characteristicUuid to subscribers)
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

internal fun GattStatus.toAndroidGattStatus(): Int = when (this) {
    GattStatus.Success -> BluetoothGatt.GATT_SUCCESS
    GattStatus.InsufficientAuthentication -> BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION
    GattStatus.InsufficientEncryption -> BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION
    GattStatus.InsufficientAuthorization -> BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION
    GattStatus.InvalidOffset -> BluetoothGatt.GATT_INVALID_OFFSET
    GattStatus.InvalidAttributeLength -> BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH
    GattStatus.ReadNotPermitted -> BluetoothGatt.GATT_READ_NOT_PERMITTED
    GattStatus.WriteNotPermitted -> BluetoothGatt.GATT_WRITE_NOT_PERMITTED
    GattStatus.RequestNotSupported -> BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
    GattStatus.ConnectionCongested -> BluetoothGatt.GATT_CONNECTION_CONGESTED
    GattStatus.Failure -> BluetoothGatt.GATT_FAILURE
    is GattStatus.Unknown -> platformCode
}
