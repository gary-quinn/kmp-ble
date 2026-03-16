package com.atruedev.kmpble.gatt

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
public class DiscoveredService(
    public val uuid: Uuid,
    public val characteristics: List<Characteristic>,
) {
    override fun toString(): String = "DiscoveredService(uuid=$uuid, chars=${characteristics.size})"
}
