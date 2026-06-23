package com.atruedev.kmpble.server

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.internal.IosPeripheralManagerDelegate
import com.atruedev.kmpble.internal.PeripheralManagerProvider
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import platform.CoreBluetooth.CBATTRequest
import platform.CoreBluetooth.CBCentral
import platform.CoreBluetooth.CBCharacteristicPropertyIndicate
import platform.CoreBluetooth.CBMutableCharacteristic
import platform.CoreBluetooth.CBPeripheralManager
import platform.Foundation.NSData
import platform.Foundation.NSError
import kotlin.concurrent.AtomicInt
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
    internal val serviceDefinitions: List<ServiceDefinition>,
    internal val manager: CBPeripheralManager = PeripheralManagerProvider.manager,
    internal val delegate: IosPeripheralManagerDelegate = PeripheralManagerProvider.delegate,
    internal val centralIdleTimeout: Duration = DEFAULT_CENTRAL_IDLE_TIMEOUT,
    internal val centralSweepInterval: Duration = DEFAULT_CENTRAL_SWEEP_INTERVAL,
) : GattServer {
    internal val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
    internal val scope = CoroutineScope(SupervisorJob() + dispatcher + CoroutineName("IosGattServer"))

    internal val _connections = MutableStateFlow<List<ServerConnection>>(emptyList())
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

    private val _readyToUpdate = atomic(CompletableDeferred<Unit>().apply { complete(Unit) })
    internal var readyToUpdate: CompletableDeferred<Unit>
        get() = _readyToUpdate.value
        set(value) {
            _readyToUpdate.value = value
        }

    private val _pendingServiceAdd = atomic<CompletableDeferred<NSError?>?>(null)
    internal var pendingServiceAdd: CompletableDeferred<NSError?>?
        get() = _pendingServiceAdd.value
        set(value) {
            _pendingServiceAdd.value = value
        }

    internal val isOpen = AtomicInt(0)
    internal val isClosed = AtomicInt(0)

    // --- Public API ---

    override suspend fun open() {
        openLifecycle()
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
        closeLifecycle()
    }

    // --- Internal helpers ---

    // iOS silently truncates notification/indication payloads to the negotiated
    // ATT MTU. Unlike Android, there is no API to query the per-central MTU or
    // receive a truncation warning - callers must size payloads conservatively
    // (typically <= 182 bytes for default MTU, or negotiate a larger MTU from
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
