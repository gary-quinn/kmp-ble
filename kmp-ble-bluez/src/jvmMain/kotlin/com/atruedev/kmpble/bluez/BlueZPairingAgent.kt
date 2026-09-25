package com.atruedev.kmpble.bluez

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.backend.backendLog
import com.atruedev.kmpble.bonding.PairingEvent
import com.atruedev.kmpble.bonding.PairingHandler
import com.atruedev.kmpble.bonding.PairingResponse
import com.atruedev.kmpble.logging.BleLogEvent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.bluez.Agent1
import org.bluez.AgentManager1
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.exceptions.DBusExecutionException
import org.freedesktop.dbus.types.UInt16
import org.freedesktop.dbus.types.UInt32
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds

/**
 * Process-wide BlueZ `Agent1` that forwards pairing prompts to the [PairingHandler] of the
 * peripheral being paired. Registered while at least one handler is set, unregistered when
 * the last one is removed. Requests for devices without a handler are rejected.
 */
internal object BlueZPairingAgent {
    private const val AGENT_PATH = "/com/atruedev/kmpble/agent"
    private const val CAPABILITY = "KeyboardDisplay"
    private val RESPONSE_TIMEOUT = 30.seconds

    private val handlers = ConcurrentHashMap<String, PairingHandler>()
    private val worker =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "kmpble-bluez-agent").apply { isDaemon = true }
        }
    private val lock = Any()
    private var connection: DBusConnection? = null

    /** Records [handler] for [address] without touching D-Bus; see [ensureRegistered]. */
    fun setHandler(
        address: String,
        handler: PairingHandler?,
    ) {
        val key = address.uppercase()
        if (handler != null) {
            handlers[key] = handler
            return
        }
        if (handlers.remove(key) != null && handlers.isEmpty()) {
            worker.execute { synchronized(lock) { if (handlers.isEmpty()) unregisterLocked() } }
        }
    }

    /** Registers the agent with BlueZ when a handler is set. Blocking; call off the main thread. */
    fun ensureRegistered() {
        synchronized(lock) {
            if (connection != null || handlers.isEmpty() || !BlueZ.isLinux()) return
            try {
                val bus = BlueZ.openSystemBus()
                bus.exportObject(AGENT_PATH, Agent)
                val manager = bus.getRemoteObject(BlueZ.SERVICE, "/org/bluez", AgentManager1::class.java)
                manager.RegisterAgent(DBusPath(AGENT_PATH), CAPABILITY)
                runCatching { manager.RequestDefaultAgent(DBusPath(AGENT_PATH)) }
                connection = bus
            } catch (e: Exception) {
                warn("pairing agent registration failed: ${e.message}")
            }
        }
    }

    private fun unregisterLocked() {
        val bus = connection ?: return
        connection = null
        runCatching {
            bus
                .getRemoteObject(
                    BlueZ.SERVICE,
                    "/org/bluez",
                    AgentManager1::class.java,
                ).UnregisterAgent(DBusPath(AGENT_PATH))
        }
        runCatching { bus.unExportObject(AGENT_PATH) }
        runCatching { bus.close() }
    }

    private fun respond(
        device: DBusPath,
        event: (Identifier) -> PairingEvent,
    ): PairingResponse {
        val address = addressFromPath(device.path) ?: throw rejected("unknown device ${device.path}")
        val handler = handlers[address] ?: throw rejected("no PairingHandler for $address")
        return try {
            runBlocking { withTimeout(RESPONSE_TIMEOUT) { handler.onPairingEvent(event(Identifier(address))) } }
        } catch (e: Exception) {
            warn("PairingHandler for $address failed: ${e.message}")
            throw rejected("PairingHandler failed")
        }
    }

    private fun confirm(
        device: DBusPath,
        event: (Identifier) -> PairingEvent,
    ) {
        val response = respond(device, event)
        if (response !is PairingResponse.Confirm ||
            !response.accepted
        ) {
            throw rejected("pairing rejected by PairingHandler")
        }
    }

    private fun pin(
        device: DBusPath,
        event: (Identifier) -> PairingEvent,
    ): Int {
        val response = respond(device, event)
        return (response as? PairingResponse.ProvidePin)?.pin ?: throw rejected("PairingHandler did not provide a PIN")
    }

    internal fun addressFromPath(path: String): String? =
        path
            .substringAfterLast('/')
            .takeIf { it.startsWith("dev_") }
            ?.removePrefix("dev_")
            ?.replace('_', ':')
            ?.uppercase()

    private fun rejected(reason: String): DBusExecutionException = org.bluez.Error.Rejected(reason)

    private fun warn(message: String) {
        backendLog(BleLogEvent.Warning(identifier = null, message = "BlueZ $message"))
    }

    private object Agent : Agent1 {
        override fun getObjectPath(): String = AGENT_PATH

        override fun Release() {}

        override fun RequestPinCode(device: DBusPath): String =
            pin(device) { PairingEvent.PasskeyRequest(it) }.toString().padStart(PIN_LENGTH, '0')

        override fun DisplayPinCode(
            device: DBusPath,
            pincode: String,
        ) {
            val passkey = pincode.toIntOrNull() ?: return
            runCatching { respond(device) { PairingEvent.PasskeyNotification(it, passkey) } }
        }

        override fun RequestPasskey(device: DBusPath): UInt32 =
            UInt32(pin(device) { PairingEvent.PasskeyRequest(it) }.toLong())

        override fun DisplayPasskey(
            device: DBusPath,
            passkey: UInt32,
            entered: UInt16,
        ) {
            runCatching { respond(device) { PairingEvent.PasskeyNotification(it, passkey.toInt()) } }
        }

        override fun RequestConfirmation(
            device: DBusPath,
            passkey: UInt32,
        ) {
            confirm(device) { PairingEvent.NumericComparison(it, passkey.toInt()) }
        }

        override fun RequestAuthorization(device: DBusPath) {
            confirm(device) { PairingEvent.JustWorksConfirmation(it) }
        }

        override fun AuthorizeService(
            device: DBusPath,
            uuid: String,
        ) {
            val address = addressFromPath(device.path)
            if (address == null ||
                !handlers.containsKey(address)
            ) {
                throw rejected("service authorization for unknown device")
            }
        }

        override fun Cancel() {}
    }

    private const val PIN_LENGTH = 6
}
