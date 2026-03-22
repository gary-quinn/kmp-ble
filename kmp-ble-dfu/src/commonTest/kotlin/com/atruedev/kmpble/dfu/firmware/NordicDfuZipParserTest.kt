package com.atruedev.kmpble.dfu.firmware

import com.atruedev.kmpble.dfu.DfuError
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NordicDfuZipParserTest {

    private fun buildZipEntry(name: String, data: ByteArray): ByteArray {
        val nameBytes = name.encodeToByteArray()
        val header = ByteArray(30)
        header[0] = 0x50; header[1] = 0x4B; header[2] = 0x03; header[3] = 0x04
        header[4] = 0x14; header[5] = 0x00
        data.size.toLittleEndian().copyInto(header, 18)
        data.size.toLittleEndian().copyInto(header, 22)
        nameBytes.size.toShortLE().copyInto(header, 26)
        return header + nameBytes + data
    }

    private fun Int.toLittleEndian(): ByteArray = byteArrayOf(
        (this and 0xFF).toByte(),
        ((this shr 8) and 0xFF).toByte(),
        ((this shr 16) and 0xFF).toByte(),
        ((this shr 24) and 0xFF).toByte(),
    )

    private fun Int.toShortLE(): ByteArray = byteArrayOf(
        (this and 0xFF).toByte(),
        ((this shr 8) and 0xFF).toByte(),
    )

    private fun buildDfuZip(
        manifestJson: String,
        datContent: ByteArray = byteArrayOf(0x01, 0x02),
        binContent: ByteArray = byteArrayOf(0x03, 0x04, 0x05),
    ): ByteArray {
        val manifest = buildZipEntry("manifest.json", manifestJson.encodeToByteArray())
        val dat = buildZipEntry("app.dat", datContent)
        val bin = buildZipEntry("app.bin", binContent)
        return manifest + dat + bin
    }

    @Test
    fun parsesValidDfuZip() {
        val manifest = """
            {
                "manifest": {
                    "application": {
                        "bin_file": "app.bin",
                        "dat_file": "app.dat"
                    }
                }
            }
        """.trimIndent()

        val datContent = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
        val binContent = byteArrayOf(0x01, 0x02, 0x03, 0x04)

        val zip = buildDfuZip(manifest, datContent, binContent)
        val pkg = NordicDfuZipParser.parse(zip)

        assertContentEquals(datContent, pkg.initPacket)
        assertContentEquals(binContent, pkg.firmware)
    }

    @Test
    fun missingManifestThrows() {
        val zip = buildZipEntry("app.dat", byteArrayOf(0x01)) +
            buildZipEntry("app.bin", byteArrayOf(0x02))

        assertFailsWith<DfuError.FirmwareParseError> {
            NordicDfuZipParser.parse(zip)
        }
    }

    @Test
    fun missingBinFileThrows() {
        val manifest = """{"manifest":{"application":{"bin_file":"missing.bin","dat_file":"app.dat"}}}"""
        val zip = buildZipEntry("manifest.json", manifest.encodeToByteArray()) +
            buildZipEntry("app.dat", byteArrayOf(0x01))

        assertFailsWith<DfuError.FirmwareParseError> {
            NordicDfuZipParser.parse(zip)
        }
    }

    @Test
    fun missingDatFileThrows() {
        val manifest = """{"manifest":{"application":{"bin_file":"app.bin","dat_file":"missing.dat"}}}"""
        val zip = buildZipEntry("manifest.json", manifest.encodeToByteArray()) +
            buildZipEntry("app.bin", byteArrayOf(0x01))

        assertFailsWith<DfuError.FirmwareParseError> {
            NordicDfuZipParser.parse(zip)
        }
    }

    @Test
    fun totalBytesIsCorrect() {
        val manifest = """{"manifest":{"application":{"bin_file":"app.bin","dat_file":"app.dat"}}}"""
        val dat = ByteArray(100)
        val bin = ByteArray(5000)
        val zip = buildDfuZip(manifest, dat, bin)

        val pkg = NordicDfuZipParser.parse(zip)
        assertEquals(5100L, pkg.totalBytes)
    }
}
