package com.atruedev.kmpble.dfu.firmware

import com.atruedev.kmpble.dfu.DfuError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ZipReaderTest {

    private fun buildZipEntry(name: String, data: ByteArray): ByteArray {
        val nameBytes = name.encodeToByteArray()
        val header = ByteArray(30)

        // Local file header signature
        header[0] = 0x50; header[1] = 0x4B; header[2] = 0x03; header[3] = 0x04
        // Version needed
        header[4] = 0x14; header[5] = 0x00
        // General purpose flags
        header[6] = 0x00; header[7] = 0x00
        // Compression method (0 = stored)
        header[8] = 0x00; header[9] = 0x00
        // Last mod time/date
        header[10] = 0x00; header[11] = 0x00; header[12] = 0x00; header[13] = 0x00
        // CRC32 (0 for simplicity)
        header[14] = 0x00; header[15] = 0x00; header[16] = 0x00; header[17] = 0x00
        // Compressed size
        data.size.toLittleEndian().copyInto(header, 18)
        // Uncompressed size
        data.size.toLittleEndian().copyInto(header, 22)
        // Filename length
        nameBytes.size.toShortLE().copyInto(header, 26)
        // Extra field length
        header[28] = 0x00; header[29] = 0x00

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

    @Test
    fun readsSingleEntry() {
        val content = "Hello".encodeToByteArray()
        val zip = buildZipEntry("test.txt", content)

        val entries = ZipReader.readEntries(zip)
        assertEquals(1, entries.size)
        assertEquals("test.txt", entries[0].name)
        assertEquals("Hello", entries[0].data.decodeToString())
    }

    @Test
    fun readsMultipleEntries() {
        val entry1 = buildZipEntry("a.txt", "AAA".encodeToByteArray())
        val entry2 = buildZipEntry("b.dat", byteArrayOf(0x01, 0x02, 0x03))
        val zip = entry1 + entry2

        val entries = ZipReader.readEntries(zip)
        assertEquals(2, entries.size)
        assertEquals("a.txt", entries[0].name)
        assertEquals("b.dat", entries[1].name)
    }

    @Test
    fun emptyZipThrows() {
        assertFailsWith<DfuError.FirmwareParseError> {
            ZipReader.readEntries(byteArrayOf())
        }
    }

    @Test
    fun truncatedHeaderThrows() {
        assertFailsWith<DfuError.FirmwareParseError> {
            ZipReader.readEntries(byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(10))
        }
    }
}
