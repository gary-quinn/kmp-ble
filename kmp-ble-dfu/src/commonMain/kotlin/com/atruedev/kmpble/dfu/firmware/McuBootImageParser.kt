package com.atruedev.kmpble.dfu.firmware

import com.atruedev.kmpble.dfu.DfuError
import com.atruedev.kmpble.dfu.internal.readIntLE
import com.atruedev.kmpble.dfu.internal.readShortLE

internal object McuBootImageParser {

    private const val HEADER_MAGIC = 0x96F3B83Du
    private const val HEADER_SIZE = 32

    fun parse(binData: ByteArray, imageIndex: Int): FirmwarePackage.McuBoot {
        if (binData.size < HEADER_SIZE) {
            throw DfuError.FirmwareParseError(
                "MCUboot image too small: ${binData.size} bytes, minimum $HEADER_SIZE",
            )
        }

        val magic = binData.readIntLE(0).toUInt()
        if (magic != HEADER_MAGIC) {
            throw DfuError.FirmwareParseError(
                "Invalid MCUboot magic: 0x${magic.toString(16)}, expected 0x${HEADER_MAGIC.toString(16)}",
            )
        }

        val headerSize = binData.readShortLE(8)
        val imageSize = binData.readIntLE(12)

        if (binData.size < headerSize + imageSize) {
            throw DfuError.FirmwareParseError(
                "MCUboot image truncated: expected ${headerSize + imageSize} bytes, got ${binData.size}",
            )
        }

        return FirmwarePackage.McuBoot(image = binData, imageIndex = imageIndex)
    }
}
