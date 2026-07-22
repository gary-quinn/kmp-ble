package com.atruedev.kmpble.mesh.proxy

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * UUIDs for the BLE Mesh Proxy Service and its characteristics.
 *
 * The Mesh Proxy Service (UUID 1828) is the GATT service through which
 * a smartphone participates in a mesh network. The proxy node bridges
 * between the GATT bearer and the ADV bearer.
 */
@OptIn(ExperimentalUuidApi::class)
public object MeshProxyService {
    /** Mesh Proxy Service UUID (SIG-assigned 16-bit UUID). */
    public val SERVICE_UUID: Uuid = Uuid.parse("00001828-0000-1000-8000-00805F9B34FB")

    /** Mesh Proxy Data In characteristic -- smartphone writes PDUs to send. */
    public val DATA_IN_UUID: Uuid = Uuid.parse("00002ADD-0000-1000-8000-00805F9B34FB")

    /** Mesh Proxy Data Out characteristic -- smartphone receives PDUs via notification. */
    public val DATA_OUT_UUID: Uuid = Uuid.parse("00002ADE-0000-1000-8000-00805F9B34FB")

    /** Mesh Provisioning Service UUID (for PB-GATT provisioning). */
    public val PROVISIONING_SERVICE_UUID: Uuid = Uuid.parse("00001827-0000-1000-8000-00805F9B34FB")

    /** Provisioning Data In characteristic. */
    public val PROVISIONING_DATA_IN_UUID: Uuid = Uuid.parse("00002ADB-0000-1000-8000-00805F9B34FB")

    /** Provisioning Data Out characteristic. */
    public val PROVISIONING_DATA_OUT_UUID: Uuid = Uuid.parse("00002ADC-0000-1000-8000-00805F9B34FB")
}
