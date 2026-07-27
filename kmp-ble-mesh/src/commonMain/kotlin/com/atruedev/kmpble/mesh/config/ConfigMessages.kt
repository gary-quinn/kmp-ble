package com.atruedev.kmpble.mesh.config

import com.atruedev.kmpble.mesh.MeshOpcode

/**
 * Configuration message opcodes as defined by the BLE Mesh specification.
 *
 * Configuration messages use opcodes in the range 0x8000-0x803F and are
 * encrypted with the Device Key rather than an Application Key.
 */
internal object ConfigOpcodes {
    // AppKey management (0x8000-0x8003)
    val CONFIG_APPKEY_ADD: MeshOpcode = MeshOpcode(0x8000u)
    val CONFIG_APPKEY_DELETE: MeshOpcode = MeshOpcode(0x8001u)
    val CONFIG_APPKEY_GET: MeshOpcode = MeshOpcode(0x8002u)
    val CONFIG_APPKEY_LIST: MeshOpcode = MeshOpcode(0x8003u)
    val CONFIG_APPKEY_STATUS: MeshOpcode = MeshOpcode(0x8004u)

    // Model-AppKey binding (0x803D-0x803E)
    val CONFIG_MODEL_APP_BIND: MeshOpcode = MeshOpcode(0x803Du)
    val CONFIG_MODEL_APP_UNBIND: MeshOpcode = MeshOpcode(0x803Eu)
    val CONFIG_MODEL_APP_STATUS: MeshOpcode = MeshOpcode(0x803Fu)

    // Publication (0x8018-0x801F)
    val CONFIG_MODEL_PUBLICATION_SET: MeshOpcode = MeshOpcode(0x8018u)
    val CONFIG_MODEL_PUBLICATION_GET: MeshOpcode = MeshOpcode(0x8019u)
    val CONFIG_MODEL_PUBLICATION_STATUS: MeshOpcode = MeshOpcode(0x801Au)

    // Subscription (0x801B-0x8021)
    val CONFIG_MODEL_SUBSCRIPTION_ADD: MeshOpcode = MeshOpcode(0x801Bu)
    val CONFIG_MODEL_SUBSCRIPTION_DELETE: MeshOpcode = MeshOpcode(0x801Cu)
    val CONFIG_MODEL_SUBSCRIPTION_OVERWRITE: MeshOpcode = MeshOpcode(0x801Du)
    val CONFIG_MODEL_SUBSCRIPTION_STATUS: MeshOpcode = MeshOpcode(0x801Fu)

    // Node features (0x8026-0x802D)
    val CONFIG_RELAY_SET: MeshOpcode = MeshOpcode(0x8027u)
    val CONFIG_RELAY_STATUS: MeshOpcode = MeshOpcode(0x8028u)
    val CONFIG_PROXY_SET: MeshOpcode = MeshOpcode(0x8029u)
    val CONFIG_PROXY_STATUS: MeshOpcode = MeshOpcode(0x802Au)
    val CONFIG_FRIEND_SET: MeshOpcode = MeshOpcode(0x802Bu)
    val CONFIG_FRIEND_STATUS: MeshOpcode = MeshOpcode(0x802Cu)

    // Composition data (0x8008-0x8009)
    val CONFIG_COMPOSITION_DATA_GET: MeshOpcode = MeshOpcode(0x8008u)
    val CONFIG_COMPOSITION_DATA_STATUS: MeshOpcode = MeshOpcode(0x8009u)

    // Default TTL (0x800C-0x800D)
    val CONFIG_DEFAULT_TTL_SET: MeshOpcode = MeshOpcode(0x800Cu)
    val CONFIG_DEFAULT_TTL_STATUS: MeshOpcode = MeshOpcode(0x800Du)
}

/** Status codes returned by the Configuration Server. */
internal object ConfigStatusCodes {
    const val SUCCESS: UByte = 0x00u
    const val INVALID_ADDRESS: UByte = 0x01u
    const val INVALID_MODEL: UByte = 0x02u
    const val INVALID_APPKEY_INDEX: UByte = 0x03u
    const val INVALID_NETKEY_INDEX: UByte = 0x04u
    const val INSUFFICIENT_RESOURCES: UByte = 0x05u
    const val KEY_INDEX_ALREADY_STORED: UByte = 0x06u
    const val INVALID_PUBLISH_PARAMETERS: UByte = 0x07u
    const val NOT_A_SUBSCRIBE_MODEL: UByte = 0x08u
    const val STORAGE_FAILURE: UByte = 0x09u
    const val FEATURE_NOT_SUPPORTED: UByte = 0x0Au
    const val CANNOT_UPDATE: UByte = 0x0Bu
    const val CANNOT_REMOVE: UByte = 0x0Cu
    const val CANNOT_BIND: UByte = 0x0Du
    const val TEMPORARILY_UNABLE: UByte = 0x0Eu
    const val CANNOT_SET: UByte = 0x0Fu
    const val UNSPECIFIED_ERROR: UByte = 0x10u
    const val INVALID_BINDING: UByte = 0x11u
}
