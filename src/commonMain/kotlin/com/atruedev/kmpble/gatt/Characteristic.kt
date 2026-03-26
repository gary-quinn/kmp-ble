package com.atruedev.kmpble.gatt

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A discovered GATT characteristic on a remote peripheral.
 *
 * Instances are obtained from [DiscoveredService.characteristics] after service discovery
 * completes. Use [properties] to determine which operations (read, write, notify, etc.) the
 * characteristic supports before performing GATT operations.
 *
 * **Identity:** Characteristic uses reference equality, matching native platform behavior where
 * each discovered characteristic is a unique handle even if two share the same UUID.
 *
 * @property serviceUuid UUID of the parent GATT service.
 * @property uuid UUID of this characteristic.
 * @property properties Supported operations (read, write, notify, etc.).
 * @property descriptors Descriptors attached to this characteristic.
 */
@OptIn(ExperimentalUuidApi::class)
public class Characteristic(
    public val serviceUuid: Uuid,
    public val uuid: Uuid,
    public val properties: Properties,
    public val descriptors: List<Descriptor> = emptyList(),
) {
    /**
     * Flags indicating which GATT operations a characteristic supports.
     */
    public data class Properties(
        val read: Boolean = false,
        val write: Boolean = false,
        val writeWithoutResponse: Boolean = false,
        val signedWrite: Boolean = false,
        val notify: Boolean = false,
        val indicate: Boolean = false,
    ) {
        /** Human-readable property list, e.g. "read, notify". */
        public val displayName: String get() =
            buildList {
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
