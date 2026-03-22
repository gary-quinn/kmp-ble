package com.atruedev.kmpble.profiles.parsing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BleByteReaderTest {

    @Test
    fun readUInt8() {
        val reader = BleByteReader(byteArrayOf(0xFF.toByte(), 0x00))
        assertEquals(255, reader.readUInt8())
        assertEquals(0, reader.readUInt8())
    }

    @Test
    fun readInt8() {
        val reader = BleByteReader(byteArrayOf(0xFF.toByte(), 0x7F))
        assertEquals(-1, reader.readInt8())
        assertEquals(127, reader.readInt8())
    }

    @Test
    fun readUInt16LittleEndian() {
        // 0x0180 little-endian = 0x80, 0x01 → 384
        val reader = BleByteReader(byteArrayOf(0x80.toByte(), 0x01))
        assertEquals(384, reader.readUInt16())
    }

    @Test
    fun readInt16Signed() {
        // 0xFFFF little-endian → -1
        val reader = BleByteReader(byteArrayOf(0xFF.toByte(), 0xFF.toByte()))
        assertEquals(-1, reader.readInt16())
    }

    @Test
    fun readUInt32LittleEndian() {
        // 0x01020304 in little-endian
        val reader = BleByteReader(byteArrayOf(0x04, 0x03, 0x02, 0x01))
        assertEquals(0x01020304L, reader.readUInt32())
    }

    @Test
    fun readUtf8() {
        val reader = BleByteReader("Hello".encodeToByteArray())
        assertEquals("Hello", reader.readUtf8(5))
    }

    @Test
    fun readDateTime() {
        // year=2024 (0xE8, 0x07), month=3, day=15, hour=10, min=30, sec=45
        val reader = BleByteReader(byteArrayOf(0xE8.toByte(), 0x07, 3, 15, 10, 30, 45))
        val dt = reader.readDateTime()
        assertEquals(2024, dt.year)
        assertEquals(3, dt.month)
        assertEquals(15, dt.day)
        assertEquals(10, dt.hours)
        assertEquals(30, dt.minutes)
        assertEquals(45, dt.seconds)
    }

    @Test
    fun hasRemainingTracksOffset() {
        val reader = BleByteReader(byteArrayOf(1, 2, 3))
        assertTrue(reader.hasRemaining(3))
        assertFalse(reader.hasRemaining(4))
        reader.readUInt8()
        assertTrue(reader.hasRemaining(2))
        assertFalse(reader.hasRemaining(3))
    }

    @Test
    fun skipAdvancesOffset() {
        val reader = BleByteReader(byteArrayOf(1, 2, 3, 4))
        reader.skip(2)
        assertEquals(2, reader.offset)
        assertEquals(3, reader.readUInt8())
    }

    @Test
    fun readBeyondEndThrows() {
        val reader = BleByteReader(byteArrayOf(1))
        reader.readUInt8()
        assertFails { reader.readUInt8() }
    }

    @Test
    fun readSFloatDelegates() {
        // SFLOAT: mantissa=100, exponent=0 → 0x0064 little-endian = [0x64, 0x00]
        val reader = BleByteReader(byteArrayOf(0x64, 0x00))
        assertEquals(100.0f, reader.readSFloat())
    }
}
