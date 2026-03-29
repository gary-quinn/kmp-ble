package com.atruedev.kmpble.sample.dfu

sealed interface DfuState {
    data object Idle : DfuState

    data class Ready(
        val firmware: FirmwareInfo,
    ) : DfuState

    data class InProgress(
        val percent: Int,
    ) : DfuState

    data object Completed : DfuState

    data class Failed(
        val message: String,
    ) : DfuState
}

data class FirmwareInfo(
    val name: String,
    val sizeBytes: Int,
    val type: FirmwareType,
)

enum class FirmwareType {
    NORDIC,
    MCUBOOT,
    ESP_OTA,
}
