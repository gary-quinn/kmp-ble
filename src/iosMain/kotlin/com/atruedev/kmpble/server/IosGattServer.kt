package com.atruedev.kmpble.server

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.bleDataFromNSData
import com.atruedev.kmpble.emptyBleData
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.internal.IosPeripheralManagerDelegate
import com.atruedev.kmpble.internal.PeripheralManagerProvider
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import com.atruedev.kmpble.scanner.uuidFrom
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import platform.CoreBluetooth.CBATTErrorInsufficientAuthentication
import platform.CoreBluetooth.CBATTErrorInsufficientAuthorization
import platform.CoreBluetooth.CBATTErrorInsufficientEncryption
import platform.CoreBluetooth.CBATTErrorInvalidAttributeValueLength
import platform.CoreBluetooth.CBATTErrorInvalidOffset
import platform.CoreBluetooth.CBATTErrorReadNotPermitted
import platform.CoreBluetooth.CBATTErrorRequestNotSupported
import platform.CoreBluetooth.CBATTErrorSuccess
import platform.CoreBluetooth.CBATTErrorWriteNotPermitted
import platform.CoreBluetooth.CBATTRequest
import platform.CoreBluetooth.CBAttributePermissionsReadEncryptionRequired
import platform.CoreBluetooth.CBAttributePermissionsReadable
import platform.CoreBluetooth.CBAttributePermissionsWriteEncryptionRequired
import platform.CoreBluetooth.CBAttributePermissionsWriteable
import platform.CoreBluetooth.CBCentral
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicPropertyIndicate
import platform.CoreBluetooth.CBCharacteristicPropertyNotify
import platform.CoreBluetooth.CBCharacteristicPropertyRead
import platform.CoreBluetooth.CBCharacteristicPropertyWrite
import platform.CoreBluetooth.CBCharacteristicPropertyWriteWithoutResponse
import platform.CoreBluetooth.CBMutableCharacteristic
import platform.CoreBluetooth.CBMutableService
import platform.CoreBluetooth.CBPeripheralManager
import platform.CoreBluetooth.CBPeripheralManagerStatePoweredOn
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSData
import platform.Foundation.NSError

/**
 * iOS implementation of [GattServer] using [CBPeripheralManager].
 *
 * ## Architecture
 *
 * Uses CBPeripheralManager delegate protocol for all server interactions.
 * Follows the same serialized dispatcher pattern as IosPeripheral
 * (limitedParallelism(1) on Dispatchers.Default).
 *
 * ## Key differences from Android implementation
 *
 * ### Connection tracking
 * iOS CBPeripheralManager does NOT provide connection/disconnection
 * callbacks. Centrals are discovered lazily:
 * - On didSubscribeTo: add central to connections
 * - On didReceiveRead/didReceiveWrite: add central to connections
 * - K/N limitation: didUnsubscribeFromCharacteristic cannot be overridden
 *   separately (same Kotlin type signature as didSubscribeToCharacteristic).
 *   Centrals remain in connections until [close] is called.
 *
 * ### CCCD handling
 * iOS manages CCCD automatically. We get didSubscribeTo callbacks
 * instead of raw CCCD write events. No need to manually
 * add CCCD descriptors to characteristics.
 *
 * ### Notification backpressure
 * updateValue() returns false when the transmit queue is full.
 * Must wait for peripheralManagerIsReadyToUpdateSubscribers()
 * before retrying. Implemented via [CompletableDeferred].
 *
 * ### Indication handling
 * iOS doesn't distinguish notify vs indicate at the API level.
 * The characteristic property (notify vs indicate) determines
 * the behavior. [indicate] calls the same updateValue() as [notify].
 * iOS handles confirmation transparently.
 *
 * ### Write request batching
 * didReceiveWrite delivers [CBATTRequest] array. Must process all
 * and respond once to the first request.
 *
 * ## Threading
 *
 * Delegate callbacks arrive on [PeripheralManagerProvider]'s serial GCD
 * queue and dispatch all mutable-state access into [scope] (serialized
 * via [dispatcher]). CBPeripheralManager.respondToRequest() can be called
 * from any thread.
 *
 * [close] uses [AtomicInt] CAS for exactly-once semantics and is safe
 * to call from any thread, matching the Android pattern.
 */
