package com.atruedev.kmpble.backend

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.server.ServerCharacteristic
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Local GATT server for one backend. Core owns the read/write handlers, connection tracking,
 * and subscriptions; the transport publishes the attribute table and relays requests.
 */
@KmpBleBackendApi
public interface GattServerTransport : AutoCloseable {
    /**
     * `true` when the stack reports central connect/disconnect ([GattServerEvent.Connected] /
     * [GattServerEvent.Disconnected]). When `false`, core infers connections from requests and
     * evicts idle centrals.
     */
    public val reportsConnections: Boolean

    public fun setEventListener(listener: GattServerEventListener?)

    /** Publishes [services]. Fails with [com.atruedev.kmpble.server.ServerException.OpenFailed]. */
    public suspend fun open(services: List<ServerServiceSpec>)

    /** Answers a [GattServerEvent.ReadRequest] or [GattServerEvent.WriteRequest]. */
    public fun respond(
        requestId: Long,
        status: GattStatus,
        value: ByteArray?,
    )

    /**
     * Sends a notification ([indicate] false) or indication to [device], or to every subscribed
     * central when [device] is `null`. Suspends while the stack's transmit queue is full.
     */
    @OptIn(ExperimentalUuidApi::class)
    public suspend fun notify(
        characteristic: Uuid,
        device: Identifier?,
        data: ByteArray,
        indicate: Boolean,
    )

    override fun close()
}

@OptIn(ExperimentalUuidApi::class)
@KmpBleBackendApi
public class ServerServiceSpec(
    public val uuid: Uuid,
    public val characteristics: List<ServerCharacteristicSpec>,
)

@OptIn(ExperimentalUuidApi::class)
@KmpBleBackendApi
public class ServerCharacteristicSpec(
    public val uuid: Uuid,
    public val properties: ServerCharacteristic.Properties,
    public val permissions: ServerCharacteristic.Permissions,
    public val descriptors: List<Uuid>,
)

@KmpBleBackendApi
public fun interface GattServerEventListener {
    public fun onEvent(event: GattServerEvent)
}

@OptIn(ExperimentalUuidApi::class)
@KmpBleBackendApi
public sealed interface GattServerEvent {
    public val device: Identifier

    public class Connected(
        override val device: Identifier,
        public val name: String? = null,
    ) : GattServerEvent

    public class Disconnected(
        override val device: Identifier,
    ) : GattServerEvent

    public class ReadRequest(
        public val requestId: Long,
        override val device: Identifier,
        public val characteristic: Uuid,
        public val offset: Int,
    ) : GattServerEvent

    /**
     * One or more attribute writes answered by a single [GattServerTransport.respond].
     * Fragments of a long write arrive in [writes] with increasing offsets.
     */
    public class WriteRequest(
        public val requestId: Long,
        override val device: Identifier,
        public val writes: List<AttributeWrite>,
        public val responseNeeded: Boolean,
    ) : GattServerEvent

    public class Subscribed(
        override val device: Identifier,
        public val characteristic: Uuid,
    ) : GattServerEvent

    public class Unsubscribed(
        override val device: Identifier,
        public val characteristic: Uuid,
    ) : GattServerEvent
}

@OptIn(ExperimentalUuidApi::class)
@KmpBleBackendApi
public class AttributeWrite(
    public val characteristic: Uuid,
    public val offset: Int,
    public val value: ByteArray,
)
