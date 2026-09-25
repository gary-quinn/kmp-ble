package com.atruedev.kmpble.backend.internal

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.backend.GattServerEvent
import com.atruedev.kmpble.backend.GattServerTransport
import com.atruedev.kmpble.backend.KmpBleBackendApi
import com.atruedev.kmpble.backend.ServerCharacteristicSpec
import com.atruedev.kmpble.backend.ServerServiceSpec
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import com.atruedev.kmpble.server.AssemblyResult
import com.atruedev.kmpble.server.BROADCAST_IDENTIFIER
import com.atruedev.kmpble.server.GattServer
import com.atruedev.kmpble.server.IdleTracker
import com.atruedev.kmpble.server.ServerConnection
import com.atruedev.kmpble.server.ServerConnectionEvent
import com.atruedev.kmpble.server.ServerException
import com.atruedev.kmpble.server.ServiceDefinition
import com.atruedev.kmpble.server.WriteFragment
import com.atruedev.kmpble.server.assembleWriteFragments
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * [GattServer] shared by every JVM backend. Handler dispatch, connection tracking, and
 * subscription bookkeeping run on a serial dispatcher; the transport only relays requests.
 */
@OptIn(KmpBleBackendApi::class, ExperimentalUuidApi::class)
internal class BackendGattServer(
    private val definitions: List<ServiceDefinition>,
    private val transport: GattServerTransport,
    private val centralIdleTimeout: Duration = DEFAULT_CENTRAL_IDLE_TIMEOUT,
    private val centralSweepInterval: Duration = DEFAULT_CENTRAL_SWEEP_INTERVAL,
) : GattServer {
    private val dispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher + CoroutineName("BackendGattServer"))

    private val _connections = MutableStateFlow<List<ServerConnection>>(emptyList())
    override val connections: StateFlow<List<ServerConnection>> = _connections.asStateFlow()

    private val _connectionEvents = MutableSharedFlow<ServerConnectionEvent>(extraBufferCapacity = EVENT_BUFFER)
    override val connectionEvents: Flow<ServerConnectionEvent> = _connectionEvents.asSharedFlow()

    private val events = Channel<GattServerEvent>(Channel.UNLIMITED)
    private val readHandlers =
        definitions
            .flatMap { it.characteristics }
            .mapNotNull { c ->
                c.readHandler?.let {
                    c.uuid to
                        it
                }
            }.toMap()
    private val writeHandlers =
        definitions
            .flatMap { it.characteristics }
            .mapNotNull { c ->
                c.writeHandler?.let {
                    c.uuid to
                        it
                }
            }.toMap()
    private val knownCharacteristics = definitions.flatMap { it.characteristics }.map { it.uuid }.toSet()

    private val centrals = IdleTracker<String?>(centralIdleTimeout)
    private val subscriptions = mutableMapOf<Uuid, MutableSet<Identifier>>()

    private val opened = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    override suspend fun open() {
        if (closed.get()) throw ServerException.OpenFailed("GATT server has been closed")
        if (!opened.compareAndSet(false, true)) throw ServerException.OpenFailed("GATT server is already open")
        transport.setEventListener { event -> events.trySend(event) }
        scope.launch { for (event in events) handleEvent(event) }
        try {
            transport.open(definitions.map { it.toSpec() })
        } catch (e: CancellationException) {
            close()
            throw e
        } catch (e: ServerException) {
            close()
            throw e
        } catch (e: Exception) {
            close()
            throw ServerException.OpenFailed(e.message ?: "transport open failed", e)
        }
        if (!transport.reportsConnections) scope.launch { sweepIdleCentrals() }
        logEvent(BleLogEvent.ServerLifecycle("opened (${definitions.size} services)"))
    }

    override suspend fun notify(
        characteristicUuid: Uuid,
        device: Identifier?,
        data: BleData,
    ) {
        send(characteristicUuid, device, data, indicate = false)
    }

    override suspend fun indicate(
        characteristicUuid: Uuid,
        device: Identifier,
        data: BleData,
    ) {
        send(characteristicUuid, device, data, indicate = true)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        transport.setEventListener(null)
        runCatching { transport.close() }
        events.close()
        scope.cancel()
        _connections.value = emptyList()
        logEvent(BleLogEvent.ServerLifecycle("closed"))
    }

    private suspend fun send(
        characteristicUuid: Uuid,
        device: Identifier?,
        data: BleData,
        indicate: Boolean,
    ) {
        withContext(dispatcher) {
            if (closed.get() || !opened.get()) throw ServerException.NotOpen()
            if (characteristicUuid !in knownCharacteristics) {
                throw ServerException.NotifyFailed("Characteristic $characteristicUuid not found")
            }
            if (device != null && device.value !in centrals) {
                throw ServerException.DeviceNotConnected("Device $device not connected")
            }
        }
        try {
            transport.notify(characteristicUuid, device, data.toByteArray(), indicate)
        } catch (e: ServerException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ServerException.NotifyFailed(e.message ?: "transport notify failed", e)
        }
        val operation = if (indicate) "indicate" else "notify"
        logEvent(
            BleLogEvent.ServerRequest(
                device ?: BROADCAST_IDENTIFIER,
                "$operation (${data.size}B)",
                characteristicUuid,
                GattStatus.Success,
            ),
        )
    }

    private suspend fun handleEvent(event: GattServerEvent) {
        when (event) {
            is GattServerEvent.Connected -> track(event.device, event.name)
            is GattServerEvent.Disconnected -> untrack(event.device)
            is GattServerEvent.ReadRequest -> handleRead(event)
            is GattServerEvent.WriteRequest -> handleWrite(event)
            is GattServerEvent.Subscribed -> {
                track(event.device, name = null)
                subscriptions.getOrPut(event.characteristic) { mutableSetOf() }.add(event.device)
                logEvent(BleLogEvent.ServerClientEvent(event.device, "subscribed to ${event.characteristic}"))
            }
            is GattServerEvent.Unsubscribed -> {
                subscriptions[event.characteristic]?.remove(event.device)
                logEvent(BleLogEvent.ServerClientEvent(event.device, "unsubscribed from ${event.characteristic}"))
            }
        }
    }

    private suspend fun handleRead(request: GattServerEvent.ReadRequest) {
        track(request.device, name = null)
        val handler = readHandlers[request.characteristic]
        if (handler == null) {
            logEvent(
                BleLogEvent.ServerRequest(
                    request.device,
                    "read-rejected (no handler)",
                    request.characteristic,
                    GattStatus.ReadNotPermitted,
                ),
            )
            transport.respond(request.requestId, GattStatus.RequestNotSupported, null)
            return
        }
        try {
            val value = handler(request.device).toByteArray()
            if (request.offset > value.size) {
                transport.respond(request.requestId, GattStatus.InvalidOffset, null)
                return
            }
            val response = value.copyOfRange(request.offset, value.size)
            transport.respond(request.requestId, GattStatus.Success, response)
            logEvent(
                BleLogEvent.ServerRequest(
                    request.device,
                    "read (${response.size}B, offset=${request.offset})",
                    request.characteristic,
                    GattStatus.Success,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logEvent(
                BleLogEvent.ServerRequest(
                    request.device,
                    "read-failed (handler threw)",
                    request.characteristic,
                    GattStatus.Failure,
                ),
            )
            transport.respond(request.requestId, GattStatus.Failure, null)
        }
    }

    private suspend fun handleWrite(request: GattServerEvent.WriteRequest) {
        track(request.device, name = null)
        val writes = assemble(request) ?: return
        var status: GattStatus = GattStatus.Success
        for ((characteristic, data) in writes) {
            status = dispatchWrite(request.device, characteristic, data, request.responseNeeded)
            if (status != GattStatus.Success) break
        }
        if (request.responseNeeded) transport.respond(request.requestId, status, null)
    }

    private fun assemble(request: GattServerEvent.WriteRequest): List<Pair<Uuid, ByteArray>>? {
        if (request.writes.none { it.offset > 0 }) return request.writes.map { it.characteristic to it.value }
        val fragments = request.writes.map { WriteFragment(it.characteristic, it.offset, it.value) }
        return when (val result = assembleWriteFragments(fragments)) {
            is AssemblyResult.Success -> result.writes.map { it.charUuid to it.data }
            is AssemblyResult.PayloadTooLarge -> {
                logEvent(
                    BleLogEvent.ServerRequest(
                        request.device,
                        "write-rejected (${result.actualSize}B exceeds limit)",
                        result.charUuid,
                        GattStatus.InvalidAttributeLength,
                    ),
                )
                if (request.responseNeeded) {
                    transport.respond(
                        request.requestId,
                        GattStatus.InvalidAttributeLength,
                        null,
                    )
                }
                null
            }
        }
    }

    private suspend fun dispatchWrite(
        device: Identifier,
        characteristic: Uuid,
        data: ByteArray,
        responseNeeded: Boolean,
    ): GattStatus {
        val handler = writeHandlers[characteristic]
        if (handler == null) {
            logEvent(
                BleLogEvent.ServerRequest(
                    device,
                    "write-rejected (no handler)",
                    characteristic,
                    GattStatus.WriteNotPermitted,
                ),
            )
            return GattStatus.WriteNotPermitted
        }
        return try {
            val status = handler(device, BleData(data), responseNeeded) ?: GattStatus.Success
            logEvent(BleLogEvent.ServerRequest(device, "write (${data.size}B)", characteristic, status))
            status
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logEvent(
                BleLogEvent.ServerRequest(device, "write-failed (handler threw)", characteristic, GattStatus.Failure),
            )
            GattStatus.Failure
        }
    }

    private fun track(
        device: Identifier,
        name: String?,
    ) {
        val isNew = centrals.trackOrRefresh(device.value, name)
        if (!isNew) return
        _connections.value = _connections.value + ServerConnection(device, name)
        if (!_connectionEvents.tryEmit(ServerConnectionEvent.Connected(device))) {
            logEvent(BleLogEvent.Error(device, "Connection event buffer full, event dropped", null))
        }
        logEvent(BleLogEvent.ServerClientEvent(device, "connected"))
    }

    private fun untrack(device: Identifier) {
        if (device.value !in centrals) return
        centrals.remove(device.value)
        dropCentral(device)
    }

    private fun dropCentral(device: Identifier) {
        subscriptions.values.forEach { it.remove(device) }
        _connections.value = _connections.value.filterNot { it.device == device }
        if (!_connectionEvents.tryEmit(ServerConnectionEvent.Disconnected(device))) {
            logEvent(BleLogEvent.Error(device, "Connection event buffer full, event dropped", null))
        }
        logEvent(BleLogEvent.ServerClientEvent(device, "disconnected"))
    }

    private suspend fun sweepIdleCentrals() {
        while (scope.isActive) {
            delay(centralSweepInterval)
            val subscribed =
                subscriptions.values
                    .flatten()
                    .map { it.value }
                    .toSet()
            for ((key, _) in centrals.evictIdle()) {
                if (key in subscribed) {
                    centrals.trackOrRefresh(key, null)
                    continue
                }
                dropCentral(Identifier(key))
            }
        }
    }

    private fun ServiceDefinition.toSpec(): ServerServiceSpec =
        ServerServiceSpec(
            uuid = uuid,
            characteristics =
                characteristics.map { c ->
                    ServerCharacteristicSpec(
                        uuid = c.uuid,
                        properties = c.properties,
                        permissions = c.permissions,
                        descriptors = c.descriptors.map { it.uuid },
                    )
                },
        )

    private companion object {
        const val EVENT_BUFFER = 64
        val DEFAULT_CENTRAL_IDLE_TIMEOUT = 5.minutes
        val DEFAULT_CENTRAL_SWEEP_INTERVAL = 1.minutes
    }
}
