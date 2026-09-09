package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.scanner.BlueZ
import com.github.hypfvieh.bluetooth.DeviceManager
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattDescriptor
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService
import org.bluez.Device1
import org.bluez.GattCharacteristic1
import org.bluez.GattDescriptor1
import org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant

/**
 * Narrow seam over hypfvieh BlueZ [BluetoothDevice] GATT + connect so peripheral
 * lifecycle is unit-testable without D-Bus or hardware.
 */
internal interface BlueZDeviceSession {
    val devicePath: String
    val address: String

    fun registerHandlers(
        onDevicePropertiesChanged: (changed: Map<String, Any?>) -> Unit,
        onGattPropertiesChanged: (path: String, changed: Map<String, Any?>) -> Unit,
    ): Boolean

    fun unregisterHandlers()

    fun connect(): Result<Unit>

    fun disconnect(): Result<Unit>

    fun isConnected(): Boolean

    fun isServicesResolved(): Boolean

    fun refreshGattServices()

    fun getGattServices(): List<BlueZGattServiceSnapshot>

    fun readCharacteristic(path: String): Result<ByteArray>

    fun writeCharacteristic(
        path: String,
        data: ByteArray,
        writeType: String,
    ): Result<Unit>

    fun readDescriptor(path: String): Result<ByteArray>

    fun writeDescriptor(
        path: String,
        data: ByteArray,
    ): Result<Unit>

    fun startNotify(path: String): Result<Unit>

    fun stopNotify(path: String): Result<Unit>

    fun readRssi(): Int?

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
        devicePath: String,
        address: String,
    ): BlueZDeviceSessionOpenResult
}

internal object DefaultBlueZDeviceSessionFactory : BlueZDeviceSessionFactory {
    override fun open(
        devicePath: String,
        address: String,
    ): BlueZDeviceSessionOpenResult {
        BlueZ.requireLinux()

        val deviceManager =
            try {
                DeviceManager.createInstance(false)
            } catch (error: Exception) {
                return BlueZDeviceSessionOpenResult.Failed(
                    BlueZPeripheral.ERROR_DBUS_UNAVAILABLE,
                    "Could not connect to system D-Bus: ${error.message}",
                )
            }

        val adapter =
            runCatching {
                val requested = BlueZ.adapterNameOrNull()
                val adapters = deviceManager.scanForBluetoothAdapters()
                if (adapters.isEmpty()) return@runCatching null
                if (requested == null) deviceManager.adapter else deviceManager.getAdapter(requested)
            }.getOrNull()

        if (adapter == null) {
            deviceManager.closeConnection()
            return BlueZDeviceSessionOpenResult.Failed(
                BlueZPeripheral.ERROR_NO_ADAPTER,
                "No BlueZ adapter found. Is bluetoothd running and an adapter present?",
            )
        }

        deviceManager.findBtDevicesByIntrospection(adapter)
        val device =
            deviceManager.getDevices(adapter.address, true).firstOrNull { dev ->
                dev.dbusPath == devicePath || dev.address == address
            }

        if (device == null) {
            deviceManager.closeConnection()
            return BlueZDeviceSessionOpenResult.Failed(
                BlueZPeripheral.ERROR_DEVICE_NOT_FOUND,
                "BlueZ device not found at $devicePath ($address)",
            )
        }

        return BlueZDeviceSessionOpenResult.Ready(
            HypfviehBlueZDeviceSession(deviceManager, device),
        )
    }
}

