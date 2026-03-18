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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
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
 * which runs on the same dispatcher.
 *
 * [close] follows the AndroidPeripheral pattern: set the closed flag,
 * close native resources synchronously (BluetoothGattServer.close() is
 * thread-safe), then cancel the scope. No runBlocking — safe to call
 * from any context including handler callbacks.
 *
 * Thread-safe fields accessed from Binder threads:
 * - [pendingNotifySent]: ConcurrentHashMap, CompletableDeferred.complete is safe
 * - [pendingServiceAdd]: @Volatile, CompletableDeferred.complete is safe
 * - [isOpen]: AtomicBoolean for visibility and atomic close
 *
 * ## Lifecycle
 *
 * Each instance is single-use: after [close], the scope is cancelled and
 * cannot be restarted. Create a new instance via [GattServer] factory to
 * reopen. This matches the Android resource lifecycle.
 *
 * ## Single Instance
 *
 * Android supports only one BluetoothGattServer per app. Opening a second
 * server while one is already open throws [ServerException.OpenFailed].
 * Use [close] to release before opening another.
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
 *
 * ## Pre-API-33 Note
 *
 * On API < 33, [BluetoothGattCharacteristic.value] is set before calling
 * [BluetoothGattServer.notifyCharacteristicChanged]. When sending the same
 * notification to multiple devices in parallel, all async blocks share the
 * same data argument so the write is safe. API 33+ uses the data-parameter
 * overload and avoids the shared mutable field entirely.
 */
