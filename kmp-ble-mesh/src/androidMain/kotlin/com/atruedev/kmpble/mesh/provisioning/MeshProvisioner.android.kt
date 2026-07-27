package com.atruedev.kmpble.mesh.provisioning

import com.atruedev.kmpble.mesh.*
import com.atruedev.kmpble.scanner.Scanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Android implementation of [MeshProvisioner].
 *
 * Uses the core kmp-ble [Scanner] to discover unprovisioned device beacons
 * on the ADV bearer, and [com.atruedev.kmpble.peripheral.Peripheral] for
 * PB-GATT provisioning.
 */
internal class AndroidMeshProvisioner : MeshProvisioner {
    private var scanner: Scanner? = null

    override val scanEvents: Flow<UnprovisionedDevice> = emptyFlow()

    override suspend fun provision(
        device: UnprovisionedDevice,
        networkKey: NetworkKey,
        unicastAddress: MeshAddress.UnicastAddress,
        oobAuth: OobAuthentication,
    ): ProvisioningResult {
        throw MeshNotSupported("Provisioning via Android is not yet implemented")
    }

    override fun close() {
        scanner?.close()
    }
}

public actual fun MeshProvisioner(): MeshProvisioner = AndroidMeshProvisioner()
