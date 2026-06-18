package com.atruedev.kmpble.server

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.internal.IosPeripheralManagerDelegate
import com.atruedev.kmpble.internal.PeripheralManagerProvider
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
import platform.CoreBluetooth.CBATTRequest
import platform.CoreBluetooth.CBCentral
import platform.CoreBluetooth.CBCharacteristicPropertyIndicate
import platform.CoreBluetooth.CBMutableCharacteristic
import platform.CoreBluetooth.CBPeripheralManager
import platform.CoreBluetooth.CBPeripheralManagerStatePoweredOn
import platform.Foundation.NSData
import platform.Foundation.NSError
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.Volatile
import kotlin.time.Duration
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
 * - Idle centrals are evicted by a periodic sweep (see [centralIdleTimeout]).
 *   Each read, write, or subscribe refreshes the central's last-activity
 *   timestamp via [IdleTracker].
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
    private val centralIdleTimeout: Duration = DEFAULT_CENTRAL_IDLE_TIMEOUT,
    private val centralSweepInterval: Duration = DEFAULT_CENTRAL_SWEEP_INTERVAL,
) : GattServer {
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
    internal val scope = CoroutineScope(SupervisorJob() + dispatcher + CoroutineName("IosGattServer"))

    private val _connections = MutableStateFlow<List<ServerConnection>>(emptyList())
    override val connections: StateFlow<List<ServerConnection>> = _connections.asStateFlow()

    internal val _connectionEvents = MutableSharedFlow<ServerConnectionEvent>(extraBufferCapacity = 64)
    override val connectionEvents: Flow<ServerConnectionEvent> = _connectionEvents.asSharedFlow()

    // --- All mutable collections below accessed ONLY on [dispatcher] ---

    internal val connectedCentrals = IdleTracker<CBCentral>(centralIdleTimeout)
    internal val subscriptions = mutableMapOf<String, MutableSet<String>>()
    internal val readHandlers = mutableMapOf<Uuid, suspend (Identifier) -> BleData>()
    internal val writeHandlers =
        mutableMapOf<Uuid, suspend (Identifier, BleData, Boolean) -> GattStatus?>()
    internal val characteristicCache = mutableMapOf<Uuid, CBMutableCharacteristic>()

    @Volatile
    internal var readyToUpdate = CompletableDeferred<Unit>().apply { complete(Unit) }

    @Volatile
    internal var pendingServiceAdd: CompletableDeferred<NSError?>? = null

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
                        "CBPeripheralManager - call close() on the existing server first.",
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

        // Force lazy CBPeripheralManager init - the constructor fires
        // peripheralManagerDidUpdateState on our delegate.
        manager

        try {
            withTimeout(POWER_ON_TIMEOUT_MS) {
                delegate.managerState.first { it == CBPeripheralManagerStatePoweredOn }
            }
        } catch (_: TimeoutCancellationException) {
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
            val nativeService = buildNativeService(serviceDef, characteristicCache)
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
            } catch (e: TimeoutCancellationException) {
                pendingServiceAdd = null
                throw ServerException.OpenFailed(
                    "Timeout adding service ${serviceDef.uuid}",
                )
            }

            logEvent(BleLogEvent.ServerLifecycle("service added: ${serviceDef.uuid}"))
        }

        isOpen.value = 1

        scope.launch { runSweepLoop() }

        logEvent(
            BleLogEvent.ServerLifecycle("open (${serviceDefinitions.size} services)"),
        )
    }

    override suspend fun notify(
        characteristicUuid: Uuid,
        device: Identifier?,
        data: BleData,
    ) {
        withContext(dispatcher) {
            checkOpen()
            val nativeChar =
                characteristicCache[characteristicUuid]
                    ?: throw ServerException.NotifyFailed(
                        "Characteristic $characteristicUuid not found",
                    )

            val targets: List<CBCentral>? =
                if (device != null) {
                    val central =
                        connectedCentrals[device.value]
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
     * determines the behavior - iOS handles confirmation transparently.
     * This method delegates to [notify] with the same underlying
     * `updateValue` call.
     */
    override suspend fun indicate(
        characteristicUuid: Uuid,
        device: Identifier,
        data: BleData,
    ) {
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

        // Stop accepting new callbacks before cancelling scope - prevents
        // a GCD-queued callback from scope.launch-ing into a cancelled scope.
        setDelegateCallbacks(active = false)

        manager.removeAllServices()

        readyToUpdate.cancel(CancellationException("Server closed"))

        // Don't clear collections here - races with in-flight coroutines before cancellation.
        scope.cancel()
        _connections.value = emptyList()

        instanceLock.value = 0
        logEvent(BleLogEvent.ServerLifecycle("closed"))
    }

    // --- Internal helpers ---

    internal fun setDelegateCallbacks(active: Boolean) {
        delegate.onServiceAdded = if (active) this::handleServiceAdded else null
        delegate.onReadRequest = if (active) this::handleReadRequest else null
        delegate.onWriteRequests = if (active) this::handleWriteRequests else null
        delegate.onSubscribe = if (active) this::handleSubscribe else null
        delegate.onReadyToUpdate = if (active) this::handleReadyToUpdate else null
    }

    /** Track or refresh a central. Returns `true` if newly discovered. */
    internal fun trackCentral(central: CBCentral): Boolean {
        val id = central.id
        val isNew = connectedCentrals.trackOrRefresh(id, central)
        if (isNew) {
            _connections.update { it + ServerConnection(Identifier(id)) }
            logEvent(
                BleLogEvent.ServerClientEvent(
                    Identifier(id),
                    "connected (${connectedCentrals.size} total)",
                ),
            )
        }
        return isNew
    }

    private suspend fun runSweepLoop() {
        while (true) {
            delay(centralSweepInterval)
            try {
                evictIdleCentrals()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logEvent(BleLogEvent.Error(null, "central sweep failed", e))
            }
        }
    }

    private suspend fun evictIdleCentrals() {
        val evicted = connectedCentrals.evictIdle()
        for ((id, _) in evicted) {
            subscriptions.values.forEach { it.remove(id) }
            val identifier = Identifier(id)
            _connections.update { list -> list.filter { it.device != identifier } }
            _connectionEvents.emit(ServerConnectionEvent.Disconnected(identifier))
            logEvent(
                BleLogEvent.ServerClientEvent(identifier, "evicted (idle > $centralIdleTimeout)"),
            )
        }
    }

    // iOS silently truncates notification/indication payloads to the negotiated
    // ATT MTU. Unlike Android, there is no API to query the per-central MTU or
    // receive a truncation warning - callers must size payloads conservatively
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
            } catch (_: TimeoutCancellationException) {
                throw ServerException.NotifyFailed(
                    "Transmit queue full - timeout waiting for ready (attempt ${attempt + 1}/$MAX_NOTIFY_RETRIES)",
                )
            }
        }
        throw ServerException.NotifyFailed("Transmit queue full after $MAX_NOTIFY_RETRIES retries")
    }

    private fun checkOpen() {
        if (isOpen.value == 0) throw ServerException.NotOpen()
    }

    internal companion object {
        val instanceLock = AtomicInt(0)
    }
}
