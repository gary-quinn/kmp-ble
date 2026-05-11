package com.atruedev.kmpble.profiles.parsing

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails

class BleByteWriterTest {

    @Test
    fun writeUInt8() {
        val bytes = BleByteWriter().writeUInt8(0xFF).writeUInt8(0).toByteArray()
        assertContentEquals(byteArrayOf(0xFF.toByte(), 0x00), bytes)
    }

    @Test
    fun writeUInt8RejectsOutOfRange() {
        assertFails { BleByteWriter().writeUInt8(-1) }
        assertFails { BleByteWriter().writeUInt8(0x100) }
    }

    @Test
    fun writeInt8() {
        val bytes = BleByteWriter().writeInt8(-1).writeInt8(127).writeInt8(-128).toByteArray()
        assertContentEquals(byteArrayOf(0xFF.toByte(), 0x7F, 0x80.toByte()), bytes)
    }

    @Test
    fun writeInt8RejectsOutOfRange() {
        assertFails { BleByteWriter().writeInt8(128) }
        assertFails { BleByteWriter().writeInt8(-129) }
    }

    @Test
    fun writeUInt16LittleEndian() {
        // 384 = 0x0180 → 0x80, 0x01
        val bytes = BleByteWriter().writeUInt16(384).toByteArray()
        assertContentEquals(byteArrayOf(0x80.toByte(), 0x01), bytes)
    }

    @Test
    fun writeUInt16RejectsOutOfRange() {
        assertFails { BleByteWriter().writeUInt16(-1) }
        assertFails { BleByteWriter().writeUInt16(0x10000) }
    }

    @Test
    fun writeInt16Negative() {
        // -1 → 0xFFFF → 0xFF, 0xFF
        val bytes = BleByteWriter().writeInt16(-1).toByteArray()
        assertContentEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte()), bytes)
    }

    @Test
    fun writeInt16Boundary() {
        val bytes = BleByteWriter()
            .writeInt16(Short.MIN_VALUE.toInt())
            .writeInt16(Short.MAX_VALUE.toInt())
            .toByteArray()
        // -32768 → 0x8000 → 0x00, 0x80
        // 32767 → 0x7FFF → 0xFF, 0x7F
        assertContentEquals(
            byteArrayOf(0x00, 0x80.toByte(), 0xFF.toByte(), 0x7F),
            bytes,
        )
    }

    @Test
    fun writeUInt32LittleEndian() {
        // 0x12345678 → 0x78, 0x56, 0x34, 0x12
        val bytes = BleByteWriter().writeUInt32(0x12345678L).toByteArray()
        assertContentEquals(byteArrayOf(0x78, 0x56, 0x34, 0x12), bytes)
    }

    @Test
    fun writeUInt32Max() {
        val bytes = BleByteWriter().writeUInt32(0xFFFFFFFFL).toByteArray()
        assertContentEquals(
            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            bytes,
        )
    }

    @Test
    fun writeUInt32RejectsOutOfRange() {
        assertFails { BleByteWriter().writeUInt32(-1L) }
        assertFails { BleByteWriter().writeUInt32(0x1_0000_0000L) }
    }

    @Test
    fun writeUtf8() {
        val bytes = BleByteWriter().writeUtf8("AB").toByteArray()
        assertContentEquals(byteArrayOf(0x41, 0x42), bytes)
    }

    @Test
    fun writeBytesAppends() {
        val bytes = BleByteWriter()
            .writeUInt8(0x01)
            .writeBytes(byteArrayOf(0x02, 0x03))
            .writeUInt8(0x04)
            .toByteArray()
        assertContentEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04), bytes)
    }

    @Test
    fun sizeReflectsWrites() {
        val writer = BleByteWriter()
        assertEquals(0, writer.size)
        writer.writeUInt8(0)
        assertEquals(1, writer.size)
        writer.writeUInt32(0)
        assertEquals(5, writer.size)
    }

    @Test
    fun roundTripsThroughReader() {
        val original = BleByteWriter()
            .writeUInt8(0xAB)
            .writeInt16(-1234)
            .writeUInt32(0xCAFEBABEL)
            .writeUtf8("hi")
            .toByteArray()
        val reader = BleByteReader(original)
        assertEquals(0xAB, reader.readUInt8())
        assertEquals(-1234, reader.readInt16())
        assertEquals(0xCAFEBABEL, reader.readUInt32())
        assertEquals("hi", reader.readUtf8(2))
    }
}
