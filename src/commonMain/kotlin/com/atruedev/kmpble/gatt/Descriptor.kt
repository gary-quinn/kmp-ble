package com.atruedev.kmpble.gatt

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A discovered GATT descriptor on a remote peripheral.
 *
 * Descriptors provide metadata about their parent [characteristic], such as the Client
 * Characteristic Configuration Descriptor (CCCD) used to enable notifications/indications.
 *
 * **Identity:** Uses reference equality, matching native platform behavior.
 *
 * @property characteristic The parent characteristic this descriptor belongs to.
 * @property uuid UUID of this descriptor.
 */
@OptIn(ExperimentalUuidApi::class)
public class Descriptor(
    public val characteristic: Characteristic,
    public val uuid: Uuid,
) {
    override fun toString(): String = "Descriptor(char=${characteristic.uuid}, uuid=$uuid)"
}