internal class AndroidGattServer(
    private val context: Context,
    private val serviceDefinitions: List<ServiceDefinition>,
) : GattServer {

    private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
    private var scope = CoroutineScope(SupervisorJob() + dispatcher + CoroutineName("GattServer"))

    // --- All fields below are accessed ONLY on [dispatcher] unless noted ---

    private var nativeServer: BluetoothGattServer? = null

    private val _connections = MutableStateFlow<List<ServerConnection>>(emptyList())
    override val connections: StateFlow<List<ServerConnection>> = _connections.asStateFlow()

    // Use tryEmit to avoid blocking Binder callback thread on slow collectors.
    // With extraBufferCapacity = 64, events are buffered. If the buffer fills,
    // the oldest event is silently dropped and a warning is logged.
    private val _connectionEvents = MutableSharedFlow<ServerConnectionEvent>(extraBufferCapacity = 64)
    override val connectionEvents: Flow<ServerConnectionEvent> = _connectionEvents.asSharedFlow()

    // Track connected BluetoothDevice instances by Identifier
    private val connectedDevices = mutableMapOf<Identifier, BluetoothDevice>()

    // Track per-device per-characteristic CCCD subscription mode.
    // Value: the raw CCCD bytes the device wrote (0x01,0x00 / 0x02,0x00 / 0x00,0x00)
    private data class SubscriptionKey(val characteristicUuid: Uuid, val device: Identifier)
    private val subscriptionModes = mutableMapOf<SubscriptionKey, ByteArray>()

    // Secondary index: characteristic UUID -> set of subscribed device identifiers.
    // Maintained in sync with subscriptionModes for O(1) broadcast notify lookup.
    private val subscribersByChar = mutableMapOf<Uuid, MutableSet<Identifier>>()

    // Track per-device MTU
    private val deviceMtu = mutableMapOf<Identifier, Int>()

    // Map characteristic UUID -> handler for read/write dispatch
    private val readHandlers = mutableMapOf<Uuid, suspend (Identifier) -> ByteArray>()
    private val writeHandlers = mutableMapOf<Uuid, suspend (Identifier, ByteArray, Boolean) -> GattStatus?>()

    // O(1) lookup cache: characteristic UUID -> native characteristic (built during open)
    private val characteristicCache = mutableMapOf<Uuid, BluetoothGattCharacteristic>()

    // Per-device pending onNotificationSent — ConcurrentHashMap because
    // onNotificationSent fires on a Binder thread and completes the deferred,
    // while notify/indicate write the entry from the serialized dispatcher.
    // Used for BOTH notifications and indications because Android's
    // onNotificationSent fires for both, and you must wait for it before
    // sending the next to the same device.
    private val pendingNotifySent = ConcurrentHashMap<String, CompletableDeferred<Int>>()

    // Pending service addition — @Volatile because written on dispatcher, read on Binder thread
    @Volatile
    private var pendingServiceAdd: CompletableDeferred<Int>? = null

    // AtomicBoolean for atomic close (prevents double-close waste)
    private val isOpen = AtomicBoolean(false)

    // Tracks whether close() has been called — prevents reopen after close
    private val isClosed = AtomicBoolean(false)

    private val callback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            scope.launch {
                val deviceId = Identifier(device.address)
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        connectedDevices[deviceId] = device
                        val connectionCount = connectedDevices.size
                        _connections.update { list ->
                            list + ServerConnection(deviceId, device.name)
                        }
                        logEvent(BleLogEvent.ServerClientEvent(deviceId, "connected ($connectionCount total)"))
                        if (connectionCount >= CONNECTION_WARNING_THRESHOLD) {
                            logEvent(BleLogEvent.Error(
                                deviceId,
                                "High connection count ($connectionCount). Android typically supports " +
                                    "7-15 concurrent BLE connections depending on device. New connections " +
                                    "may be silently rejected.",
                                null,
                            ))
                        }
                        if (!_connectionEvents.tryEmit(ServerConnectionEvent.Connected(deviceId))) {
                            logEvent(BleLogEvent.Error(deviceId, "Connection event buffer full, event dropped", null))
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        connectedDevices.remove(deviceId)
                        deviceMtu.remove(deviceId)
                        // Remove all subscriptions for this device
                        subscriptionModes.keys.removeAll { it.device == deviceId }
                        for ((_, subscribers) in subscribersByChar) {
                            subscribers.remove(deviceId)
                        }
                        _connections.update { list ->
                            list.filter { it.device != deviceId }
                        }
                        // Cancel any pending notification/indication for this device
                        pendingNotifySent.remove(device.address)
                            ?.cancel(kotlinx.coroutines.CancellationException("Device disconnected"))
                        logEvent(BleLogEvent.ServerClientEvent(deviceId, "disconnected"))
                        if (!_connectionEvents.tryEmit(ServerConnectionEvent.Disconnected(deviceId))) {
                            logEvent(BleLogEvent.Error(deviceId, "Connection event buffer full, event dropped", null))
                        }
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

        override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
            // Prepared writes are rejected in onCharacteristicWriteRequest, but a
            // misbehaving client may still send Execute Write. Respond immediately
            // so the client doesn't hang waiting for a response.
            sendResponseSafe(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, 0, null)
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            // Called on Binder thread — ConcurrentHashMap + CompletableDeferred are both thread-safe
            pendingNotifySent.remove(device.address)?.complete(status)
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            // Called on Binder thread — @Volatile + CompletableDeferred.complete is thread-safe
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
        if (isClosed.get()) {
            throw ServerException.OpenFailed(
                "This server instance has been closed and cannot be reopened. " +
                    "Create a new instance via GattServer { ... } factory.",
            )
        }

        withContext(dispatcher) {
            if (isOpen.get()) return@withContext

            // Single-instance enforcement
            if (!instanceLock.compareAndSet(false, true)) {
                throw ServerException.OpenFailed(
                    "Another GattServer is already open. Android supports only one GATT server per app. " +
                        "Call close() on the existing server before opening a new one.",
                )
            }

            logEvent(BleLogEvent.ServerLifecycle("opening"))

            try {
                openInternal()
            } catch (e: Exception) {
                instanceLock.set(false)
                throw e
            }
        }
    }

    private suspend fun openInternal() {
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

        // Build O(1) characteristic lookup cache
        for (service in server.services) {
            for (char in service.characteristics) {
                characteristicCache[char.uuid.toKotlinUuid()] = char
            }
        }

        isOpen.set(true)
        logEvent(BleLogEvent.ServerLifecycle("open (${serviceDefinitions.size} services)"))
    }

    override suspend fun notify(characteristicUuid: Uuid, device: Identifier?, data: ByteArray) {
        withContext(dispatcher) {
            checkOpen()
            val server = nativeServer ?: throw ServerException.NotOpen()
            val nativeChar = characteristicCache[characteristicUuid]
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
                // All subscribed devices for this characteristic — O(1) lookup via secondary index
                val subscribed = subscribersByChar[characteristicUuid] ?: emptySet()
                subscribed.mapNotNull { connectedDevices[it] }
            }

            // Warn if notification payload exceeds any target's MTU
            for (target in targets) {
                val targetId = Identifier(target.address)
                val mtu = deviceMtu[targetId] ?: DEFAULT_MTU
                val maxPayload = mtu - ATT_HEADER_SIZE
                if (data.size > maxPayload) {
                    logEvent(BleLogEvent.Error(
                        targetId,
                        "Notification payload (${data.size}B) exceeds device MTU ($mtu, max payload ${maxPayload}B). " +
                            "Data will be truncated by the BLE stack.",
                        null,
                    ))
                }
            }

            // Send to all targets in parallel — Android only requires
            // per-device serialization, not global serialization.
            // Individual failures are logged but don't cancel other sends.
            targets.map { target ->
                async {
                    try {
                        awaitNotifySend(server, target, nativeChar, data, confirm = false)
                    } catch (e: Exception) {
                        logEvent(BleLogEvent.Error(Identifier(target.address), "notify failed", e))
                    }
                }
            }.awaitAll()

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
            val nativeChar = characteristicCache[characteristicUuid]
                ?: throw ServerException.NotifyFailed("Characteristic $characteristicUuid not found")

            val target = connectedDevices[device]
                ?: throw ServerException.DeviceNotConnected("Device $device not connected")

            // Check CCCD subscription
            val key = SubscriptionKey(characteristicUuid, device)
            val mode = subscriptionModes[key]
            if (mode == null || mode.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                throw ServerException.NotifyFailed("Device $device is not subscribed to $characteristicUuid")
            }

            // Warn on MTU truncation
            val mtu = deviceMtu[device] ?: DEFAULT_MTU
            val maxPayload = mtu - ATT_HEADER_SIZE
            if (data.size > maxPayload) {
                logEvent(BleLogEvent.Error(
                    device,
                    "Indication payload (${data.size}B) exceeds device MTU ($mtu, max payload ${maxPayload}B). " +
                        "Data will be truncated by the BLE stack.",
                    null,
                ))
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
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            pendingNotifySent.remove(device.address)
            throw ServerException.NotifyFailed(
                if (confirm) "Indication timed out" else "Notification timed out",
            )
        }
    }

    /**
     * Close the server. Safe to call from any thread, including handler callbacks.
     *
     * Follows the AndroidPeripheral pattern: set closed flag, release native
     * resources synchronously (BluetoothGattServer.close() is thread-safe),
     * then cancel the scope. No runBlocking — no deadlock risk.
     *
     * Uses [AtomicBoolean.compareAndSet] to ensure exactly one close executes
     * even under concurrent calls.
     */
    override fun close() {
        if (!isOpen.compareAndSet(true, false)) return
        isClosed.set(true)
        logEvent(BleLogEvent.ServerLifecycle("closing"))

        // Close native server first — stops all Binder callbacks
        try {
            nativeServer?.close()
        } catch (_: SecurityException) {
            // Ignore permission errors on close
        }
        nativeServer = null

        // Cancel all pending notifications/indications
        for ((_, deferred) in pendingNotifySent) {
            deferred.cancel(kotlinx.coroutines.CancellationException("Server closed"))
        }
        pendingNotifySent.clear()

        // Cancel scope — all in-flight coroutines (handlers, connection events) stop
        scope.cancel()

        // Reset state (no more coroutines can access these after scope.cancel)
        connectedDevices.clear()
        deviceMtu.clear()
        subscriptionModes.clear()
        subscribersByChar.clear()
        characteristicCache.clear()
        readHandlers.clear()
        writeHandlers.clear()
        _connections.value = emptyList()

        // Release singleton lock
        instanceLock.set(false)
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
            // Pre-API-33: shared mutable field. Safe in parallel notify because all
            // asyncs write the same data bytes. See class KDoc for details.
            characteristic.value = data
            server.notifyCharacteristicChanged(device, characteristic, confirm)
        }
    }

    private fun handleCccdWrite(device: BluetoothDevice, characteristicUuid: Uuid, value: ByteArray) {
        val deviceId = Identifier(device.address)
        val key = SubscriptionKey(characteristicUuid, deviceId)
        if (value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
            subscriptionModes.remove(key)
            subscribersByChar[characteristicUuid]?.remove(deviceId)
            logEvent(BleLogEvent.ServerClientEvent(deviceId, "unsubscribed from $characteristicUuid"))
        } else {
            subscriptionModes[key] = value.copyOf()
            subscribersByChar.getOrPut(characteristicUuid) { mutableSetOf() }.add(deviceId)
            val mode = when {
                value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) -> "notifications"
                value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE) -> "indications"
                else -> "unknown mode"
            }
            logEvent(BleLogEvent.ServerClientEvent(deviceId, "subscribed to $characteristicUuid ($mode)"))
        }
    }

    private fun sendResponseSafe(
        device: BluetoothDevice,
        requestId: Int,
        status: Int,
        offset: Int,
        value: ByteArray?,
    ) {
        if (!isOpen.get()) return
        try {
            nativeServer?.sendResponse(device, requestId, status, offset, value)
        } catch (_: SecurityException) {
            // Ignore — device disconnected or permission lost
        }
    }

    private fun checkOpen() {
        if (!isOpen.get()) throw ServerException.NotOpen()
    }

    internal companion object {
        val CCCD_UUID: Uuid = com.atruedev.kmpble.scanner.uuidFrom("2902")
        const val NOTIFY_TIMEOUT_MS = 5_000L
        const val DEFAULT_MTU = 23
        const val ATT_HEADER_SIZE = 3
        const val CONNECTION_WARNING_THRESHOLD = 7

        // Global single-instance guard — Android supports one GATT server per app
        private val instanceLock = AtomicBoolean(false)
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
