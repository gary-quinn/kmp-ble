package com.atruedev.kmpble.dfu.firmware

import com.atruedev.kmpble.dfu.DfuError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class McuBootImageParserTest {

    @Test
    fun validMcuBootHeader() {
        val image = buildMcuBootImage(imageSize = 1024)
        val firmware = FirmwarePackage.McuBoot.fromBinBytes(image)
        assertEquals(0, firmware.imageIndex)
        assertEquals(image.size.toLong(), firmware.totalBytes)
    }

    @Test
    fun customImageIndex() {
        val image = buildMcuBootImage(imageSize = 512)
        val firmware = FirmwarePackage.McuBoot.fromBinBytes(image, imageIndex = 1)
        assertEquals(1, firmware.imageIndex)
    }

    @Test
    fun tooSmallImage() {
        assertFailsWith<DfuError.FirmwareParseError> {
            FirmwarePackage.McuBoot.fromBinBytes(ByteArray(16))
        }
    }

    @Test
    fun invalidMagic() {
        val image = ByteArray(64)
        // wrong magic bytes
        image[0] = 0xFF.toByte()
        assertFailsWith<DfuError.FirmwareParseError> {
            FirmwarePackage.McuBoot.fromBinBytes(image)
        }
    }

    @Test
    fun truncatedImage() {
        // header says imageSize=1024, headerSize=32, but actual data is only 64 bytes
        val image = buildMcuBootImage(imageSize = 1024).copyOfRange(0, 64)
        assertFailsWith<DfuError.FirmwareParseError> {
            FirmwarePackage.McuBoot.fromBinBytes(image)
        }
    }

    private fun buildMcuBootImage(headerSize: Int = 32, imageSize: Int = 256): ByteArray {
        val totalSize = headerSize + imageSize
        val data = ByteArray(totalSize)

        // MCUboot magic: 0x96F3B83D (little-endian)
        data[0] = 0x3D; data[1] = 0xB8.toByte(); data[2] = 0xF3.toByte(); data[3] = 0x96.toByte()

        // header size at offset 8 (little-endian 16-bit)
        data[8] = (headerSize and 0xFF).toByte()
        data[9] = ((headerSize shr 8) and 0xFF).toByte()

        // image size at offset 12 (little-endian 32-bit)
        data[12] = (imageSize and 0xFF).toByte()
        data[13] = ((imageSize shr 8) and 0xFF).toByte()
        data[14] = ((imageSize shr 16) and 0xFF).toByte()
        data[15] = ((imageSize shr 24) and 0xFF).toByte()

        return data
    }
}
