package com.atruedev.kmpble.gatt

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A GATT service discovered on a remote peripheral after connection.
 *
 * Contains the service UUID and all [characteristics] found within it. Obtained from
 * [com.atruedev.kmpble.peripheral.Peripheral.services] after service discovery completes.
 *
 * @property uuid UUID of this GATT service.
 * @property characteristics Characteristics belonging to this service.
 */
@OptIn(ExperimentalUuidApi::class)
public class DiscoveredService(
    public val uuid: Uuid,
    public val characteristics: List<Characteristic>,
) {
    override fun toString(): String = "DiscoveredService(uuid=$uuid, chars=${characteristics.size})"
}
