package com.atruedev.kmpble.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class PreparedWriteAssemblerTest {
    private val charA = Uuid.parse("0000aa01-0000-1000-8000-00805f9b34fb")
    private val charB = Uuid.parse("0000aa02-0000-1000-8000-00805f9b34fb")

    @Test
    fun empty_fragments_returns_empty_list() {
        val result = assembleWriteFragments(emptyList())
        assertIs<AssemblyResult.Success>(result)
        assertTrue(result.writes.isEmpty())
    }

    @Test
    fun single_fragment_at_offset_zero() {
        val data = byteArrayOf(1, 2, 3)
        val result = assembleWriteFragments(listOf(WriteFragment(charA, 0, data)))

        assertIs<AssemblyResult.Success>(result)
        assertEquals(1, result.writes.size)
        assertEquals(charA, result.writes[0].charUuid)
        assertTrue(data.contentEquals(result.writes[0].data))
    }

    @Test
    fun multiple_sequential_fragments_assembled_in_order() {
        val fragments =
            listOf(
                WriteFragment(charA, 0, byteArrayOf(0x10, 0x20)),
                WriteFragment(charA, 2, byteArrayOf(0x30, 0x40)),
                WriteFragment(charA, 4, byteArrayOf(0x50)),
            )

        val result = assembleWriteFragments(fragments)
        assertIs<AssemblyResult.Success>(result)
        assertEquals(1, result.writes.size)
        assertTrue(byteArrayOf(0x10, 0x20, 0x30, 0x40, 0x50).contentEquals(result.writes[0].data))
    }

    @Test
    fun out_of_order_fragments_sorted_by_offset() {
        val fragments =
            listOf(
                WriteFragment(charA, 3, byteArrayOf(0x40)),
                WriteFragment(charA, 0, byteArrayOf(0x10)),
                WriteFragment(charA, 1, byteArrayOf(0x20, 0x30)),
            )

        val result = assembleWriteFragments(fragments)
        assertIs<AssemblyResult.Success>(result)
        assertTrue(byteArrayOf(0x10, 0x20, 0x30, 0x40).contentEquals(result.writes[0].data))
    }

    @Test
    fun overlapping_fragments_use_last_write_wins() {
        val fragments =
            listOf(
                WriteFragment(charA, 0, byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())),
                WriteFragment(charA, 1, byteArrayOf(0xDD.toByte(), 0xEE.toByte())),
            )

        val result = assembleWriteFragments(fragments)
        assertIs<AssemblyResult.Success>(result)
        // Byte at offset 1 overwritten by second fragment
        assertTrue(byteArrayOf(0xAA.toByte(), 0xDD.toByte(), 0xEE.toByte()).contentEquals(result.writes[0].data))
    }

    @Test
    fun multiple_characteristics_grouped_independently() {
        val fragments =
            listOf(
                WriteFragment(charA, 0, byteArrayOf(1, 2)),
                WriteFragment(charB, 0, byteArrayOf(3, 4, 5)),
                WriteFragment(charA, 2, byteArrayOf(6)),
            )

        val result = assembleWriteFragments(fragments)
        assertIs<AssemblyResult.Success>(result)
        assertEquals(2, result.writes.size)

        val writeA = result.writes.first { it.charUuid == charA }
        val writeB = result.writes.first { it.charUuid == charB }
        assertTrue(byteArrayOf(1, 2, 6).contentEquals(writeA.data))
        assertTrue(byteArrayOf(3, 4, 5).contentEquals(writeB.data))
    }

    @Test
    fun exceeding_max_characteristic_size_returns_payload_too_large() {
        val fragments =
            listOf(
                WriteFragment(charA, 0, ByteArray(MAX_CHARACTERISTIC_VALUE_SIZE + 1)),
            )

        val result = assembleWriteFragments(fragments)
        assertIs<AssemblyResult.PayloadTooLarge>(result)
        assertEquals(charA, result.charUuid)
        assertEquals(MAX_CHARACTERISTIC_VALUE_SIZE + 1, result.actualSize)
    }

    @Test
    fun exactly_max_characteristic_size_succeeds() {
        val fragments =
            listOf(
                WriteFragment(charA, 0, ByteArray(MAX_CHARACTERISTIC_VALUE_SIZE)),
            )

        val result = assembleWriteFragments(fragments)
        assertIs<AssemblyResult.Success>(result)
        assertEquals(MAX_CHARACTERISTIC_VALUE_SIZE, result.writes[0].data.size)
    }

    @Test
    fun offset_gap_leaves_zero_filled_bytes() {
        val fragments =
            listOf(
                WriteFragment(charA, 0, byteArrayOf(0x01)),
                WriteFragment(charA, 4, byteArrayOf(0x05)),
            )

        val result = assembleWriteFragments(fragments)
        assertIs<AssemblyResult.Success>(result)
        val data = result.writes[0].data
        assertEquals(5, data.size)
        assertEquals(0x01, data[0])
        assertEquals(0x00, data[1])
        assertEquals(0x00, data[2])
        assertEquals(0x00, data[3])
        assertEquals(0x05, data[4])
    }

    @Test
    fun projected_buffer_size_with_empty_buffer() {
        assertEquals(10, projectedBufferSize(emptyList(), 0, 10))
    }

    @Test
    fun projected_buffer_size_accounts_for_existing_fragments() {
        val existing = listOf(WriteFragment(charA, 0, ByteArray(20)))
        assertEquals(20, projectedBufferSize(existing, 5, 10))
        assertEquals(25, projectedBufferSize(existing, 5, 20))
    }
}
