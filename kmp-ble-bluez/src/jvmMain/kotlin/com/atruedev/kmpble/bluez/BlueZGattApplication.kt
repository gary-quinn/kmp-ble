package com.atruedev.kmpble.bluez

import com.atruedev.kmpble.backend.ServerCharacteristicSpec
import com.atruedev.kmpble.backend.ServerServiceSpec
import org.bluez.GattCharacteristic1
import org.bluez.GattDescriptor1
import org.bluez.GattService1
import org.bluez.datatypes.TwoTuple
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.FileDescriptor
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.exceptions.DBusExecutionException
import org.freedesktop.dbus.interfaces.ObjectManager
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.UInt16
import org.freedesktop.dbus.types.Variant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Callbacks from exported GATT objects, invoked on D-Bus method threads. */
internal interface BlueZGattRequestHandler {
    @OptIn(ExperimentalUuidApi::class)
    fun onRead(
        characteristic: Uuid,
        options: Map<String, Variant<*>>,
    ): ByteArray

    @OptIn(ExperimentalUuidApi::class)
    fun onWrite(
        characteristic: Uuid,
        value: ByteArray,
        options: Map<String, Variant<*>>,
    )

    @OptIn(ExperimentalUuidApi::class)
    fun onNotifying(
        characteristic: Uuid,
        notifying: Boolean,
    )
}

/**
 * The object tree BlueZ expects from `GattManager1.RegisterApplication`: an ObjectManager
 * root with one `GattService1` per service and nested characteristics and descriptors.
 */
