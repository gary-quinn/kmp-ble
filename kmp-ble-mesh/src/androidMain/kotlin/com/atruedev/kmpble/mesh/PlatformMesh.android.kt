package com.atruedev.kmpble.mesh

import com.atruedev.kmpble.mesh.internal.MeshNetworkImpl
import com.atruedev.kmpble.mesh.provisioning.MeshProvisioner

/**
 * Android implementation of [MeshNetwork] factory.
 *
 * Creates a fully functional [MeshNetworkImpl] backed by core kmp-ble APIs.
 * BLE Mesh on Android uses the GATT Proxy bearer via [Peripheral.connect()]
 * and optionally PB-ADV via [Scanner] for unprovisioned device discovery.
 */
public actual fun MeshNetwork(
    builder: MeshNetworkBuilder.() -> Unit,
): MeshNetwork {
    val b = MeshNetworkBuilder().apply(builder)
    return MeshNetworkImpl(b)
}
