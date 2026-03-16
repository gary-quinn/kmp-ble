package com.atruedev.kmpble.gatt

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
public class Descriptor(
    public val characteristic: Characteristic,
    public val uuid: Uuid,
) {
    override fun toString(): String = "Descriptor(char=${characteristic.uuid}, uuid=$uuid)"
}