@OptIn(ExperimentalUuidApi::class)
internal class BlueZGattApplication(
    val rootPath: String,
    services: List<ServerServiceSpec>,
    private val handler: BlueZGattRequestHandler,
) : ObjectManager {
    val serviceObjects: List<ServiceObject> =
        services.mapIndexed { serviceIndex, service ->
            val servicePath = "$rootPath/service$serviceIndex"
            ServiceObject(
                path = servicePath,
                uuid = service.uuid,
                characteristics =
                    service.characteristics.mapIndexed { charIndex, characteristic ->
                        val charPath = "$servicePath/char$charIndex"
                        CharacteristicObject(
                            path = charPath,
                            servicePath = servicePath,
                            spec = characteristic,
                            descriptors =
                                characteristic.descriptors.mapIndexed { descIndex, uuid ->
                                    DescriptorObject("$charPath/desc$descIndex", charPath, uuid)
                                },
                        )
                    },
            )
        }

    private val characteristicsByUuid: Map<Uuid, CharacteristicObject> =
        serviceObjects.flatMap { it.characteristics }.associateBy { it.spec.uuid }

    fun characteristic(uuid: Uuid): CharacteristicObject? = characteristicsByUuid[uuid]

    fun export(connection: DBusConnection) {
        connection.exportObject(rootPath, this)
        for (service in serviceObjects) {
            connection.exportObject(service.path, service)
            for (characteristic in service.characteristics) {
                connection.exportObject(characteristic.path, characteristic)
                for (descriptor in characteristic.descriptors) {
                    connection.exportObject(descriptor.path, descriptor)
                }
            }
        }
    }

    fun unexport(connection: DBusConnection) {
        for (service in serviceObjects) {
            for (characteristic in service.characteristics) {
                characteristic.descriptors.forEach { runCatching { connection.unExportObject(it.path) } }
                runCatching { connection.unExportObject(characteristic.path) }
            }
            runCatching { connection.unExportObject(service.path) }
        }
        runCatching { connection.unExportObject(rootPath) }
    }

    override fun getObjectPath(): String = rootPath

    override fun GetManagedObjects(): Map<DBusPath, Map<String, Map<String, Variant<*>>>> {
        val objects = linkedMapOf<DBusPath, Map<String, Map<String, Variant<*>>>>()
        for (service in serviceObjects) {
            objects[DBusPath(service.path)] = mapOf(BlueZ.GATT_SERVICE_INTERFACE to service.properties())
            for (characteristic in service.characteristics) {
                objects[DBusPath(characteristic.path)] =
                    mapOf(BlueZ.GATT_CHARACTERISTIC_INTERFACE to characteristic.properties())
                for (descriptor in characteristic.descriptors) {
                    objects[DBusPath(descriptor.path)] =
                        mapOf(BlueZ.GATT_DESCRIPTOR_INTERFACE to descriptor.properties())
                }
            }
        }
        return objects
    }

    inner class ServiceObject(
        val path: String,
        val uuid: Uuid,
        val characteristics: List<CharacteristicObject>,
    ) : GattService1,
        Properties {
        fun properties(): Map<String, Variant<*>> =
            mapOf(
                "UUID" to Variant(uuid.toString()),
                "Primary" to Variant(true),
            )

        override fun getObjectPath(): String = path

        override fun <A : Any?> Get(
            interfaceName: String,
            propertyName: String,
        ): A = propertyOf(properties(), propertyName)

        override fun <A : Any?> Set(
            interfaceName: String,
            propertyName: String,
            value: A,
        ): Unit = throw readOnly(propertyName)

        override fun GetAll(interfaceName: String): Map<String, Variant<*>> = properties()
    }

    inner class CharacteristicObject(
        val path: String,
        private val servicePath: String,
        val spec: ServerCharacteristicSpec,
        val descriptors: List<DescriptorObject>,
    ) : GattCharacteristic1,
        Properties {
        @Volatile var notifying: Boolean = false
            private set

        fun properties(): Map<String, Variant<*>> =
            mapOf(
                "UUID" to Variant(spec.uuid.toString()),
                "Service" to Variant(DBusPath(servicePath)),
                "Flags" to Variant(characteristicFlags(spec).toTypedArray()),
                "Notifying" to Variant(notifying),
            )

        override fun getObjectPath(): String = path

        override fun ReadValue(options: Map<String, Variant<*>>): ByteArray = handler.onRead(spec.uuid, options)

        override fun WriteValue(
            value: ByteArray,
            options: Map<String, Variant<*>>,
        ) {
            handler.onWrite(spec.uuid, value, options)
        }

        override fun AcquireWrite(options: Map<String, Variant<*>>): TwoTuple<FileDescriptor, UInt16> =
            throw notSupported("AcquireWrite")

        override fun AcquireNotify(options: Map<String, Variant<*>>): TwoTuple<FileDescriptor, UInt16> =
            throw notSupported("AcquireNotify")

        override fun StartNotify() {
            notifying = true
            handler.onNotifying(spec.uuid, true)
        }

        override fun StopNotify() {
            notifying = false
            handler.onNotifying(spec.uuid, false)
        }

        override fun Confirm() {}

        override fun <A : Any?> Get(
            interfaceName: String,
            propertyName: String,
        ): A = propertyOf(properties(), propertyName)

        override fun <A : Any?> Set(
            interfaceName: String,
            propertyName: String,
            value: A,
        ): Unit = throw readOnly(propertyName)

        override fun GetAll(interfaceName: String): Map<String, Variant<*>> = properties()
    }

    inner class DescriptorObject(
        val path: String,
        private val characteristicPath: String,
        val uuid: Uuid,
    ) : GattDescriptor1,
        Properties {
        @Volatile private var value: ByteArray = byteArrayOf()

        fun properties(): Map<String, Variant<*>> =
            mapOf(
                "UUID" to Variant(uuid.toString()),
                "Characteristic" to Variant(DBusPath(characteristicPath)),
                "Flags" to Variant(arrayOf("read", "write")),
            )

        override fun getObjectPath(): String = path

        override fun ReadValue(options: Map<String, Variant<*>>): ByteArray = value

        override fun WriteValue(
            value: ByteArray,
            options: Map<String, Variant<*>>,
        ) {
            this.value = value.copyOf()
        }

        override fun <A : Any?> Get(
            interfaceName: String,
            propertyName: String,
        ): A = propertyOf(properties(), propertyName)

        override fun <A : Any?> Set(
            interfaceName: String,
            propertyName: String,
            value: A,
        ): Unit = throw readOnly(propertyName)

        override fun GetAll(interfaceName: String): Map<String, Variant<*>> = properties()
    }
}

internal fun characteristicFlags(spec: ServerCharacteristicSpec): List<String> =
    buildList {
        val properties = spec.properties
        val permissions = spec.permissions
        if (properties.read) add(if (permissions.readEncrypted) "encrypt-read" else "read")
        if (properties.write) add(if (permissions.writeEncrypted) "encrypt-write" else "write")
        if (properties.writeWithoutResponse) add("write-without-response")
        if (properties.notify) add("notify")
        if (properties.indicate) add("indicate")
    }

@Suppress("UNCHECKED_CAST")
private fun <A> propertyOf(
    properties: Map<String, Variant<*>>,
    name: String,
): A = properties[name]?.value as A

private fun readOnly(name: String): DBusExecutionException = org.bluez.Error.NotPermitted("Property $name is read-only")

private fun notSupported(method: String): DBusExecutionException =
    org.bluez.Error.NotSupported("$method is not supported")
