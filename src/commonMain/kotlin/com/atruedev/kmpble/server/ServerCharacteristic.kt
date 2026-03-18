package com.atruedev.kmpble.server

import kotlin.uuid.Uuid

/**
 * A characteristic hosted by a GATT server.
 *
 * Unlike client-side [com.atruedev.kmpble.gatt.Characteristic] which uses object identity
 * (session-scoped from service discovery), server characteristics use structural equality
 * (UUID-based) because the server defines them at creation time.
 */
public data class ServerCharacteristic(
    val uuid: Uuid,
    val properties: Properties,
    val permissions: Permissions,
    val descriptors: List<ServerDescriptor> = emptyList(),
) {
    public data class Properties(
        val read: Boolean = false,
        val write: Boolean = false,
        val writeWithoutResponse: Boolean = false,
        val notify: Boolean = false,
        val indicate: Boolean = false,
    )

    public data class Permissions(
        val read: Boolean = false,
        val readEncrypted: Boolean = false,
        val write: Boolean = false,
        val writeEncrypted: Boolean = false,
    )
}

public data class ServerDescriptor(
    val uuid: Uuid,
)

public data class ServerService(
    val uuid: Uuid,
    val characteristics: List<ServerCharacteristic>,
)
