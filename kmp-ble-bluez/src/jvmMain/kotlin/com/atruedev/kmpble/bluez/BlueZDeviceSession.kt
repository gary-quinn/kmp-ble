package com.atruedev.kmpble.bluez

import org.bluez.Adapter1
import org.bluez.Device1
import org.bluez.GattCharacteristic1
import org.bluez.GattDescriptor1
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler
import org.freedesktop.dbus.handlers.AbstractSignalHandlerBase
import org.freedesktop.dbus.interfaces.ObjectManager
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.UInt16
import org.freedesktop.dbus.types.Variant

/**
 * Unsolicited BlueZ signals for one device, delivered on a D-Bus dispatch thread.
 */
internal interface BlueZDeviceSignals {
    fun onDeviceChanged(changed: Map<String, Any?>)

    fun onAttributeChanged(
        path: String,
        changed: Map<String, Any?>,
    )

    fun onGattTableChanged()

    fun onAdapterPowered(powered: Boolean)
}

/** A D-Bus call that may outlive dbus-java's 20 s synchronous reply timeout. */
internal interface BlueZPendingCall {
    fun isDone(): Boolean

    /** Returns normally on success; throws the D-Bus error otherwise. */
    fun result()
}

/**
 * Narrow, blocking seam over the BlueZ D-Bus objects of one device so the transport is
 * unit-testable without D-Bus or hardware. Methods throw the BlueZ D-Bus exceptions.
 */
internal interface BlueZDeviceSession {
    val devicePath: String
    val address: String

    fun registerSignals(signals: BlueZDeviceSignals): Boolean

    fun unregisterSignals()

    fun connect(): BlueZPendingCall

    fun disconnect()

    fun isConnected(): Boolean

    fun isServicesResolved(): Boolean

    fun gattServices(): List<BlueZGattServiceSnapshot>

    fun readCharacteristic(path: String): ByteArray

    fun writeCharacteristic(
        path: String,
        data: ByteArray,
        type: String,
    )

    fun readDescriptor(path: String): ByteArray

    fun writeDescriptor(
        path: String,
        data: ByteArray,
    )

    fun startNotify(path: String)

    fun stopNotify(path: String)

    fun rssi(): Int?

    fun isPaired(): Boolean

    fun isBonded(): Boolean?

    fun pair(): BlueZPendingCall

    fun cancelPairing()

    fun removeFromAdapter()

    fun closeConnection()
}

internal sealed interface BlueZDeviceSessionOpenResult {
    data class Ready(
        val session: BlueZDeviceSession,
    ) : BlueZDeviceSessionOpenResult

    data class Failed(
        val code: Int,
        val message: String,
    ) : BlueZDeviceSessionOpenResult
}

internal fun interface BlueZDeviceSessionFactory {
    fun open(
        devicePath: String?,
        address: String,
    ): BlueZDeviceSessionOpenResult
}

internal object DefaultBlueZDeviceSessionFactory : BlueZDeviceSessionFactory {
    override fun open(
        devicePath: String?,
        address: String,
    ): BlueZDeviceSessionOpenResult {
        if (!BlueZ.isLinux()) {
            return BlueZDeviceSessionOpenResult.Failed(BlueZ.ERROR_DBUS_UNAVAILABLE, "BlueZ requires Linux")
        }
        val connection =
            try {
                BlueZ.openSystemBus()
            } catch (error: Exception) {
                return BlueZDeviceSessionOpenResult.Failed(
                    BlueZ.ERROR_DBUS_UNAVAILABLE,
                    "Could not connect to system D-Bus: ${error.message}",
                )
            }
        return try {
            val adapterPath =
                BlueZ.findAdapterPath(connection)
                    ?: return BlueZDeviceSessionOpenResult
                        .Failed(
                            BlueZ.ERROR_NO_ADAPTER,
                            "No BlueZ adapter found. Is bluetoothd running and an adapter present?",
                        ).also { connection.close() }
            val path = devicePath ?: BlueZ.devicePathFor(adapterPath, address)
            val objects = BlueZ.managedObjects(connection)
            val device = objects[path]?.get(BlueZ.DEVICE_INTERFACE)
            if (device == null) {
                connection.close()
                return BlueZDeviceSessionOpenResult.Failed(
                    BlueZ.ERROR_DEVICE_NOT_FOUND,
                    "BlueZ has no device at $path ($address). Scan for it first.",
                )
            }
            val deviceAdapter = (device["Adapter"] as? DBusPath)?.path ?: adapterPath
            BlueZDeviceSessionOpenResult.Ready(DbusBlueZDeviceSession(connection, deviceAdapter, path, address))
        } catch (error: Exception) {
            connection.close()
            BlueZDeviceSessionOpenResult.Failed(BlueZ.ERROR_DBUS_UNAVAILABLE, "BlueZ lookup failed: ${error.message}")
        }
    }
}

