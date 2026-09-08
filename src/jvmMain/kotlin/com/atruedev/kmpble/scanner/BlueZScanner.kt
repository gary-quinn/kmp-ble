package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import com.atruedev.kmpble.scanner.internal.toScanEvents
import com.github.hypfvieh.bluetooth.DeviceManager
import com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.bluez.Device1
import org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler
import org.freedesktop.dbus.handlers.AbstractSignalHandlerBase
import org.freedesktop.dbus.interfaces.ObjectManager
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant
import java.util.concurrent.ConcurrentHashMap

/**
 * Linux BlueZ-backed [Scanner] using D-Bus ([org.bluez.Adapter1.StartDiscovery] and
 * [org.bluez.Device1] property updates).
 *
 * Use [BlueZScanner] to construct explicitly. The portable [Scanner] factory on JVM remains
 * disabled unless [BlueZ.ENABLE_PROPERTY] is set to `"true"`.
 */
public class BlueZScanner(
    configure: ScannerConfig.() -> Unit = {},
) : Scanner {
    private val config = ScannerConfig().apply(configure)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val scanEvents: Flow<ScanEvent> = createRawScanFlow().toScanEvents(config, scope)

    override fun close() {
        scope.cancel()
    }

    private fun createRawScanFlow(): Flow<Advertisement> =
        callbackFlow {
            BlueZ.requireLinux()

            val deviceManager =
                try {
                    DeviceManager.createInstance(false)
                } catch (error: Exception) {
                    close(
                        ScanFailedException(
                            ERROR_DBUS_UNAVAILABLE,
                            "Could not connect to system D-Bus: ${error.message}",
                        ),
                    )
                    return@callbackFlow
                }

            val adapter = resolveAdapter(deviceManager)
            if (adapter == null) {
                deviceManager.closeConnection()
                close(
                    ScanFailedException(
                        ERROR_NO_ADAPTER,
                        "No BlueZ adapter found. Is bluetoothd running and an adapter present?",
                    ),
                )
                return@callbackFlow
            }

            if (!adapter.isPowered) {
                deviceManager.closeConnection()
                close(
                    ScanFailedException(
                        ERROR_ADAPTER_OFF,
                        "BlueZ adapter ${adapter.deviceName} is powered off",
                    ),
                )
                return@callbackFlow
            }

            applyLeDiscoveryFilter(adapter)

            val snapshots = ConcurrentHashMap<String, BlueZDeviceSnapshot>()

            fun emitSnapshot(snapshot: BlueZDeviceSnapshot) {
                if (snapshot.rssi == null) return
                val merged =
                    snapshots.merge(
                        snapshot.dbusPath,
                        snapshot,
                    ) { previous, update ->
                        previous.merge(update)
                    }!!
                trySend(merged.toAdvertisement())
            }

            fun emitDevice(device: BluetoothDevice) {
                device.toSnapshot()?.let(::emitSnapshot)
            }

            val propertyHandler =
                object : AbstractPropertiesChangedHandler() {
                    override fun handle(signal: Properties.PropertiesChanged) {
                        if (signal.interfaceName != Device1::class.java.name) return
                        val path = signal.path ?: return
                        if (!path.startsWith(adapter.dbusPath + "/dev_")) return

                        val changed =
                            signal.propertiesChanged
                                ?.mapValues { it.value.value }
                                .orEmpty()
                        if (changed.isEmpty()) return

                        val existing = snapshots[path]
                        val address = existing?.address ?: (changed["Address"] as? String) ?: return
                        val delta = snapshotFromChangedProperties(path, address, changed)
                        emitSnapshot((existing ?: delta).merge(delta))
                    }
                }

            val addedHandler =
                object : AbstractSignalHandlerBase<ObjectManager.InterfacesAdded>() {
                    override fun getImplementationClass(): Class<ObjectManager.InterfacesAdded> =
                        ObjectManager.InterfacesAdded::class.java

                    override fun handle(signal: ObjectManager.InterfacesAdded) {
                        val path = signal.objectPath ?: return
                        if (!path.startsWith(adapter.dbusPath + "/dev_")) return
                        val deviceProps = signal.interfaces?.get(Device1::class.java.name) ?: return
                        snapshotFromPropertyMap(path, unwrapVariantMap(deviceProps))?.let(::emitSnapshot)
                    }
                }

            try {
                deviceManager.registerPropertyHandler(propertyHandler)
                deviceManager.registerSignalHandler(addedHandler)
            } catch (error: Exception) {
                deviceManager.closeConnection()
                close(
                    ScanFailedException(
                        ERROR_DBUS_UNAVAILABLE,
                        "Failed to register BlueZ signal handlers: ${error.message}",
                    ),
                )
                return@callbackFlow
            }

            deviceManager.findBtDevicesByIntrospection(adapter)
            deviceManager.getDevices(adapter.address, true).forEach(::emitDevice)

            if (!adapter.startDiscovery()) {
                runCatching { deviceManager.unRegisterPropertyHandler(propertyHandler) }
                runCatching { deviceManager.unRegisterSignalHandler(addedHandler) }
                deviceManager.closeConnection()
                close(
                    ScanFailedException(
                        ERROR_DISCOVERY_FAILED,
                        "BlueZ StartDiscovery failed on ${adapter.deviceName}",
                    ),
                )
                return@callbackFlow
            }

            logEvent(BleLogEvent.ScanStarted(config.filterGroups.size))

            awaitClose {
                adapter.stopDiscovery()
                runCatching { deviceManager.unRegisterPropertyHandler(propertyHandler) }
                runCatching { deviceManager.unRegisterSignalHandler(addedHandler) }
                deviceManager.closeConnection()
                logEvent(BleLogEvent.ScanStopped("closed"))
            }
        }

    private fun resolveAdapter(deviceManager: DeviceManager): BluetoothAdapter? {
        val requested = BlueZ.adapterNameOrNull()
        val adapters = deviceManager.scanForBluetoothAdapters()
        if (adapters.isEmpty()) return null
        if (requested == null) return deviceManager.adapter
        return deviceManager.getAdapter(requested)
    }

    private fun applyLeDiscoveryFilter(adapter: BluetoothAdapter) {
        val filters =
            linkedMapOf<String, Variant<*>>(
                "Transport" to Variant("le"),
                "DuplicateData" to Variant(true),
            )
        runCatching { adapter.setDiscoveryFilter(filters) }
    }

    public companion object {
        public const val ERROR_DBUS_UNAVAILABLE: Int = -10
        public const val ERROR_NO_ADAPTER: Int = -11
        public const val ERROR_ADAPTER_OFF: Int = -12
        public const val ERROR_DISCOVERY_FAILED: Int = -13
    }
}
