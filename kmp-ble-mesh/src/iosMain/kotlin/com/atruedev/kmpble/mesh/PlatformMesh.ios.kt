package com.atruedev.kmpble.mesh

import com.atruedev.kmpble.mesh.internal.MeshNetworkImpl
import com.atruedev.kmpble.mesh.provisioning.MeshProvisioner

/**
 * iOS implementation of [MeshNetwork] factory.
 *
 * Creates a fully functional [MeshNetworkImpl] backed by core kmp-ble APIs.
 * BLE Mesh on iOS uses the GATT Proxy bearer via CoreBluetooth.
 */
public actual fun MeshNetwork(
    builder: MeshNetworkBuilder.() -> Unit,
): MeshNetwork {
    val b = MeshNetworkBuilder().apply(builder)
    return MeshNetworkImpl(b)
}
