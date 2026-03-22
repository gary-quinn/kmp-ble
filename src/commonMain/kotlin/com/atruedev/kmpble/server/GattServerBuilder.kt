package com.atruedev.kmpble.server

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.scanner.uuidFrom
import kotlin.uuid.Uuid

/**
 * Restricts DSL scope so that, e.g., `service {}` cannot be called
 * inside a `characteristic {}` block.
 */
@DslMarker
public annotation class GattServerDsl

/**
 * Factory function to create a [GattServer] with DSL configuration.
 *
 * ```kotlin
 * val server = GattServer {
 *     service(myServiceUuid) {
 *         characteristic(commandUuid) {
 *             properties { write = true }
 *             permissions { write = true }
 *             onWrite { device, data, responseNeeded ->
 *                 processCommand(data.toByteArray())
 *                 if (responseNeeded) GattStatus.Success else null
 *             }
 *         }
 *     }
 * }
 * ```
 */
public expect fun GattServer(builder: GattServerBuilder.() -> Unit): GattServer

@GattServerDsl
public class GattServerBuilder {
    internal val services = mutableListOf<ServiceDefinition>()
    private val serviceUuids = mutableSetOf<Uuid>()

    public fun service(
        uuid: Uuid,
        block: ServiceBuilder.() -> Unit,
    ) {
        require(serviceUuids.add(uuid)) { "Duplicate service UUID: $uuid" }
        val builder = ServiceBuilder(uuid)
        builder.block()
        services.add(builder.build())
    }

    /** Convenience: string UUID shorthand for standard 16-bit BLE UUIDs. */
    public fun service(
        uuid: String,
        block: ServiceBuilder.() -> Unit,
    ) {
        service(uuidFrom(uuid), block)
    }
}

@GattServerDsl
public class ServiceBuilder(private val uuid: Uuid) {
    internal val characteristics = mutableListOf<CharacteristicDefinition>()

    public fun characteristic(
        uuid: Uuid,
        block: CharacteristicBuilder.() -> Unit,
    ) {
        val builder = CharacteristicBuilder(uuid)
        builder.block()
        characteristics.add(builder.build())
    }

    public fun characteristic(
        uuid: String,
        block: CharacteristicBuilder.() -> Unit,
    ) {
        characteristic(uuidFrom(uuid), block)
    }

    internal fun build(): ServiceDefinition {
        val uuids = characteristics.map { it.uuid }
        val duplicates = uuids.groupBy { it }.filter { it.value.size > 1 }.keys
        require(duplicates.isEmpty()) {
            "Duplicate characteristic UUIDs in service $uuid: $duplicates"
        }
        return ServiceDefinition(uuid, characteristics.toList())
    }
}

@GattServerDsl
public class CharacteristicBuilder(private val uuid: Uuid) {
    private var props = ServerCharacteristic.Properties()
    private var perms = ServerCharacteristic.Permissions()
    private var readHandler: (suspend (device: Identifier) -> BleData)? = null
    private var writeHandler: (
        suspend (
            device: Identifier,
            data: BleData,
            responseNeeded: Boolean,
        ) -> GattStatus?
    )? = null
    private val descriptors = mutableListOf<ServerDescriptor>()

    public fun properties(block: PropertiesBuilder.() -> Unit) {
        val builder = PropertiesBuilder()
        builder.block()
        props = builder.build()
    }

    public fun permissions(block: PermissionsBuilder.() -> Unit) {
        val builder = PermissionsBuilder()
        builder.block()
        perms = builder.build()
    }

    /**
     * Handler called when a remote device reads this characteristic.
     *
     * @return The data to send to the client
     */
    public fun onRead(handler: suspend (device: Identifier) -> BleData) {
        readHandler = handler
    }

    /**
     * Handler called when a remote device writes to this characteristic.
     *
     * @param handler Receives the writing device, the data, and whether a response is needed.
     *                Return [GattStatus.Success] to acknowledge, or another status to reject.
     *                Return null if no response is needed (write-without-response).
     */
    public fun onWrite(handler: suspend (device: Identifier, data: BleData, responseNeeded: Boolean) -> GattStatus?) {
        writeHandler = handler
    }

    public fun descriptor(uuid: Uuid) {
        descriptors.add(ServerDescriptor(uuid))
    }

    internal fun build(): CharacteristicDefinition {
        require(!props.read || readHandler != null) {
            "Characteristic $uuid has read property but no onRead handler"
        }
        require(!(props.write || props.writeWithoutResponse) || writeHandler != null) {
            "Characteristic $uuid has write property but no onWrite handler"
        }
        return CharacteristicDefinition(
            uuid = uuid,
            properties = props,
            permissions = perms,
            readHandler = readHandler,
            writeHandler = writeHandler,
            descriptors = descriptors.toList(),
        )
    }
}

@GattServerDsl
public class PropertiesBuilder {
    public var read: Boolean = false
    public var write: Boolean = false
    public var writeWithoutResponse: Boolean = false
    public var notify: Boolean = false
    public var indicate: Boolean = false

    internal fun build(): ServerCharacteristic.Properties =
        ServerCharacteristic.Properties(read, write, writeWithoutResponse, notify, indicate)
}

@GattServerDsl
public class PermissionsBuilder {
    public var read: Boolean = false
    public var readEncrypted: Boolean = false
    public var write: Boolean = false
    public var writeEncrypted: Boolean = false

    internal fun build(): ServerCharacteristic.Permissions =
        ServerCharacteristic.Permissions(read, readEncrypted, write, writeEncrypted)
}

/** Internal representation of a configured service. */
internal class ServiceDefinition(
    val uuid: Uuid,
    val characteristics: List<CharacteristicDefinition>,
)

/** Internal representation of a configured characteristic with handlers. */
internal class CharacteristicDefinition(
    val uuid: Uuid,
    val properties: ServerCharacteristic.Properties,
    val permissions: ServerCharacteristic.Permissions,
    val readHandler: (suspend (Identifier) -> BleData)?,
    val writeHandler: (suspend (Identifier, BleData, Boolean) -> GattStatus?)?,
    val descriptors: List<ServerDescriptor>,
)
