package com.atruedev.kmpble.dfu.firmware

import com.atruedev.kmpble.dfu.DfuError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EspImageParserTest {

    @Test
    fun validEspImage() {
        val image = buildEspImage()
        val firmware = FirmwarePackage.EspOta.fromBinBytes(image)
        assertEquals(image.size.toLong(), firmware.totalBytes)
    }

    @Test
    fun tooSmallImage() {
        assertFailsWith<DfuError.FirmwareParseError> {
            FirmwarePackage.EspOta.fromBinBytes(ByteArray(10))
        }
    }

    @Test
    fun invalidMagic() {
        val image = ByteArray(64)
        image[0] = 0x00 // wrong magic (should be 0xE9)
        assertFailsWith<DfuError.FirmwareParseError> {
            FirmwarePackage.EspOta.fromBinBytes(image)
        }
    }

    @Test
    fun minimumSizeImage() {
        val image = ByteArray(24)
        image[0] = 0xE9.toByte()
        val firmware = FirmwarePackage.EspOta.fromBinBytes(image)
        assertEquals(24L, firmware.totalBytes)
    }

    private fun buildEspImage(size: Int = 256): ByteArray {
        val data = ByteArray(size)
        data[0] = 0xE9.toByte() // ESP-IDF magic byte
        data[1] = 0x02          // segment count
        return data
    }
}
