package com.atruedev.kmpble.gatt

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
public class Characteristic(
    public val serviceUuid: Uuid,
    public val uuid: Uuid,
    public val properties: Properties,
    public val descriptors: List<Descriptor> = emptyList(),
) {
    public data class Properties(
        val read: Boolean = false,
        val write: Boolean = false,
        val writeWithoutResponse: Boolean = false,
        val signedWrite: Boolean = false,
        val notify: Boolean = false,
        val indicate: Boolean = false,
    )

    override fun toString(): String = "Characteristic(service=$serviceUuid, uuid=$uuid)"
}
