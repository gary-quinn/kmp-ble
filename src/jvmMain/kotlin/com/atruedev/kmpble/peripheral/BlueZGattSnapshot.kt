package com.atruedev.kmpble.peripheral

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
)

internal data class BlueZGattDescriptorSnapshot(
    val path: String,
    val uuid: String,
)