internal class HypfviehBlueZDeviceSession(
    private val deviceManager: DeviceManager,
    private val device: BluetoothDevice,
) : BlueZDeviceSession {
    override val devicePath: String = device.dbusPath
    override val address: String = checkNotNull(device.address)

    private var propertyHandler: AbstractPropertiesChangedHandler? = null

    override fun registerHandlers(
        onDevicePropertiesChanged: (changed: Map<String, Any?>) -> Unit,
        onGattPropertiesChanged: (path: String, changed: Map<String, Any?>) -> Unit,
    ): Boolean =
        try {
            val handler =
                object : AbstractPropertiesChangedHandler() {
                    override fun handle(signal: Properties.PropertiesChanged) {
                        val path = signal.path ?: return
                        val changed =
                            signal.propertiesChanged
                                ?.mapValues { it.value.value }
                                .orEmpty()
                        if (changed.isEmpty()) return
                        when (signal.interfaceName) {
                            Device1::class.java.name -> {
                                if (path == devicePath) onDevicePropertiesChanged(changed)
                            }
                            GattCharacteristic1::class.java.name,
                            GattDescriptor1::class.java.name,
                            -> onGattPropertiesChanged(path, changed)
                        }
                    }
                }
            deviceManager.registerPropertyHandler(handler)
            propertyHandler = handler
            true
        } catch (_: Exception) {
            false
        }

    override fun unregisterHandlers() {
        propertyHandler?.let { runCatching { deviceManager.unRegisterPropertyHandler(it) } }
        propertyHandler = null
    }

    override fun connect(): Result<Unit> =
        runCatching {
            if (device.isConnected == true) return@runCatching
            if (!device.connect()) error("BlueZ Device1.Connect returned false")
        }

    override fun disconnect(): Result<Unit> =
        runCatching {
            if (device.isConnected != true) return@runCatching
            if (!device.disconnect()) error("BlueZ Device1.Disconnect returned false")
        }

    override fun isConnected(): Boolean = device.isConnected == true

    override fun isServicesResolved(): Boolean = device.isServicesResolved == true

    override fun refreshGattServices() {
        device.refreshGattServices()
    }

    override fun getGattServices(): List<BlueZGattServiceSnapshot> =
        device.gattServices.map { it.toSnapshot() }

    override fun readCharacteristic(path: String): Result<ByteArray> =
        runCatching { findCharacteristic(path).readValue(emptyOptions()) }

    override fun writeCharacteristic(
        path: String,
        data: ByteArray,
        writeType: String,
    ): Result<Unit> =
        runCatching {
            findCharacteristic(path).writeValue(data, mapOf("type" to Variant(writeType)))
        }

    override fun readDescriptor(path: String): Result<ByteArray> =
        runCatching { findDescriptor(path).readValue(emptyOptions()) }

    override fun writeDescriptor(
        path: String,
        data: ByteArray,
    ): Result<Unit> =
        runCatching {
            findDescriptor(path).writeValue(data, emptyOptions())
        }

    override fun startNotify(path: String): Result<Unit> =
        runCatching { findCharacteristic(path).startNotify() }

    override fun stopNotify(path: String): Result<Unit> =
        runCatching { findCharacteristic(path).stopNotify() }

    override fun readRssi(): Int? = device.rssi?.toInt()

    override fun closeConnection() {
        unregisterHandlers()
        deviceManager.closeConnection()
    }

    private fun findCharacteristic(path: String): BluetoothGattCharacteristic {
        for (service in device.gattServices) {
            service.gattCharacteristics.firstOrNull { it.dbusPath == path }?.let { return it }
        }
        error("GATT characteristic not found at $path")
    }

    private fun findDescriptor(path: String): BluetoothGattDescriptor {
        for (service in device.gattServices) {
            for (characteristic in service.gattCharacteristics) {
                characteristic.gattDescriptors.firstOrNull { it.dbusPath == path }?.let { return it }
            }
        }
        error("GATT descriptor not found at $path")
    }

    private fun emptyOptions(): Map<String, Variant<*>> = emptyMap()
}

private fun BluetoothGattService.toSnapshot(): BlueZGattServiceSnapshot =
    BlueZGattServiceSnapshot(
        path = dbusPath,
        uuid = uuid,
        characteristics = gattCharacteristics.map { it.toSnapshot() },
    )

private fun BluetoothGattCharacteristic.toSnapshot(): BlueZGattCharacteristicSnapshot =
    BlueZGattCharacteristicSnapshot(
        path = dbusPath,
        uuid = uuid,
        flags = flags.orEmpty(),
        descriptors = gattDescriptors.map { it.toSnapshot() },
    )

private fun BluetoothGattDescriptor.toSnapshot(): BlueZGattDescriptorSnapshot =
    BlueZGattDescriptorSnapshot(
        path = dbusPath,
        uuid = uuid,
    )
