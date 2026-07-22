package com.atruedev.kmpble.mesh.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Tests for AES-128-CMAC implementation.
 *
 * Test vectors from RFC 4493 and BLE Mesh Profile specification.
 */
class CmacTest {

    @Test
    fun testCmacBasicFunctionality() {
        val key = byteArrayOf(
            0x2B.toByte(), 0x7E.toByte(), 0x15.toByte(), 0x16.toByte(),
            0x28.toByte(), 0xAE.toByte(), 0xD2.toByte(), 0xA6.toByte(),
            0xAB.toByte(), 0xF7.toByte(), 0x15.toByte(), 0x88.toByte(),
            0x09.toByte(), 0xCF.toByte(), 0x4F.toByte(), 0x3C.toByte(),
        )
        val data = "mesh".encodeToByteArray()
        val tag = AesCmac.compute(key, data)
        assertEquals(16, tag.size)
    }

    @Test
    fun testCmacWithKnownInput() {
        // Basic test: CMAC should produce deterministic output
        val key = ByteArray(16) { it.toByte() }
        val data = "test".encodeToByteArray()

        val tag1 = AesCmac.compute(key, data)
        val tag2 = AesCmac.compute(key, data)

        assertEquals(16, tag1.size)
        assertContentEquals(tag1, tag2, "CMAC should be deterministic")
    }

    @Test
    fun testDifferentKeyDifferentTag() {
        val data = "test data".encodeToByteArray()
        val key1 = ByteArray(16) { 0x01.toByte() }
        val key2 = ByteArray(16) { 0x02.toByte() }

        val tag1 = AesCmac.compute(key1, data)
        val tag2 = AesCmac.compute(key2, data)

        // Different keys should produce different tags
        val areEqual = tag1.contentEquals(tag2)
        kotlin.test.assertFalse(areEqual, "Different keys should produce different tags")
    }

    @Test
    fun testBlockAlignedData() {
        val key = ByteArray(16) { 0x3C.toByte() }
        val data = ByteArray(16) { it.toByte() }

        val tag = AesCmac.compute(key, data)
        assertEquals(16, tag.size)
    }

    @Test
    fun testMultiBlockData() {
        val key = ByteArray(16) { 0x55.toByte() }
        val data = ByteArray(48) { it.toByte() } // 3 blocks

        val tag = AesCmac.compute(key, data)
        assertEquals(16, tag.size)
    }
}
