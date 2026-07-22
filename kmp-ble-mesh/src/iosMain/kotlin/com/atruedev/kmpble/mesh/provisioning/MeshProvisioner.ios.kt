package com.atruedev.kmpble.mesh.provisioning

import com.atruedev.kmpble.mesh.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * iOS implementation of [MeshProvisioner].
 *
 * Uses CoreBluetooth via the core kmp-ble APIs to discover unprovisioned
 * devices and perform PB-GATT provisioning.
 */
internal class IosMeshProvisioner : MeshProvisioner {
    override val scanEvents: Flow<UnprovisionedDevice> = emptyFlow()

    override suspend fun provision(
        device: UnprovisionedDevice,
        networkKey: NetworkKey,
        unicastAddress: MeshAddress.UnicastAddress,
        oobAuth: OobAuthentication,
    ): ProvisioningResult {
        throw MeshNotSupported("Provisioning via iOS is not yet implemented")
    }

    override fun close() {}
}

public actual fun MeshProvisioner(): MeshProvisioner = IosMeshProvisioner()
