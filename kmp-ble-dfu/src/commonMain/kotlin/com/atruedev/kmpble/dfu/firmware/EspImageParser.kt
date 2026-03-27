package com.atruedev.kmpble.dfu.firmware

import com.atruedev.kmpble.dfu.DfuError

internal object EspImageParser {

    private const val ESP_IMAGE_MAGIC: Byte = 0xE9.toByte()
    private const val MIN_HEADER_SIZE = 24

    fun parse(binData: ByteArray): FirmwarePackage.EspOta {
        if (binData.size < MIN_HEADER_SIZE) {
            throw DfuError.FirmwareParseError(
                "ESP image too small: ${binData.size} bytes, minimum $MIN_HEADER_SIZE",
            )
        }

        if (binData[0] != ESP_IMAGE_MAGIC) {
            throw DfuError.FirmwareParseError(
                "Invalid ESP image magic: 0x${(binData[0].toInt() and 0xFF).toString(16)}, expected 0xE9",
            )
        }

        return FirmwarePackage.EspOta(firmware = binData)
    }
}
