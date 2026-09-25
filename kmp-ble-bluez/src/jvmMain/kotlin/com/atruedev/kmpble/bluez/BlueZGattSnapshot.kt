package com.atruedev.kmpble.bluez

internal data class BlueZGattServiceSnapshot(
    val path: String,
    val uuid: String,
    val characteristics: List<BlueZGattCharacteristicSnapshot>,
)

internal data class BlueZGattCharacteristicSnapshot(
    val path: String,
    val uuid: String,
    val flags: List<String>,
    val descriptors: List<BlueZGattDescriptorSnapshot>,
    val mtu: Int? = null,
)

internal data class BlueZGattDescriptorSnapshot(
    val path: String,
    val uuid: String,
)
