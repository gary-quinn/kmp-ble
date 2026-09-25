package com.atruedev.kmpble.mesh

import com.atruedev.kmpble.mesh.internal.MeshNetworkImpl

/**
 * JVM implementation of [MeshNetwork] factory.
 *
 * Creates a [MeshNetworkImpl] backed by core kmp-ble APIs, so the GATT Proxy bearer runs over
 * whichever JVM backend is on the classpath (`kmp-ble-bluez` on Linux, `kmp-ble-macos` on
 * macOS arm64). Connecting throws [UnsupportedOperationException] when no backend applies.
 */
public actual fun MeshNetwork(
    builder: MeshNetworkBuilder.() -> Unit,
): MeshNetwork {
    val b = MeshNetworkBuilder().apply(builder)
    return MeshNetworkImpl(b)
}
