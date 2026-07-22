package com.atruedev.kmpble.mesh

/**
 * JVM implementation -- BLE Mesh is not supported on JVM.
 */
public actual fun MeshNetwork(
    builder: MeshNetworkBuilder.() -> Unit,
): MeshNetwork {
    throw MeshNotSupported()
}
