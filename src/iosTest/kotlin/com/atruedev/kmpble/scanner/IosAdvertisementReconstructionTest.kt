@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.atruedev.kmpble.scanner

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreBluetooth.CBAdvertisementDataLocalNameKey
import platform.CoreBluetooth.CBAdvertisementDataManufacturerDataKey
import platform.CoreBluetooth.CBAdvertisementDataServiceDataKey
import platform.CoreBluetooth.CBAdvertisementDataServiceUUIDsKey
import platform.CoreBluetooth.CBAdvertisementDataSolicitedServiceUUIDsKey
import platform.CoreBluetooth.CBAdvertisementDataTxPowerLevelKey
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.Foundation.create
import platform.Foundation.numberWithInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosAdvertisementReconstructionTest {
    @Test
    fun emptyDictionaryYieldsEmptyBytes() {
        val result = reconstructAdvertisingBytes(emptyMap<Any?, Any?>())
        assertEquals(0, result.size)
    }

    @Test
    fun localNameEncodesAsCompleteLocalNameTlv() {
        val data = mapOf<Any?, Any?>(CBAdvertisementDataLocalNameKey to "ABC")
        val bytes = reconstructAdvertisingBytes(data).toByteArray()

        // length (1 type + 3 name) = 4, type = 0x09, payload = "ABC"
        assertContentEquals(byteArrayOf(0x04, 0x09, 0x41, 0x42, 0x43), bytes)
    }

    @Test
    fun txPowerEncodesAsSignedByte() {
        val data = mapOf<Any?, Any?>(CBAdvertisementDataTxPowerLevelKey to NSNumber.numberWithInt(-12))

        val bytes = reconstructAdvertisingBytes(data).toByteArray()

        assertContentEquals(byteArrayOf(0x02, 0x0A, (-12).toByte()), bytes)
    }

    @Test
    fun manufacturerDataPassesThroughUnchanged() {
        val payload = byteArrayOf(0x4C, 0x00, 0x10, 0x05) // Apple companyId LE + payload
        val data =
            mapOf<Any?, Any?>(
                CBAdvertisementDataManufacturerDataKey to payload.toNSData(),
            )

        val bytes = reconstructAdvertisingBytes(data).toByteArray()

        assertContentEquals(byteArrayOf(0x05, 0xFF.toByte()) + payload, bytes)
    }

    @Test
    fun sixteenBitServiceUuidsEncodeLittleEndianGrouped() {
        val data =
            mapOf<Any?, Any?>(
                CBAdvertisementDataServiceUUIDsKey to
                    listOf(
                        CBUUID.UUIDWithString("180D"),
                        CBUUID.UUIDWithString("180A"),
                    ),
            )

        val bytes = reconstructAdvertisingBytes(data).toByteArray()

        // length = 1 type + 4 (two 16-bit UUIDs) = 5; type = 0x03; UUIDs LE
        assertContentEquals(byteArrayOf(0x05, 0x03, 0x0D, 0x18, 0x0A, 0x18), bytes)
    }

    @Test
    fun oneTwentyEightBitServiceUuidsEncodeLittleEndian() {
        val uuid = CBUUID.UUIDWithString("0000180D-0000-1000-8000-00805F9B34FB")
        val data = mapOf<Any?, Any?>(CBAdvertisementDataServiceUUIDsKey to listOf(uuid))

        val bytes = reconstructAdvertisingBytes(data).toByteArray()

        val expectedUuidBytes =
            byteArrayOf(
                0xFB.toByte(),
                0x34,
                0x9B.toByte(),
                0x5F,
                0x80.toByte(),
                0x00,
                0x00,
                0x80.toByte(),
                0x00,
                0x10,
                0x00,
                0x00,
                0x0D,
                0x18,
                0x00,
                0x00,
            )
        assertContentEquals(byteArrayOf(0x11, 0x07) + expectedUuidBytes, bytes)
    }

    @Test
    fun mixedWidthServiceUuidsEmitSeparateTlvSegments() {
        val data =
            mapOf<Any?, Any?>(
                CBAdvertisementDataServiceUUIDsKey to
                    listOf(
                        CBUUID.UUIDWithString("180D"),
                        CBUUID.UUIDWithString("0000180D-0000-1000-8000-00805F9B34FB"),
                    ),
            )

        val bytes = reconstructAdvertisingBytes(data).toByteArray()

        // 16-bit segment first, 128-bit second
        assertEquals(0x03, bytes[0].toInt())
        assertEquals(0x03, bytes[1].toInt())
        // 16-bit segment is 4 bytes total (length + type + 2 uuid bytes)
        assertEquals(0x11, bytes[4].toInt())
        assertEquals(0x07, bytes[5].toInt())
    }

    @Test
    fun serviceData16BitEncodesUuidLittleEndianFollowedByPayload() {
        val payload = byteArrayOf(0x55, 0xAA.toByte())
        val data =
            mapOf<Any?, Any?>(
                CBAdvertisementDataServiceDataKey to
                    mapOf(
                        CBUUID.UUIDWithString("FEAA") to payload.toNSData(),
                    ),
            )

        val bytes = reconstructAdvertisingBytes(data).toByteArray()

        // length = 1 type + 2 uuid + 2 payload = 5; type = 0x16
        assertContentEquals(
            byteArrayOf(0x05, 0x16, 0xAA.toByte(), 0xFE.toByte(), 0x55, 0xAA.toByte()),
            bytes,
        )
    }

    @Test
    fun solicitedServiceUuidsUseSolicitTypeCodes() {
        val data =
            mapOf<Any?, Any?>(
                CBAdvertisementDataSolicitedServiceUUIDsKey to listOf(CBUUID.UUIDWithString("FEAA")),
            )

        val bytes = reconstructAdvertisingBytes(data).toByteArray()

        // type 0x14 = list of 16-bit solicit UUIDs
        assertContentEquals(byteArrayOf(0x03, 0x14, 0xAA.toByte(), 0xFE.toByte()), bytes)
    }

    @Test
    fun unknownEntriesAreSkipped() {
        val data =
            mapOf<Any?, Any?>(
                "SomeUnknownKey" to "ignored",
                CBAdvertisementDataLocalNameKey to "X",
            )

        val bytes = reconstructAdvertisingBytes(data).toByteArray()

        assertContentEquals(byteArrayOf(0x02, 0x09, 0x58), bytes)
    }
}

private fun assertContentEquals(
    expected: ByteArray,
    actual: ByteArray,
) {
    val match = expected.size == actual.size && expected.indices.all { expected[it] == actual[it] }
    assertTrue(match, "expected=${expected.toHex()}, actual=${actual.toHex()}")
}

private fun ByteArray.toHex(): String = joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}
