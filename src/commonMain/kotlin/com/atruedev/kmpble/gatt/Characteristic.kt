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
    ) {
        /** Human-readable property list, e.g. "read, notify". */
        public val displayName: String get() = buildList {
            if (read) add("read")
            if (write) add("write")
            if (writeWithoutResponse) add("writeNoResp")
            if (signedWrite) add("signedWrite")
            if (notify) add("notify")
            if (indicate) add("indicate")
        }.joinToString(", ")
    }

    override fun toString(): String = "Characteristic(service=$serviceUuid, uuid=$uuid)"
}
