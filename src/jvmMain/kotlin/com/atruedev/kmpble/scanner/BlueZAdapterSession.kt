package com.atruedev.kmpble.scanner

import com.github.hypfvieh.bluetooth.DeviceManager
import com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice
import org.bluez.Device1
import org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler
import org.freedesktop.dbus.handlers.AbstractSignalHandlerBase
import org.freedesktop.dbus.interfaces.ObjectManager
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant

/**
 * Narrow seam over hypfvieh BlueZ adapter + DeviceManager so discovery lifecycle
 * is unit-testable without D-Bus or hardware.
 */
internal interface BlueZAdapterSession {
    val adapterPath: String

    fun setDiscoveryFilter(filters: Map<String, Variant<*>>): Result<Unit>

    fun registerHandlers(
        onPropertiesChanged: (path: String, changed: Map<String, Any?>) -> Unit,
        onDeviceAdded: (path: String, properties: Map<String, Any?>) -> Unit,
    ): Boolean

    fun unregisterHandlers()

    fun seedExistingDevices(onDevice: (BluetoothDevice) -> Unit)

    fun startDiscovery(): Boolean

    fun stopDiscovery()

    fun closeConnection()
}

internal sealed interface BlueZSessionOpenResult {
    data class Ready(
        val session: BlueZAdapterSession,
    ) : BlueZSessionOpenResult

    data class Failed(
        val code: Int,
        val message: String,
    ) : BlueZSessionOpenResult
}

internal fun interface BlueZSessionFactory {
    fun open(): BlueZSessionOpenResult
}

internal object DefaultBlueZSessionFactory : BlueZSessionFactory {
    override fun open(): BlueZSessionOpenResult {
        BlueZ.requireLinux()

        val deviceManager =
            try {
                DeviceManager.createInstance(false)
            } catch (error: Exception) {
                return BlueZSessionOpenResult.Failed(
                    BlueZScanner.ERROR_DBUS_UNAVAILABLE,
                    "Could not connect to system D-Bus: ${error.message}",
                )
            }

        val adapter = resolveAdapter(deviceManager)
        if (adapter == null) {
            deviceManager.closeConnection()
            return BlueZSessionOpenResult.Failed(
                BlueZScanner.ERROR_NO_ADAPTER,
                "No BlueZ adapter found. Is bluetoothd running and an adapter present?",
            )
        }

        if (!adapter.isPowered) {
            deviceManager.closeConnection()
            return BlueZSessionOpenResult.Failed(
                BlueZScanner.ERROR_ADAPTER_OFF,
                "BlueZ adapter ${adapter.deviceName} is powered off",
            )
        }

        return BlueZSessionOpenResult.Ready(
            HypfviehBlueZAdapterSession(deviceManager, adapter),
        )
    }

    private fun resolveAdapter(deviceManager: DeviceManager): BluetoothAdapter? {
        val requested = BlueZ.adapterNameOrNull()
        val adapters = deviceManager.scanForBluetoothAdapters()
        if (adapters.isEmpty()) return null
        if (requested == null) return deviceManager.adapter
        return deviceManager.getAdapter(requested)
    }
}

internal class HypfviehBlueZAdapterSession(
    private val deviceManager: DeviceManager,
    private val adapter: BluetoothAdapter,
) : BlueZAdapterSession {
    override val adapterPath: String = adapter.dbusPath

    private var propertyHandler: AbstractPropertiesChangedHandler? = null
    private var addedHandler: AbstractSignalHandlerBase<ObjectManager.InterfacesAdded>? = null

    override fun setDiscoveryFilter(filters: Map<String, Variant<*>>): Result<Unit> =
        runCatching { adapter.setDiscoveryFilter(filters) }

    override fun registerHandlers(
        onPropertiesChanged: (path: String, changed: Map<String, Any?>) -> Unit,
        onDeviceAdded: (path: String, properties: Map<String, Any?>) -> Unit,
    ): Boolean =
        try {
            val property =
                object : AbstractPropertiesChangedHandler() {
                    override fun handle(signal: Properties.PropertiesChanged) {
                        if (signal.interfaceName != Device1::class.java.name) return
                        val path = signal.path ?: return
                        if (!path.startsWith(adapterPath + "/dev_")) return

                        val changed =
                            signal.propertiesChanged
                                ?.mapValues { it.value.value }
                                .orEmpty()
                        if (changed.isEmpty()) return
                        onPropertiesChanged(path, changed)
                    }
                }
            val added =
                object : AbstractSignalHandlerBase<ObjectManager.InterfacesAdded>() {
                    override fun getImplementationClass(): Class<ObjectManager.InterfacesAdded> =
                        ObjectManager.InterfacesAdded::class.java

                    override fun handle(signal: ObjectManager.InterfacesAdded) {
                        val path = signal.objectPath ?: return
                        if (!path.startsWith(adapterPath + "/dev_")) return
                        val deviceProps = signal.interfaces?.get(Device1::class.java.name) ?: return
                        onDeviceAdded(path, unwrapVariantMap(deviceProps))
                    }
                }
            deviceManager.registerPropertyHandler(property)
            deviceManager.registerSignalHandler(added)
            propertyHandler = property
            addedHandler = added
            true
        } catch (_: Exception) {
            false
        }

    override fun unregisterHandlers() {
        propertyHandler?.let { runCatching { deviceManager.unRegisterPropertyHandler(it) } }
        addedHandler?.let { runCatching { deviceManager.unRegisterSignalHandler(it) } }
        propertyHandler = null
        addedHandler = null
    }

    override fun seedExistingDevices(onDevice: (BluetoothDevice) -> Unit) {
        deviceManager.findBtDevicesByIntrospection(adapter)
        deviceManager.getDevices(adapter.address, true).forEach(onDevice)
    }

    override fun startDiscovery(): Boolean = adapter.startDiscovery()

    override fun stopDiscovery() {
        adapter.stopDiscovery()
    }

    override fun closeConnection() {
        deviceManager.closeConnection()
    }
}
