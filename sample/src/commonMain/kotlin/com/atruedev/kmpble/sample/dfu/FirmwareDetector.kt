package com.atruedev.kmpble.sample.dfu

import com.atruedev.kmpble.dfu.firmware.FirmwarePackage

object FirmwareDetector {
    internal fun detect(
        name: String,
        bytes: ByteArray,
    ): DetectionResult {
        if (name.endsWith(".zip", ignoreCase = true)) {
            return DetectionResult(
                firmware = FirmwarePackage.Nordic.fromZipBytes(bytes),
                info = FirmwareInfo(name = name, sizeBytes = bytes.size, type = FirmwareType.NORDIC),
            )
        }

        if (isMcuBootImage(bytes)) {
            return DetectionResult(
                firmware = FirmwarePackage.McuBoot.fromBinBytes(bytes),
                info = FirmwareInfo(name = name, sizeBytes = bytes.size, type = FirmwareType.MCUBOOT),
            )
        }

        return DetectionResult(
            firmware = FirmwarePackage.EspOta.fromBinBytes(bytes),
            info = FirmwareInfo(name = name, sizeBytes = bytes.size, type = FirmwareType.ESP_OTA),
        )
    }
}

internal data class DetectionResult(
    val firmware: FirmwarePackage,
    val info: FirmwareInfo,
)

private const val MCUBOOT_MAGIC_BYTE_0: Byte = 0x3D
private const val MCUBOOT_MAGIC_BYTE_1: Byte = 0xB8.toByte()
private const val MCUBOOT_MAGIC_BYTE_2: Byte = 0xF3.toByte()
private const val MCUBOOT_MAGIC_BYTE_3: Byte = 0x96.toByte()
private const val MCUBOOT_HEADER_SIZE = 4

private fun isMcuBootImage(bytes: ByteArray): Boolean =
    bytes.size >= MCUBOOT_HEADER_SIZE &&
        bytes[0] == MCUBOOT_MAGIC_BYTE_0 &&
        bytes[1] == MCUBOOT_MAGIC_BYTE_1 &&
        bytes[2] == MCUBOOT_MAGIC_BYTE_2 &&
        bytes[3] == MCUBOOT_MAGIC_BYTE_3
