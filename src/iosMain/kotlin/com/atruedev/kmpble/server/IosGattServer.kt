package com.atruedev.kmpble.server

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.bleDataFromNSData
import com.atruedev.kmpble.emptyBleData
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.internal.PeripheralManagerProvider
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import com.atruedev.kmpble.scanner.uuidFrom
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
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

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
 * All delegate callbacks arrive on [PeripheralManagerProvider]'s
 * serial GCD queue. Handler invocations dispatch to serialized
 * coroutine [dispatcher]. CBPeripheralManager.respondToRequest()
 * can be called from any thread.
 */
internal class IosGattServer(
    private val serviceDefinitions: List<ServiceDefinition>,
) : GattServer {

    private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher + CoroutineName("IosGattServer"))

    private val manager get() = PeripheralManagerProvider.manager
    private val delegate get() = PeripheralManagerProvider.delegate

    private val _connections = MutableStateFlow<List<ServerConnection>>(emptyList())
    override val connections: StateFlow<List<ServerConnection>> = _connections.asStateFlow()

    private val _connectionEvents = MutableSharedFlow<ServerConnectionEvent>(extraBufferCapacity = 64)
    override val connectionEvents: Flow<ServerConnectionEvent> = _connectionEvents.asSharedFlow()

    // Connected centrals: UUID string -> CBCentral reference
    private val connectedCentrals = mutableMapOf<String, CBCentral>()

    // Subscription tracking: char UUID string -> set of central UUID strings
    private val subscriptions = mutableMapOf<String, MutableSet<String>>()

    // Handlers mapped by characteristic UUID
    private val readHandlers = mutableMapOf<Uuid, suspend (Identifier) -> BleData>()
    private val writeHandlers =
        mutableMapOf<Uuid, suspend (Identifier, BleData, Boolean) -> GattStatus?>()

    // Native characteristic cache: Uuid -> CBMutableCharacteristic (stored at build time)
    private val characteristicCache = mutableMapOf<Uuid, CBMutableCharacteristic>()

    // Backpressure: completed when transmit queue has space
    private var readyToUpdate = CompletableDeferred<Unit>().apply { complete(Unit) }

    // Pending service add
    @Volatile
    private var pendingServiceAdd: CompletableDeferred<NSError?>? = null

    // Lifecycle
    @Volatile
    private var isOpen = false
    private var isClosed = false

    // --- Public API ---

    override suspend fun open() {
        if (isClosed) {
            throw ServerException.OpenFailed(
                "This server instance has been closed and cannot be reopened. " +
                    "Create a new instance via GattServer { ... } factory.",
            )
        }

        withContext(dispatcher) {
            if (isOpen) return@withContext

            logEvent(BleLogEvent.ServerLifecycle("opening"))

            // Register callbacks with delegate
            registerDelegateCallbacks()

            // Trigger lazy init — CBPeripheralManager constructor fires
            // peripheralManagerDidUpdateState on the delegate
            manager

            // Wait for powered on state
            try {
                withTimeout(POWER_ON_TIMEOUT_MS) {
                    delegate.managerState.first { it == CBPeripheralManagerStatePoweredOn }
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                clearDelegateCallbacks()
                throw ServerException.OpenFailed(
                    "Timeout waiting for Bluetooth to power on (state: ${delegate.managerState.value})",
                )
            }

            // Register handlers from service definitions
            for (serviceDef in serviceDefinitions) {
                for (charDef in serviceDef.characteristics) {
                    charDef.readHandler?.let { readHandlers[charDef.uuid] = it }
                    charDef.writeHandler?.let { writeHandlers[charDef.uuid] = it }
                }
            }

            // Add services sequentially (must wait for didAdd callback for each)
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

            isOpen = true
            logEvent(
                BleLogEvent.ServerLifecycle("open (${serviceDefinitions.size} services)"),
            )
        }
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
                    device ?: Identifier("broadcast"),
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
        if (!isOpen && !isClosed) {
            isClosed = true
            clearDelegateCallbacks()
            return
        }
        if (!isOpen) return

        isOpen = false
        isClosed = true
        logEvent(BleLogEvent.ServerLifecycle("closing"))

        // Remove all services from the peripheral manager
        manager.removeAllServices()

        // Cancel pending backpressure
        readyToUpdate.cancel(kotlinx.coroutines.CancellationException("Server closed"))

        // Cancel scope
        scope.cancel()

        // Clear state
        connectedCentrals.clear()
        subscriptions.clear()
        characteristicCache.clear()
        readHandlers.clear()
        writeHandlers.clear()
        _connections.value = emptyList()

        // Unregister from delegate
        clearDelegateCallbacks()

        logEvent(BleLogEvent.ServerLifecycle("closed"))
    }

    // --- Delegate callback handlers (called on GCD queue) ---

    private fun handleServiceAdded(error: NSError?) {
        pendingServiceAdd?.complete(error)
    }

    private fun handleReadRequest(peripheral: CBPeripheralManager, request: CBATTRequest) {
        val charUuid = uuidFrom(request.characteristic.UUID.UUIDString)
        val centralId = Identifier(request.central.identifier.UUIDString)

        trackCentral(request.central)

        val handler = readHandlers[charUuid]
        if (handler == null) {
            logEvent(
                BleLogEvent.ServerRequest(
                    centralId, "read-rejected (no handler)", charUuid, GattStatus.ReadNotPermitted,
                ),
            )
            peripheral.respondToRequest(request, withResult = CBATTErrorRequestNotSupported)
            return
        }

        scope.launch {
            try {
                val bleData = handler(centralId)

                val offset = request.offset.toInt()
                if (offset > bleData.size) {
                    peripheral.respondToRequest(request, withResult = CBATTErrorInvalidOffset)
                    return@launch
                }

                val responseNsData = if (offset > 0) {
                    bleData.slice(offset, bleData.size).nsData
                } else {
                    bleData.nsData
                }
                request.value = responseNsData
                peripheral.respondToRequest(request, withResult = CBATTErrorSuccess)

                val responseSize = responseNsData.length.toInt()
                logEvent(
                    BleLogEvent.ServerRequest(
                        centralId,
                        "read (${responseSize}B, offset=$offset)",
                        charUuid,
                        GattStatus.Success,
                    ),
                )
            } catch (_: Exception) {
                logEvent(
                    BleLogEvent.ServerRequest(
                        centralId, "read-failed (handler threw)", charUuid, GattStatus.Failure,
                    ),
                )
                peripheral.respondToRequest(request, withResult = CBATTErrorUnlikelyError)
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
            var failError: Long = CBATTErrorUnlikelyError

            for (request in requests) {
                val charUuid = uuidFrom(request.characteristic.UUID.UUIDString)
                val centralId = Identifier(request.central.identifier.UUIDString)
                val data = if (request.value != null) bleDataFromNSData(request.value!!) else emptyBleData()

                trackCentral(request.central)

                val handler = writeHandlers[charUuid]
                if (handler == null) {
                    logEvent(
                        BleLogEvent.ServerRequest(
                            centralId, "write-rejected (no handler)", charUuid,
                            GattStatus.WriteNotPermitted,
                        ),
                    )
                    failed = true
                    failError = CBATTErrorWriteNotPermitted
                    break
                }

                try {
                    val status = handler(centralId, data, true)
                    if (status != null && status != GattStatus.Success) {
                        failed = true
                        failError = status.toCBATTError()
                        break
                    }
                    logEvent(
                        BleLogEvent.ServerRequest(
                            centralId, "write (${data.size}B)", charUuid, status,
                        ),
                    )
                } catch (_: Exception) {
                    logEvent(
                        BleLogEvent.ServerRequest(
                            centralId, "write-failed (handler threw)", charUuid, GattStatus.Failure,
                        ),
                    )
                    failed = true
                    break
                }
            }

            // Respond once to the first request — iOS applies the result to the entire batch
            if (failed) {
                peripheral.respondToRequest(firstRequest, withResult = failError)
            } else {
                peripheral.respondToRequest(firstRequest, withResult = CBATTErrorSuccess)
            }
        }
    }

    private fun handleSubscribe(central: CBCentral, characteristic: CBCharacteristic) {
        val charUuid = characteristic.UUID.UUIDString
        val centralId = Identifier(central.identifier.UUIDString)
        val isNewCentral = trackCentral(central)

        subscriptions.getOrPut(charUuid) { mutableSetOf() }.add(central.identifier.UUIDString)

        logEvent(
            BleLogEvent.ServerClientEvent(centralId, "subscribed to $charUuid"),
        )

        if (isNewCentral) {
            scope.launch {
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

    private fun registerDelegateCallbacks() {
        delegate.onServiceAdded = ::handleServiceAdded
        delegate.onReadRequest = ::handleReadRequest
        delegate.onWriteRequests = ::handleWriteRequests
        delegate.onSubscribe = ::handleSubscribe
        delegate.onReadyToUpdate = ::handleReadyToUpdate
    }

    private fun clearDelegateCallbacks() {
        delegate.onServiceAdded = null
        delegate.onReadRequest = null
        delegate.onWriteRequests = null
        delegate.onSubscribe = null
        delegate.onReadyToUpdate = null
    }

    /**
     * Track a central as connected. Returns true if the central is newly discovered.
     */
    private fun trackCentral(central: CBCentral): Boolean {
        val id = central.identifier.UUIDString
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

    private suspend fun sendUpdate(
        characteristic: CBMutableCharacteristic,
        nsData: NSData,
        targets: List<CBCentral>?,
    ) {
        var sent = manager.updateValue(nsData, forCharacteristic = characteristic, onSubscribedCentrals = targets)
        if (!sent) {
            // Transmit queue full — wait for peripheralManagerIsReadyToUpdateSubscribers
            readyToUpdate = CompletableDeferred()
            try {
                withTimeout(NOTIFY_TIMEOUT) {
                    readyToUpdate.await()
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                throw ServerException.NotifyFailed("Transmit queue full — timeout waiting for ready")
            }
            // Retry once
            sent = manager.updateValue(nsData, forCharacteristic = characteristic, onSubscribedCentrals = targets)
            if (!sent) {
                throw ServerException.NotifyFailed("Transmit queue full after retry")
            }
        }
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
                value = null, // Dynamic — value served via delegate read callback
                permissions = permissions,
            )
            // Cache for updateValue calls
            characteristicCache[charDef.uuid] = char
            char
        }
        @Suppress("UNCHECKED_CAST")
        service.setCharacteristics(nativeChars as List<Any>?)
        return service
    }

    private fun checkOpen() {
        if (!isOpen) throw ServerException.NotOpen()
    }

    private companion object {
        const val POWER_ON_TIMEOUT_MS = 10_000L
        const val SERVICE_ADD_TIMEOUT_MS = 10_000L
        val NOTIFY_TIMEOUT = 5.seconds

        const val CBATTErrorUnlikelyError: Long = 0x0E
    }
}

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
    GattStatus.ConnectionCongested -> 0x0E // CBATTErrorUnlikelyError
    GattStatus.Failure -> 0x0E
    is GattStatus.Unknown -> 0x0E
}
