package com.atruedev.kmpble.macos.internal

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.backend.AttributeWrite
import com.atruedev.kmpble.backend.GattServerEvent
import com.atruedev.kmpble.backend.GattServerEventListener
import com.atruedev.kmpble.backend.GattServerTransport
import com.atruedev.kmpble.backend.ServerCharacteristicSpec
import com.atruedev.kmpble.backend.ServerServiceSpec
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.server.ServerException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * GATT server on the shared `CBPeripheralManager`. Requests for characteristics owned by
 * another server instance are ignored here. CoreBluetooth manages CCCDs itself and reports
 * no central connect/disconnect, so core infers connections from requests.
 */
@OptIn(ExperimentalUuidApi::class)
internal class MacosGattServerTransport(
    private val stack: MacosStack,
    private val settleTimeout: Duration = SETTLE_TIMEOUT,
    private val serviceAddTimeout: Duration = SERVICE_ADD_TIMEOUT,
    private val notifyReadyTimeout: Duration = NOTIFY_READY_TIMEOUT,
) : GattServerTransport {
    override val reportsConnections: Boolean = false

    @Volatile private var listener: GattServerEventListener? = null

    @Volatile private var registration: AutoCloseable? = null

    private val uuidsByHandle = ConcurrentHashMap<Long, Uuid>()
    private val handlesByUuid = ConcurrentHashMap<Uuid, Long>()
    private val services = CopyOnWriteArrayList<Long>()
    private val serviceAdded = ConcurrentHashMap<Long, CompletableDeferred<MacosEvent.PmServiceAdded>>()
    private val readyToUpdate = Channel<Unit>(Channel.CONFLATED)

    override fun setEventListener(listener: GattServerEventListener?) {
        this.listener = listener
    }

    override suspend fun open(services: List<ServerServiceSpec>) {
        val state =
            try {
                stack.awaitPeripheralManagerSettled(settleTimeout)
            } catch (e: MissingUsageDescriptionException) {
                throw ServerException.OpenFailed(e.message ?: "", e)
            } catch (e: UnsatisfiedLinkError) {
                throw ServerException.NotSupported(e.message ?: "kmp-ble-macos native library unavailable")
            }
        when (state) {
            CbManagerState.POWERED_ON -> Unit
            CbManagerState.UNSUPPORTED -> throw ServerException.NotSupported(
                "This Mac does not support the Bluetooth LE peripheral role",
            )
            else -> throw ServerException.OpenFailed("Bluetooth is not powered on (CBManagerState $state)")
        }
        registration = stack.addPeripheralManagerListener(::onEvent)
        for (service in services) add(service)
    }

    override fun respond(
        requestId: Long,
        status: GattStatus,
        value: ByteArray?,
    ) {
        stack.api.pmRespond(requestId, MacosStatus.attResult(status), value)
    }

    override suspend fun notify(
        characteristic: Uuid,
        device: Identifier?,
        data: ByteArray,
        indicate: Boolean,
    ) {
        val handle =
            handlesByUuid[characteristic]
                ?: throw ServerException.NotifyFailed("Characteristic $characteristic not found")
        repeat(MAX_NOTIFY_ATTEMPTS) { attempt ->
            when (stack.api.pmUpdateValue(handle, data, device?.value)) {
                SENT -> return
                QUEUE_FULL ->
                    withTimeoutOrNull(notifyReadyTimeout) { readyToUpdate.receive() }
                        ?: throw ServerException.NotifyFailed(
                            "Transmit queue full (attempt ${attempt + 1}/$MAX_NOTIFY_ATTEMPTS)",
                        )
                else -> throw ServerException.DeviceNotConnected(
                    "Central ${device?.value} has not interacted with this server",
                )
            }
        }
        throw ServerException.NotifyFailed("Transmit queue full after $MAX_NOTIFY_ATTEMPTS attempts")
    }

    override fun close() {
        listener = null
        registration?.close()
        registration = null
        services.forEach { stack.api.pmRemoveService(it) }
        services.clear()
        uuidsByHandle.clear()
        handlesByUuid.clear()
    }

    private suspend fun add(service: ServerServiceSpec) {
        val handles =
            stack.api.pmAddService(
                serviceUuid = service.uuid.toString(),
                characteristicUuids = service.characteristics.map { it.uuid.toString() },
                properties = service.characteristics.map { it.cbProperties() }.toIntArray(),
                permissions = service.characteristics.map { it.cbPermissions() }.toIntArray(),
            ) ?: throw ServerException.OpenFailed("CoreBluetooth rejected service ${service.uuid}")
        val serviceHandle = handles.first()
        services += serviceHandle
        service.characteristics.zip(handles.drop(1)).forEach { (characteristic, handle) ->
            uuidsByHandle[handle] = characteristic.uuid
            handlesByUuid[characteristic.uuid] = handle
        }
        val added =
            withTimeoutOrNull(serviceAddTimeout) { serviceAddedFor(serviceHandle).await() }
                ?: throw ServerException.OpenFailed("Timed out adding service ${service.uuid}")
        if (added.status !=
            0
        ) {
            throw ServerException.OpenFailed(added.message ?: "addService failed with status ${added.status}")
        }
    }

    private fun serviceAddedFor(service: Long): CompletableDeferred<MacosEvent.PmServiceAdded> =
        serviceAdded.computeIfAbsent(service) { CompletableDeferred() }

    private fun onEvent(event: MacosEvent) {
        when (event) {
            is MacosEvent.PmServiceAdded -> serviceAddedFor(event.service).complete(event)
            is MacosEvent.PmReadyToUpdate -> readyToUpdate.trySend(Unit)
            is MacosEvent.PmReadRequest -> {
                val uuid = uuidsByHandle[event.characteristic] ?: return
                val events = listener ?: return stack.api.pmRespond(event.request, UNLIKELY_ERROR, null)
                events.onEvent(
                    GattServerEvent.ReadRequest(event.request, Identifier(event.central), uuid, event.offset),
                )
            }
            is MacosEvent.PmWriteRequests -> {
                val writes =
                    event.writes.map { write ->
                        val uuid = uuidsByHandle[write.characteristic] ?: return
                        AttributeWrite(uuid, write.offset, write.value)
                    }
                val events = listener ?: return stack.api.pmRespond(event.request, UNLIKELY_ERROR, null)
                events.onEvent(
                    GattServerEvent.WriteRequest(
                        event.request,
                        Identifier(event.central),
                        writes,
                        responseNeeded = true,
                    ),
                )
            }
            is MacosEvent.PmSubscribed -> {
                val uuid = uuidsByHandle[event.characteristic] ?: return
                listener?.onEvent(GattServerEvent.Subscribed(Identifier(event.central), uuid))
            }
            is MacosEvent.PmUnsubscribed -> {
                val uuid = uuidsByHandle[event.characteristic] ?: return
                listener?.onEvent(GattServerEvent.Unsubscribed(Identifier(event.central), uuid))
            }
            else -> Unit
        }
    }

    private companion object {
        const val SENT = 1
        const val QUEUE_FULL = 0
        const val MAX_NOTIFY_ATTEMPTS = 3
        const val UNLIKELY_ERROR = 0x0E
        val SETTLE_TIMEOUT = 10.seconds
        val SERVICE_ADD_TIMEOUT = 10.seconds
        val NOTIFY_READY_TIMEOUT = 5.seconds
    }
}

internal fun ServerCharacteristicSpec.cbProperties(): Int {
    var flags = 0
    if (properties.read) flags = flags or 0x02
    if (properties.writeWithoutResponse) flags = flags or 0x04
    if (properties.write) flags = flags or 0x08
    if (properties.notify) flags = flags or 0x10
    if (properties.indicate) flags = flags or 0x20
    return flags
}

internal fun ServerCharacteristicSpec.cbPermissions(): Int {
    var flags = 0
    if (permissions.read) flags = flags or 0x01
    if (permissions.write) flags = flags or 0x02
    if (permissions.readEncrypted) flags = flags or 0x04
    if (permissions.writeEncrypted) flags = flags or 0x08
    return flags
}
