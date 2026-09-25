package com.atruedev.kmpble.bluez

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.adapter.BluetoothAdapterState
import com.atruedev.kmpble.backend.AdapterCapabilities
import com.atruedev.kmpble.backend.AdapterTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler
import org.freedesktop.dbus.interfaces.Properties

/**
 * Adapter state from `Adapter1.Powered`, updated through `PropertiesChanged`. The D-Bus
 * connection opens on a background thread, so construction never blocks.
 */
internal class BlueZAdapterTransport : AdapterTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<BluetoothAdapterState>(BluetoothAdapterState.Unavailable)
    override val state: StateFlow<BluetoothAdapterState> = _state.asStateFlow()

    @Volatile private var connection: DBusConnection? = null

    @Volatile private var registration: AutoCloseable? = null

    @Volatile private var adapterPath: String? = null

    init {
        scope.launch { start() }
    }

    override val capabilities: AdapterCapabilities by lazy { readCapabilities() }

    override fun bondedDevices(): List<Identifier> {
        val bus = connection ?: return emptyList()
        val adapter = adapterPath ?: return emptyList()
        return runCatching {
            BlueZ
                .managedObjects(bus)
                .filterKeys { it.startsWith("$adapter/dev_") }
                .mapNotNull { (_, interfaces) ->
                    val device = interfaces[BlueZ.DEVICE_INTERFACE] ?: return@mapNotNull null
                    val bonded = (device["Bonded"] as? Boolean) ?: (device["Paired"] as? Boolean) ?: false
                    (device["Address"] as? String)?.takeIf { bonded }?.let(::Identifier)
                }
        }.getOrDefault(emptyList())
    }

    override fun close() {
        runCatching { registration?.close() }
        registration = null
        runCatching { connection?.close() }
        connection = null
        scope.cancel()
    }

    private fun start() {
        if (!BlueZ.isLinux()) {
            _state.value = BluetoothAdapterState.Unsupported
            return
        }
        val bus =
            try {
                BlueZ.openSystemBus()
            } catch (e: Exception) {
                _state.value =
                    if (e.isAccessDenied()) BluetoothAdapterState.Unauthorized else BluetoothAdapterState.Unavailable
                return
            }
        connection = bus
        val path =
            try {
                BlueZ.findAdapterPath(bus)
            } catch (e: Exception) {
                _state.value =
                    if (e.isAccessDenied()) BluetoothAdapterState.Unauthorized else BluetoothAdapterState.Unavailable
                return
            }
        if (path == null) {
            _state.value = BluetoothAdapterState.Unsupported
            return
        }
        adapterPath = path
        registration =
            runCatching {
                bus.addSigHandler(
                    Properties.PropertiesChanged::class.java,
                    object : AbstractPropertiesChangedHandler() {
                        override fun handle(signal: Properties.PropertiesChanged) {
                            if (signal.path != path || signal.interfaceName != BlueZ.ADAPTER_INTERFACE) return
                            val powered = signal.propertiesChanged?.get("Powered")?.value as? Boolean ?: return
                            _state.value = if (powered) BluetoothAdapterState.On else BluetoothAdapterState.Off
                        }
                    },
                )
            }.getOrNull()
        val powered =
            runCatching {
                BlueZ.managedObjects(bus)[path]?.get(BlueZ.ADAPTER_INTERFACE)?.get("Powered") as? Boolean
            }.getOrNull()
        _state.value =
            when (powered) {
                true -> BluetoothAdapterState.On
                false -> BluetoothAdapterState.Off
                null -> BluetoothAdapterState.Unavailable
            }
    }

    private fun readCapabilities(): AdapterCapabilities {
        val bus = connection ?: return AdapterCapabilities()
        val path = adapterPath ?: return AdapterCapabilities()
        val manager =
            runCatching {
                BlueZ
                    .managedObjects(
                        bus,
                    )[path]
                    ?.get(BlueZ.ADVERTISING_MANAGER_INTERFACE)
            }.getOrNull()
        val secondaryChannels = stringValues(manager?.get("SupportedSecondaryChannels"))
        val instances = (manager?.get("SupportedInstances") as? Number)?.toInt() ?: 0
        return AdapterCapabilities(
            supportsExtendedAdvertising = secondaryChannels.isNotEmpty() || instances > 1,
            supportsLe2mPhy = "2M" in secondaryChannels,
            supportsLeCodedPhy = "Coded" in secondaryChannels,
            supportsPeriodicAdvertising = false,
        )
    }
}

internal fun Throwable.isAccessDenied(): Boolean =
    javaClass.name.endsWith("AccessDenied") ||
        message?.contains("AccessDenied", ignoreCase = true) == true ||
        message?.contains("not allowed", ignoreCase = true) == true ||
        message?.contains("Permission denied", ignoreCase = true) == true