internal class DbusBlueZDeviceSession(
    private val connection: DBusConnection,
    private val adapterPath: String,
    override val devicePath: String,
    override val address: String,
) : BlueZDeviceSession {
    private val device: Device1 = connection.getRemoteObject(BlueZ.SERVICE, devicePath, Device1::class.java)
    private val deviceProperties: Properties =
        connection.getRemoteObject(
            BlueZ.SERVICE,
            devicePath,
            Properties::class.java,
        )

    private val registrations = mutableListOf<AutoCloseable>()

    override fun registerSignals(signals: BlueZDeviceSignals): Boolean =
        try {
            val properties =
                object : AbstractPropertiesChangedHandler() {
                    override fun handle(signal: Properties.PropertiesChanged) {
                        val path = signal.path ?: return
                        val changed = signal.propertiesChanged?.mapValues { it.value.value }.orEmpty()
                        if (changed.isEmpty()) return
                        when {
                            path == devicePath && signal.interfaceName == BlueZ.DEVICE_INTERFACE ->
                                signals
                                    .onDeviceChanged(
                                        changed,
                                    )
                            path == adapterPath && signal.interfaceName == BlueZ.ADAPTER_INTERFACE ->
                                (changed["Powered"] as? Boolean)?.let(signals::onAdapterPowered)
                            path.startsWith("$devicePath/") -> signals.onAttributeChanged(path, changed)
                        }
                    }
                }
            val added =
                object : AbstractSignalHandlerBase<ObjectManager.InterfacesAdded>() {
                    override fun getImplementationClass(): Class<ObjectManager.InterfacesAdded> =
                        ObjectManager.InterfacesAdded::class.java

                    override fun handle(signal: ObjectManager.InterfacesAdded) {
                        val path = signal.signalSource?.path ?: return
                        if (path.startsWith("$devicePath/") &&
                            signal.interfaces?.containsKey(BlueZ.GATT_SERVICE_INTERFACE) == true
                        ) {
                            signals.onGattTableChanged()
                        }
                    }
                }
            val removed =
                object : AbstractSignalHandlerBase<ObjectManager.InterfacesRemoved>() {
                    override fun getImplementationClass(): Class<ObjectManager.InterfacesRemoved> =
                        ObjectManager.InterfacesRemoved::class.java

                    override fun handle(signal: ObjectManager.InterfacesRemoved) {
                        val path = signal.signalSource?.path ?: return
                        if (path.startsWith("$devicePath/") &&
                            signal.interfaces?.contains(BlueZ.GATT_SERVICE_INTERFACE) == true
                        ) {
                            signals.onGattTableChanged()
                        }
                    }
                }
            registrations += connection.addSigHandler(Properties.PropertiesChanged::class.java, properties)
            registrations += connection.addSigHandler(ObjectManager.InterfacesAdded::class.java, added)
            registrations += connection.addSigHandler(ObjectManager.InterfacesRemoved::class.java, removed)
            true
        } catch (_: Exception) {
            unregisterSignals()
            false
        }

    override fun unregisterSignals() {
        registrations.forEach { runCatching { it.close() } }
        registrations.clear()
    }

    override fun connect(): BlueZPendingCall = asyncCall("Connect")

    override fun disconnect() {
        device.Disconnect()
    }

    override fun isConnected(): Boolean = deviceProperty<Boolean>("Connected") == true

    override fun isServicesResolved(): Boolean = deviceProperty<Boolean>("ServicesResolved") == true

    override fun gattServices(): List<BlueZGattServiceSnapshot> {
        val objects = BlueZ.managedObjects(connection).filterKeys { it.startsWith("$devicePath/") }
        val descriptors =
            objects
                .mapNotNull { (path, interfaces) ->
                    val props = interfaces[BlueZ.GATT_DESCRIPTOR_INTERFACE] ?: return@mapNotNull null
                    val owner = (props["Characteristic"] as? DBusPath)?.path ?: path.substringBeforeLast('/')
                    owner to
                        BlueZGattDescriptorSnapshot(
                            path = path,
                            uuid =
                                props["UUID"] as? String ?: return@mapNotNull null,
                        )
                }.groupBy({ it.first }, { it.second })
        val characteristics =
            objects
                .mapNotNull { (path, interfaces) ->
                    val props = interfaces[BlueZ.GATT_CHARACTERISTIC_INTERFACE] ?: return@mapNotNull null
                    val owner = (props["Service"] as? DBusPath)?.path ?: path.substringBeforeLast('/')
                    owner to
                        BlueZGattCharacteristicSnapshot(
                            path = path,
                            uuid = props["UUID"] as? String ?: return@mapNotNull null,
                            flags = stringValues(props["Flags"]),
                            descriptors = descriptors[path].orEmpty().sortedBy { it.path },
                            mtu = (props["MTU"] as? Number)?.toInt() ?: (props["MTU"] as? UInt16)?.toInt(),
                        )
                }.groupBy({ it.first }, { it.second })
        return objects
            .mapNotNull { (path, interfaces) ->
                val props = interfaces[BlueZ.GATT_SERVICE_INTERFACE] ?: return@mapNotNull null
                BlueZGattServiceSnapshot(
                    path = path,
                    uuid = props["UUID"] as? String ?: return@mapNotNull null,
                    characteristics = characteristics[path].orEmpty().sortedBy { it.path },
                )
            }.sortedBy { it.path }
    }

    override fun readCharacteristic(path: String): ByteArray = characteristic(path).ReadValue(emptyMap())

    override fun writeCharacteristic(
        path: String,
        data: ByteArray,
        type: String,
    ) {
        characteristic(path).WriteValue(data, mapOf("type" to Variant(type)))
    }

    override fun readDescriptor(path: String): ByteArray = descriptor(path).ReadValue(emptyMap())

    override fun writeDescriptor(
        path: String,
        data: ByteArray,
    ) {
        descriptor(path).WriteValue(data, emptyMap())
    }

    override fun startNotify(path: String) {
        characteristic(path).StartNotify()
    }

    override fun stopNotify(path: String) {
        characteristic(path).StopNotify()
    }

    override fun rssi(): Int? = deviceProperty<Number>("RSSI")?.toInt()

    override fun isPaired(): Boolean = deviceProperty<Boolean>("Paired") == true

    override fun isBonded(): Boolean? = runCatching { deviceProperty<Boolean>("Bonded") }.getOrNull()

    override fun pair(): BlueZPendingCall = asyncCall("Pair")

    override fun cancelPairing() {
        device.CancelPairing()
    }

    override fun removeFromAdapter() {
        connection
            .getRemoteObject(BlueZ.SERVICE, adapterPath, Adapter1::class.java)
            .RemoveDevice(DBusPath(devicePath))
    }

    override fun closeConnection() {
        unregisterSignals()
        runCatching { connection.close() }
    }

    private fun asyncCall(method: String): BlueZPendingCall {
        val reply = connection.callMethodAsync(device, method)
        return object : BlueZPendingCall {
            override fun isDone(): Boolean = reply.hasReply()

            override fun result() {
                reply.reply
            }
        }
    }

    private fun characteristic(path: String): GattCharacteristic1 =
        connection.getRemoteObject(BlueZ.SERVICE, path, GattCharacteristic1::class.java)

    private fun descriptor(path: String): GattDescriptor1 =
        connection.getRemoteObject(BlueZ.SERVICE, path, GattDescriptor1::class.java)

    @Suppress("UNCHECKED_CAST")
    private fun <T> deviceProperty(name: String): T? =
        runCatching { deviceProperties.Get<Any?>(BlueZ.DEVICE_INTERFACE, name) }
            .getOrNull()
            ?.let { (if (it is Variant<*>) it.value else it) as? T }
}

internal fun stringValues(value: Any?): List<String> =
    when (value) {
        is List<*> -> value.mapNotNull { it as? String }
        is Array<*> -> value.mapNotNull { it as? String }
        else -> emptyList()
    }