internal class IosGattServer(
    private val serviceDefinitions: List<ServiceDefinition>,
    private val manager: CBPeripheralManager = PeripheralManagerProvider.manager,
    private val delegate: IosPeripheralManagerDelegate = PeripheralManagerProvider.delegate,
) : GattServer {

    private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher + CoroutineName("IosGattServer"))

    private val _connections = MutableStateFlow<List<ServerConnection>>(emptyList())
    override val connections: StateFlow<List<ServerConnection>> = _connections.asStateFlow()

    private val _connectionEvents = MutableSharedFlow<ServerConnectionEvent>(extraBufferCapacity = 64)
    override val connectionEvents: Flow<ServerConnectionEvent> = _connectionEvents.asSharedFlow()

    // --- All mutable collections below accessed ONLY on [dispatcher] ---

    private val connectedCentrals = mutableMapOf<String, CBCentral>()
    private val subscriptions = mutableMapOf<String, MutableSet<String>>()
    private val readHandlers = mutableMapOf<Uuid, suspend (Identifier) -> BleData>()
    private val writeHandlers =
        mutableMapOf<Uuid, suspend (Identifier, BleData, Boolean) -> GattStatus?>()
    private val characteristicCache = mutableMapOf<Uuid, CBMutableCharacteristic>()

    @Volatile
    private var readyToUpdate = CompletableDeferred<Unit>().apply { complete(Unit) }

    @Volatile
    private var pendingServiceAdd: CompletableDeferred<NSError?>? = null

    private val isOpen = AtomicInt(0)
    private val isClosed = AtomicInt(0)

    // --- Public API ---

    override suspend fun open() {
        if (isClosed.value != 0) {
            throw ServerException.OpenFailed(
                "This server instance has been closed and cannot be reopened. " +
                    "Create a new instance via GattServer { ... } factory.",
            )
        }

        withContext(dispatcher) {
            if (isOpen.value != 0) return@withContext

            if (!instanceLock.compareAndSet(0, 1)) {
                throw ServerException.OpenFailed(
                    "Another IosGattServer is already open. iOS uses a single " +
                        "CBPeripheralManager — call close() on the existing server first.",
                )
            }

            logEvent(BleLogEvent.ServerLifecycle("opening"))

            try {
                openInternal()
            } catch (e: Exception) {
                instanceLock.value = 0
                throw e
            }
        }
    }

    private suspend fun openInternal() {
        setDelegateCallbacks(active = true)

        // Force lazy CBPeripheralManager init — the constructor fires
        // peripheralManagerDidUpdateState on our delegate.
        manager

        try {
            withTimeout(POWER_ON_TIMEOUT_MS) {
                delegate.managerState.first { it == CBPeripheralManagerStatePoweredOn }
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            setDelegateCallbacks(active = false)
            throw ServerException.OpenFailed(
                "Timeout waiting for Bluetooth to power on (state: ${delegate.managerState.value})",
            )
        }

        for (serviceDef in serviceDefinitions) {
            for (charDef in serviceDef.characteristics) {
                charDef.readHandler?.let { readHandlers[charDef.uuid] = it }
                charDef.writeHandler?.let { writeHandlers[charDef.uuid] = it }
            }
        }

        for (serviceDef in serviceDefinitions) {
            val nativeService = buildNativeService(serviceDef)
            val deferred = CompletableDeferred<NSError?>()
            pendingServiceAdd = deferred
            manager.addService(nativeService)

            try {
                val error = withTimeout(SERVICE_ADD_TIMEOUT_MS) { deferred.await() }
                pendingServiceAdd = null
                if (error != null) {
                    throw ServerException.OpenFailed(
                        "addService failed for ${serviceDef.uuid}: ${error.localizedDescription}",
                    )
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                pendingServiceAdd = null
                throw ServerException.OpenFailed(
                    "Timeout adding service ${serviceDef.uuid}",
                )
            }

            logEvent(BleLogEvent.ServerLifecycle("service added: ${serviceDef.uuid}"))
        }

        isOpen.value = 1
        logEvent(
            BleLogEvent.ServerLifecycle("open (${serviceDefinitions.size} services)"),
        )
    }

    override suspend fun notify(characteristicUuid: Uuid, device: Identifier?, data: BleData) {
        withContext(dispatcher) {
            checkOpen()
            val nativeChar = characteristicCache[characteristicUuid]
                ?: throw ServerException.NotifyFailed(
                    "Characteristic $characteristicUuid not found",
                )

            val targets: List<CBCentral>? = if (device != null) {
                val central = connectedCentrals[device.value]
                    ?: throw ServerException.DeviceNotConnected(
                        "Device $device not connected",
                    )
                listOf(central)
            } else {
                null
            }

            sendUpdate(nativeChar, data.nsData, targets)

            logEvent(
                BleLogEvent.ServerRequest(
                    device ?: BROADCAST_IDENTIFIER,
                    "notify (${data.size}B)",
                    characteristicUuid,
                    GattStatus.Success,
                ),
            )
        }
    }

    /**
     * Send an indication.
     *
     * iOS doesn't distinguish notify vs indicate at the API level.
     * The characteristic property ([CBCharacteristicPropertyIndicate])
     * determines the behavior — iOS handles confirmation transparently.
     * This method delegates to [notify] with the same underlying
     * `updateValue` call.
     */
    override suspend fun indicate(characteristicUuid: Uuid, device: Identifier, data: BleData) {
        notify(characteristicUuid, device, data)
    }

    override fun close() {
        if (isClosed.compareAndSet(0, 1).not()) return

        val wasOpen = isOpen.compareAndSet(1, 0)
        if (!wasOpen) {
            setDelegateCallbacks(active = false)
            return
        }

        logEvent(BleLogEvent.ServerLifecycle("closing"))

        // Stop accepting new callbacks before cancelling scope — prevents
        // a GCD-queued callback from scope.launch-ing into a cancelled scope.
        setDelegateCallbacks(active = false)

        manager.removeAllServices()

        readyToUpdate.cancel(kotlinx.coroutines.CancellationException("Server closed"))

        scope.cancel()

        connectedCentrals.clear()
        subscriptions.clear()
        characteristicCache.clear()
        readHandlers.clear()
        writeHandlers.clear()
        _connections.value = emptyList()

        instanceLock.value = 0
        logEvent(BleLogEvent.ServerLifecycle("closed"))
    }

    // --- Delegate callback handlers (called on GCD queue, dispatch to scope) ---

    private fun handleServiceAdded(error: NSError?) {
        pendingServiceAdd?.complete(error)
    }

    private fun handleReadRequest(peripheral: CBPeripheralManager, request: CBATTRequest) {
        scope.launch {
            trackCentral(request.central)

            val handler = readHandlers[request.charUuid]
            if (handler == null) {
                logEvent(
                    BleLogEvent.ServerRequest(
                        request.centralId, "read-rejected (no handler)", request.charUuid,
                        GattStatus.ReadNotPermitted,
                    ),
                )
                peripheral.respondToRequest(request, withResult = CBATTErrorRequestNotSupported)
                return@launch
            }

            try {
                val bleData = handler(request.centralId)
                val offset = request.offset.toInt()

                val responseNsData = when {
                    offset >= bleData.size && offset > 0 -> emptyBleData().nsData
                    offset > 0 -> bleData.slice(offset, bleData.size).nsData
                    else -> bleData.nsData
                }
                request.value = responseNsData
                peripheral.respondToRequest(request, withResult = CBATTErrorSuccess)

                logEvent(
                    BleLogEvent.ServerRequest(
                        request.centralId,
                        "read (${responseNsData.length.toInt()}B, offset=$offset)",
                        request.charUuid,
                        GattStatus.Success,
                    ),
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logEvent(
                    BleLogEvent.ServerRequest(
                        request.centralId, "read-failed (handler threw)", request.charUuid,
                        GattStatus.Failure,
                    ),
                )
                peripheral.respondToRequest(request, withResult = CB_ATT_ERROR_UNLIKELY)
            }
        }
    }

    private fun handleWriteRequests(peripheral: CBPeripheralManager, rawRequests: List<*>) {
        @Suppress("UNCHECKED_CAST")
        val requests = rawRequests.filterIsInstance<CBATTRequest>()
        if (requests.isEmpty()) return

        val firstRequest = requests.first()

        scope.launch {
            var failed = false
            var failError: Long = CB_ATT_ERROR_UNLIKELY

            for (request in requests) {
                val data = if (request.value != null) bleDataFromNSData(request.value!!) else emptyBleData()

                trackCentral(request.central)

                val handler = writeHandlers[request.charUuid]
                if (handler == null) {
                    logEvent(
                        BleLogEvent.ServerRequest(
                            request.centralId, "write-rejected (no handler)", request.charUuid,
                            GattStatus.WriteNotPermitted,
                        ),
                    )
                    failed = true
                    failError = CBATTErrorWriteNotPermitted
                    break
                }

                try {
                    val status = handler(request.centralId, data, true)
                    if (status != null && status != GattStatus.Success) {
                        failed = true
                        failError = status.toCBATTError()
                        break
                    }
                    logEvent(
                        BleLogEvent.ServerRequest(
                            request.centralId, "write (${data.size}B)", request.charUuid, status,
                        ),
                    )
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    logEvent(
                        BleLogEvent.ServerRequest(
                            request.centralId, "write-failed (handler threw)", request.charUuid,
                            GattStatus.Failure,
                        ),
                    )
                    failed = true
                    break
                }
            }

            if (failed) {
                peripheral.respondToRequest(firstRequest, withResult = failError)
            } else {
                peripheral.respondToRequest(firstRequest, withResult = CBATTErrorSuccess)
            }
        }
    }

    private fun handleSubscribe(central: CBCentral, characteristic: CBCharacteristic) {
        scope.launch {
            val charUuid = characteristic.UUID.UUIDString
            val centralId = Identifier(central.id)
            val isNewCentral = trackCentral(central)

            subscriptions.getOrPut(charUuid) { mutableSetOf() }.add(central.id)

            logEvent(
                BleLogEvent.ServerClientEvent(centralId, "subscribed to $charUuid"),
            )

            if (isNewCentral) {
                if (!_connectionEvents.tryEmit(ServerConnectionEvent.Connected(centralId))) {
                    logEvent(
                        BleLogEvent.Error(
                            centralId, "Connection event buffer full, event dropped", null,
                        ),
                    )
                }
            }
        }
    }

    private fun handleReadyToUpdate() {
        readyToUpdate.complete(Unit)
    }

    // --- Internal helpers ---

    private fun setDelegateCallbacks(active: Boolean) {
        delegate.onServiceAdded = if (active) ::handleServiceAdded else null
        delegate.onReadRequest = if (active) ::handleReadRequest else null
        delegate.onWriteRequests = if (active) ::handleWriteRequests else null
        delegate.onSubscribe = if (active) ::handleSubscribe else null
        delegate.onReadyToUpdate = if (active) ::handleReadyToUpdate else null
    }

    /**
     * Track a central as connected. Returns true if the central is newly discovered.
     * Must be called on [dispatcher].
     *
     * TODO: Centrals accumulate until [close] because K/N cannot override
     *  didUnsubscribeFromCharacteristic separately from didSubscribeToCharacteristic
     *  (identical Kotlin type signatures). For long-lived servers with many transient
     *  clients, consider a periodic sweep or idle-timeout eviction.
     */
    private fun trackCentral(central: CBCentral): Boolean {
        val id = central.id
        if (connectedCentrals.containsKey(id)) return false
        connectedCentrals[id] = central
        _connections.update { list ->
            list + ServerConnection(Identifier(id))
        }
        logEvent(
            BleLogEvent.ServerClientEvent(
                Identifier(id), "connected (${connectedCentrals.size} total)",
            ),
        )
        return true
    }

    // iOS silently truncates notification/indication payloads to the negotiated
    // ATT MTU. Unlike Android, there is no API to query the per-central MTU or
    // receive a truncation warning — callers must size payloads conservatively
    // (typically ≤ 182 bytes for default MTU, or negotiate a larger MTU from
    // the central side).
    private suspend fun sendUpdate(
        characteristic: CBMutableCharacteristic,
        nsData: NSData,
        targets: List<CBCentral>?,
    ) {
        repeat(MAX_NOTIFY_RETRIES) { attempt ->
            val sent = manager.updateValue(nsData, forCharacteristic = characteristic, onSubscribedCentrals = targets)
            if (sent) return

            // Safe despite handleReadyToUpdate running on the GCD queue:
            // if complete(Unit) fires between assignment and await(), await()
            // returns immediately (CompletableDeferred is idempotent).
            readyToUpdate = CompletableDeferred()
            try {
                withTimeout(NOTIFY_TIMEOUT) {
                    readyToUpdate.await()
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                throw ServerException.NotifyFailed(
                    "Transmit queue full — timeout waiting for ready (attempt ${attempt + 1}/$MAX_NOTIFY_RETRIES)",
                )
            }
        }
        throw ServerException.NotifyFailed("Transmit queue full after $MAX_NOTIFY_RETRIES retries")
    }

    private fun buildNativeService(definition: ServiceDefinition): CBMutableService {
        val service = CBMutableService(
            type = CBUUID.UUIDWithString(definition.uuid.toString()),
            primary = true,
        )
        val nativeChars = definition.characteristics.map { charDef ->
            val properties = buildCBProperties(charDef.properties)
            val permissions = buildCBPermissions(charDef.permissions)
            val char = CBMutableCharacteristic(
                type = CBUUID.UUIDWithString(charDef.uuid.toString()),
                properties = properties,
                value = null,
                permissions = permissions,
            )
            characteristicCache[charDef.uuid] = char
            char
        }
        @Suppress("UNCHECKED_CAST")
        service.setCharacteristics(nativeChars as List<Any>?)
        return service
    }

    private fun checkOpen() {
        if (isOpen.value == 0) throw ServerException.NotOpen()
    }

    private companion object {
        const val POWER_ON_TIMEOUT_MS = 10_000L
        const val SERVICE_ADD_TIMEOUT_MS = 10_000L
        const val MAX_NOTIFY_RETRIES = 3
        val NOTIFY_TIMEOUT = 5.seconds

        val instanceLock = AtomicInt(0)
    }
}

// --- Private extensions to reduce CBATTRequest/CBCentral identity boilerplate ---

private val CBATTRequest.charUuid: Uuid get() = uuidFrom(characteristic.UUID.UUIDString)
private val CBATTRequest.centralId: Identifier get() = Identifier(central.identifier.UUIDString)
private val CBCentral.id: String get() = identifier.UUIDString

private const val CB_ATT_ERROR_UNLIKELY: Long = 0x0E

private fun buildCBProperties(props: ServerCharacteristic.Properties): ULong {
    var flags: ULong = 0u
    if (props.read) flags = flags or CBCharacteristicPropertyRead
    if (props.write) flags = flags or CBCharacteristicPropertyWrite
    if (props.writeWithoutResponse) flags = flags or CBCharacteristicPropertyWriteWithoutResponse
    if (props.notify) flags = flags or CBCharacteristicPropertyNotify
    if (props.indicate) flags = flags or CBCharacteristicPropertyIndicate
    return flags
}

private fun buildCBPermissions(perms: ServerCharacteristic.Permissions): ULong {
    var flags: ULong = 0u
    if (perms.read) flags = flags or CBAttributePermissionsReadable
    if (perms.readEncrypted) flags = flags or CBAttributePermissionsReadEncryptionRequired
    if (perms.write) flags = flags or CBAttributePermissionsWriteable
    if (perms.writeEncrypted) flags = flags or CBAttributePermissionsWriteEncryptionRequired
    return flags
}

private fun GattStatus.toCBATTError(): Long = when (this) {
    GattStatus.Success -> CBATTErrorSuccess
    GattStatus.ReadNotPermitted -> CBATTErrorReadNotPermitted
    GattStatus.WriteNotPermitted -> CBATTErrorWriteNotPermitted
    GattStatus.InvalidOffset -> CBATTErrorInvalidOffset
    GattStatus.InvalidAttributeLength -> CBATTErrorInvalidAttributeValueLength
    GattStatus.InsufficientAuthentication -> CBATTErrorInsufficientAuthentication
    GattStatus.InsufficientEncryption -> CBATTErrorInsufficientEncryption
    GattStatus.InsufficientAuthorization -> CBATTErrorInsufficientAuthorization
    GattStatus.RequestNotSupported -> CBATTErrorRequestNotSupported
    GattStatus.ConnectionCongested -> CB_ATT_ERROR_UNLIKELY
    GattStatus.Failure -> CB_ATT_ERROR_UNLIKELY
    is GattStatus.Unknown -> CB_ATT_ERROR_UNLIKELY
}
