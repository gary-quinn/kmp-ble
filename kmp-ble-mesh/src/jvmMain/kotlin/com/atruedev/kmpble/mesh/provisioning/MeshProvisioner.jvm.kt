package com.atruedev.kmpble.mesh.provisioning

import com.atruedev.kmpble.mesh.MeshAddress
import com.atruedev.kmpble.mesh.MeshNotSupported
import com.atruedev.kmpble.mesh.NetworkKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * JVM implementation of [MeshProvisioner], matching Android and iOS: unprovisioned-device
 * discovery and PB-GATT provisioning are not implemented yet on any platform.
 */
internal class JvmMeshProvisioner : MeshProvisioner {
    override val scanEvents: Flow<UnprovisionedDevice> = emptyFlow()

    override suspend fun provision(
        device: UnprovisionedDevice,
        networkKey: NetworkKey,
        unicastAddress: MeshAddress.UnicastAddress,
        oobAuth: OobAuthentication,
    ): ProvisioningResult = throw MeshNotSupported("Provisioning via JVM is not yet implemented")

    override fun close() {}
}

public actual fun MeshProvisioner(): MeshProvisioner = JvmMeshProvisioner()
