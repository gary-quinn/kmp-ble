package com.atruedev.kmpble.mesh

/**
 * Platform-specific factory for [MeshNetwork].
 *
 * Android and iOS provide full BLE Mesh support via core kmp-ble APIs.
 * JVM throws [MeshNotSupported] since there is no Bluetooth stack.
 */
public expect fun MeshNetwork(builder: MeshNetworkBuilder.() -> Unit): MeshNetwork
