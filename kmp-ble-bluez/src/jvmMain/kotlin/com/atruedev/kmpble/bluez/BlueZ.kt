package com.atruedev.kmpble.bluez

import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.ObjectManager

/** Linux BlueZ helpers for the `kmp-ble-bluez` JVM backend. */
public object BlueZ {
    /** [com.atruedev.kmpble.backend.BleBackend.id] of this backend, for `-Dkmpble.backend=bluez`. */
    public const val BACKEND_ID: String = "bluez"

    /** Optional adapter id (for example `hci0` or the adapter MAC). Defaults to the first adapter. */
    public const val ADAPTER_PROPERTY: String = "kmpble.bluez.adapter"

    /** `platformCode` of a [com.atruedev.kmpble.error.ConnectionFailed] when the system D-Bus is unreachable. */
    public const val ERROR_DBUS_UNAVAILABLE: Int = -20

    /** `platformCode` when no BlueZ adapter is present. */
    public const val ERROR_NO_ADAPTER: Int = -21

    /** `platformCode` when BlueZ has no object for the device (it was never discovered or was removed). */
    public const val ERROR_DEVICE_NOT_FOUND: Int = -22

    /** `platformCode` when `Device1.Connect` failed. */
    public const val ERROR_CONNECT_FAILED: Int = -23

    /** True when running on a Linux operating system. */
    public fun isLinux(): Boolean {
        val os = System.getProperty("os.name")?.lowercase() ?: return false
        return os.contains("linux")
    }

    /**
     * Best-effort probe: Linux, reachable system D-Bus, and at least one BlueZ adapter.
     * Safe to call on CI; returns false when BlueZ is absent.
     */
    public fun isAvailable(): Boolean {
        if (!isLinux()) return false
        return runCatching {
            openSystemBus().use { connection -> findAdapterPath(connection) != null }
        }.getOrDefault(false)
    }

    internal fun adapterNameOrNull(): String? = System.getProperty(ADAPTER_PROPERTY)?.trim()?.takeIf { it.isNotEmpty() }

    internal fun openSystemBus(): DBusConnection = DBusConnectionBuilder.forSystemBus().build()

    /**
     * Private connection for an exported GATT application. bluetoothd does not wait for the
     * `WriteValue` reply on characteristics that allow write without response, so method calls
     * run on one thread to reach the application in the order bluetoothd sent them.
     */
    internal fun openGattServerBus(address: String? = null): DBusConnection =
        (if (address == null) DBusConnectionBuilder.forSystemBus() else DBusConnectionBuilder.forAddress(address))
            .withShared(false)
            .receivingThreadConfig()
            .withMethodCallThreadCount(1)
            .connectionConfig()
            .build()

    /**
     * Object path of the adapter selected by [ADAPTER_PROPERTY] (name such as `hci0` or MAC),
     * or the first adapter. `null` when BlueZ reports none.
     */
    internal fun findAdapterPath(connection: DBusConnection): String? {
        val objects = managedObjects(connection)
        val adapters =
            objects
                .filterValues { it.containsKey(ADAPTER_INTERFACE) }
                .mapValues { (_, interfaces) -> interfaces.getValue(ADAPTER_INTERFACE) }
        if (adapters.isEmpty()) return null
        val requested = adapterNameOrNull() ?: return adapters.keys.minOrNull()
        return adapters.entries
            .firstOrNull { (path, properties) ->
                path.substringAfterLast('/') == requested ||
                    (properties["Address"] as? String)?.equals(requested, ignoreCase = true) == true
            }?.key
    }

    internal fun managedObjects(connection: DBusConnection): Map<String, Map<String, Map<String, Any?>>> {
        val manager = connection.getRemoteObject(SERVICE, "/", ObjectManager::class.java)
        return manager.GetManagedObjects().entries.associate { (path, interfaces) ->
            path.path to
                interfaces.mapValues { (_, properties) -> properties.mapValues { (_, variant) -> variant.value } }
        }
    }

    internal fun devicePathFor(
        adapterPath: String,
        address: String,
    ): String = "$adapterPath/dev_${address.uppercase().replace(':', '_')}"

    internal const val SERVICE = "org.bluez"
    internal const val ADAPTER_INTERFACE = "org.bluez.Adapter1"
    internal const val DEVICE_INTERFACE = "org.bluez.Device1"
    internal const val GATT_SERVICE_INTERFACE = "org.bluez.GattService1"
    internal const val GATT_CHARACTERISTIC_INTERFACE = "org.bluez.GattCharacteristic1"
    internal const val GATT_DESCRIPTOR_INTERFACE = "org.bluez.GattDescriptor1"
    internal const val GATT_MANAGER_INTERFACE = "org.bluez.GattManager1"
    internal const val ADVERTISING_MANAGER_INTERFACE = "org.bluez.LEAdvertisingManager1"
}
