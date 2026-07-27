package com.atruedev.kmpble.mesh.provisioning

import com.atruedev.kmpble.mesh.*

/**
 * Data distributed to a device during provisioning phase 5.
 *
 * Contains everything the device needs to become a mesh node:
 * network key, key index, IV Index, unicast address, and a unique device key.
 */
public data class ProvisioningData(
    val networkKey: NetworkKey,
    val keyIndex: KeyIndex,
    val ivIndex: IvIndex,
    val unicastAddress: MeshAddress.UnicastAddress,
    val deviceKey: DeviceKey,
)

/** Result of a completed provisioning operation. */
public data class ProvisioningResult(
    val node: MeshNode,
    val data: ProvisioningData,
)
