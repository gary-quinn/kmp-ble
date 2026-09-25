package com.atruedev.kmpble.bluez

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.backend.AttributeWrite
import com.atruedev.kmpble.backend.GattServerEvent
import com.atruedev.kmpble.backend.GattServerEventListener
import com.atruedev.kmpble.backend.GattServerTransport
import com.atruedev.kmpble.backend.ServerServiceSpec
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.server.ServerException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bluez.GattManager1
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.exceptions.DBusExecutionException
import org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * BlueZ GATT server through `GattManager1.RegisterApplication`.
 *
 * Limitations of the BlueZ D-Bus API: `StartNotify` does not name the subscribing central,
 * so notifications go to every subscriber and [GattServerEvent.Subscribed] is not reported;
 * long (prepared) writes, which BlueZ replays as separate offset writes, are rejected with
 * `InvalidOffset`.
 */
@OptIn(ExperimentalUuidApi::class)
internal class BlueZGattServerTransport(
    private val openBus: () -> DBusConnection = BlueZ::openSystemBus,
    private val requestTimeoutMs: Long = REQUEST_TIMEOUT_MS,
) : GattServerTransport,
    BlueZGattRequestHandler {
    override val reportsConnections: Boolean = false

    @Volatile private var listener: GattServerEventListener? = null

    @Volatile private var bus: DBusConnection? = null

    @Volatile private var application: BlueZGattApplication? = null

    @Volatile private var adapterPath: String? = null

    @Volatile private var deviceWatch: AutoCloseable? = null

    private val pending = ConcurrentHashMap<Long, CompletableFuture<Pair<GattStatus, ByteArray?>>>()
    private val nextRequest = AtomicLong(1)
    private val rootPath = "/com/atruedev/kmpble/gatt/app${instances.incrementAndGet()}"

    override fun setEventListener(listener: GattServerEventListener?) {
        this.listener = listener
    }

    override suspend fun open(services: List<ServerServiceSpec>) {
        withContext(Dispatchers.IO) {
            if (!BlueZ.isLinux()) throw ServerException.NotSupported("BlueZ GATT server requires Linux")
            val connection =
                try {
                    openBus()
                } catch (e: Exception) {
                    throw ServerException.OpenFailed("Could not connect to system D-Bus: ${e.message}", e)
                }
            try {
                val adapter =
                    BlueZ.findAdapterPath(connection) ?: throw ServerException.OpenFailed("No BlueZ adapter found")
                val app = BlueZGattApplication(rootPath, services, this@BlueZGattServerTransport)
                app.export(connection)
                bus = connection
                application = app
                adapterPath = adapter
                deviceWatch = watchDisconnects(connection)
                connection
                    .getRemoteObject(BlueZ.SERVICE, adapter, GattManager1::class.java)
                    .RegisterApplication(DBusPath(rootPath), emptyMap())
            } catch (e: ServerException) {
                close()
                throw e
            } catch (e: Exception) {
                close()
                throw ServerException.OpenFailed("BlueZ RegisterApplication failed: ${e.message}", e)
            }
        }
    }

    override fun respond(
        requestId: Long,
        status: GattStatus,
        value: ByteArray?,
    ) {
        pending.remove(requestId)?.complete(status to value)
    }

    override suspend fun notify(
        characteristic: Uuid,
        device: Identifier?,
        data: ByteArray,
        indicate: Boolean,
    ) {
        val connection = bus ?: throw ServerException.NotOpen()
        val target =
            application?.characteristic(characteristic)
                ?: throw ServerException.NotifyFailed("Characteristic $characteristic not found")
        if (!target.notifying) return
        withContext(Dispatchers.IO) {
            connection.sendMessage(
                Properties.PropertiesChanged(
                    target.path,
                    BlueZ.GATT_CHARACTERISTIC_INTERFACE,
                    mapOf("Value" to Variant(data)),
                    emptyList(),
                ),
            )
        }
    }

    override fun close() {
        listener = null
        val connection = bus
        bus = null
        runCatching { deviceWatch?.close() }
        deviceWatch = null
        val app = application
        application = null
        val adapter = adapterPath
        if (connection != null) {
            if (adapter != null) {
                runCatching {
                    connection
                        .getRemoteObject(
                            BlueZ.SERVICE,
                            adapter,
                            GattManager1::class.java,
                        ).UnregisterApplication(DBusPath(rootPath))
                }
            }
            app?.unexport(connection)
            runCatching { connection.close() }
        }
        pending.values.forEach { it.complete(GattStatus.Failure to null) }
        pending.clear()
    }

    override fun onRead(
        characteristic: Uuid,
        options: Map<String, Variant<*>>,
    ): ByteArray {
        val events = listener ?: throw bluezError(GattStatus.RequestNotSupported)
        val (id, future) = newRequest()
        events.onEvent(GattServerEvent.ReadRequest(id, deviceOf(options), characteristic, offsetOf(options)))
        val (status, value) = await(id, future)
        if (status != GattStatus.Success) throw bluezError(status)
        return value ?: byteArrayOf()
    }

    override fun onWrite(
        characteristic: Uuid,
        value: ByteArray,
        options: Map<String, Variant<*>>,
    ) {
        if (options.containsKey("prepare-authorize")) return
        if (offsetOf(options) > 0) throw bluezError(GattStatus.InvalidOffset)
        val events = listener ?: throw bluezError(GattStatus.WriteNotPermitted)
        val responseNeeded = (options["type"]?.value as? String) != "command"
        val (id, future) = newRequest()
        events.onEvent(
            GattServerEvent.WriteRequest(
                requestId = id,
                device = deviceOf(options),
                writes = listOf(AttributeWrite(characteristic, 0, value)),
                responseNeeded = responseNeeded,
            ),
        )
        if (!responseNeeded) {
            pending.remove(id)
            return
        }
        val (status, _) = await(id, future)
        if (status != GattStatus.Success) throw bluezError(status)
    }

    override fun onNotifying(
        characteristic: Uuid,
        notifying: Boolean,
    ) {
        val connection = bus ?: return
        val target = application?.characteristic(characteristic) ?: return
        runCatching {
            connection.sendMessage(
                Properties.PropertiesChanged(
                    target.path,
                    BlueZ.GATT_CHARACTERISTIC_INTERFACE,
                    mapOf("Notifying" to Variant(notifying)),
                    emptyList(),
                ),
            )
        }
    }

    private fun newRequest(): Pair<Long, CompletableFuture<Pair<GattStatus, ByteArray?>>> {
        val id = nextRequest.getAndIncrement()
        val future = CompletableFuture<Pair<GattStatus, ByteArray?>>()
        pending[id] = future
        return id to future
    }

    private fun await(
        id: Long,
        future: CompletableFuture<Pair<GattStatus, ByteArray?>>,
    ): Pair<GattStatus, ByteArray?> =
        try {
            future.get(requestTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            pending.remove(id)
            GattStatus.Failure to null
        }

    private fun watchDisconnects(connection: DBusConnection): AutoCloseable? =
        runCatching {
            connection.addSigHandler(
                Properties.PropertiesChanged::class.java,
                object : AbstractPropertiesChangedHandler() {
                    override fun handle(signal: Properties.PropertiesChanged) {
                        if (signal.interfaceName != BlueZ.DEVICE_INTERFACE) return
                        if (signal.propertiesChanged?.get("Connected")?.value != false) return
                        val address = BlueZPairingAgent.addressFromPath(signal.path ?: return) ?: return
                        listener?.onEvent(GattServerEvent.Disconnected(Identifier(address)))
                    }
                },
            )
        }.getOrNull()

    private fun deviceOf(options: Map<String, Variant<*>>): Identifier {
        val path = (options["device"]?.value as? DBusPath)?.path ?: options["device"]?.value?.toString()
        return Identifier(path?.let(BlueZPairingAgent::addressFromPath) ?: UNKNOWN_DEVICE)
    }

    private fun offsetOf(options: Map<String, Variant<*>>): Int = (options["offset"]?.value as? Number)?.toInt() ?: 0

    private companion object {
        const val REQUEST_TIMEOUT_MS = 20_000L
        const val UNKNOWN_DEVICE = "00:00:00:00:00:00"
        val instances = AtomicInteger(0)
    }
}

internal fun bluezError(status: GattStatus): DBusExecutionException {
    val message = "GATT request rejected: $status"
    return when (status) {
        GattStatus.ReadNotPermitted, GattStatus.WriteNotPermitted -> org.bluez.Error.NotPermitted(message)
        GattStatus.InsufficientAuthentication,
        GattStatus.InsufficientAuthorization,
        GattStatus.InsufficientEncryption,
        -> org.bluez.Error.NotAuthorized(message)
        GattStatus.InvalidOffset -> org.bluez.Error.InvalidOffset(message)
        GattStatus.InvalidAttributeLength -> org.bluez.Error.InvalidValueLength(message)
        GattStatus.RequestNotSupported -> org.bluez.Error.NotSupported(message)
        else -> org.bluez.Error.Failed(message)
    }
}
