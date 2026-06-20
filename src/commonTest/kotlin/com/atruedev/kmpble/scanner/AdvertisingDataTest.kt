package com.atruedev.kmpble.scanner

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class AdvertisingDataTest {

    // -- Flags (AD Type 0x01) --

    @Test
    fun `flags encode single flag`() {
        val data = AdvertisingData {
            flags(AdvertisingFlags.LE_GENERAL_DISCOVERABLE)
        }
        val bytes = data.encode()
        assertContentEquals(byteArrayOf(0x02, 0x01, 0x02), bytes)
    }

    @Test
    fun `flags encode multiple flags OR'ed`() {
        val data = AdvertisingData {
            flags(
                AdvertisingFlags.LE_GENERAL_DISCOVERABLE,
                AdvertisingFlags.BR_EDR_NOT_SUPPORTED,
            )
        }
        val bytes = data.encode()
        // 0x02 | 0x04 = 0x06
        assertContentEquals(byteArrayOf(0x02, 0x01, 0x06), bytes)
    }

    @Test
    fun `flags encode all five flags`() {
        val data = AdvertisingData {
            flags(
                AdvertisingFlags.LE_LIMITED_DISCOVERABLE,
                AdvertisingFlags.LE_GENERAL_DISCOVERABLE,
                AdvertisingFlags.BR_EDR_NOT_SUPPORTED,
                AdvertisingFlags.LE_BR_EDR_CONTROLLER,
                AdvertisingFlags.LE_BR_EDR_HOST,
            )
        }
        val bytes = data.encode()
        // 0x01 | 0x02 | 0x04 | 0x08 | 0x10 = 0x1F
        assertContentEquals(byteArrayOf(0x02, 0x01, 0x1F.toByte()), bytes)
    }

    // -- Local Name (AD Types 0x08, 0x09) --

    @Test
    fun `complete local name encodes correctly`() {
        val data = AdvertisingData {
            completeLocalName("Test")
        }
        val bytes = data.encode()
        // Length=5, Type=0x09, "Test" in UTF-8
        val expected = byteArrayOf(0x05, 0x09) + "Test".encodeToByteArray()
        assertContentEquals(expected, bytes)
    }

    @Test
    fun `short local name encodes correctly`() {
        val data = AdvertisingData {
            shortLocalName("T")
        }
        val bytes = data.encode()
        // Length=2, Type=0x08, "T"
        assertContentEquals(byteArrayOf(0x02, 0x08, 'T'.code.toByte()), bytes)
    }

    @Test
    fun `complete local name takes precedence over short in encode`() {
        val data = AdvertisingData {
            shortLocalName("Short")
            completeLocalName("Complete")
        }
        val bytes = data.encode()
        val types = (0 until bytes.size step 2).mapNotNull { i ->
            if (i + 1 < bytes.size && bytes[i].toInt() and 0xFF > 1) bytes[i + 1].toInt() and 0xFF else null
        }
        assertTrue(types.contains(0x09))
        assertTrue(types.contains(0x08))
    }

    // -- Service UUIDs (AD Types 0x03, 0x05, 0x07) --

    @Test
    fun `16-bit service UUID encodes with type 0x03`() {
        val data = AdvertisingData {
            serviceUuid16(0x180D) // Heart Rate
        }
        val bytes = data.encode()
        // Length=3, Type=0x03, UUID=0x0D18 (LE)
        assertContentEquals(byteArrayOf(0x03, 0x03, 0x0D.toByte(), 0x18.toByte()), bytes)
    }

    @Test
    fun `multiple 16-bit service UUIDs`() {
        val data = AdvertisingData {
            serviceUuid16(0x180D) // Heart Rate
            serviceUuid16(0x180A) // Device Information
        }
        val bytes = data.encode()
        // Length=5, Type=0x03, 4 bytes of UUIDs
        assertEquals(0x05, bytes[0].toInt() and 0xFF)
        assertEquals(0x03, bytes[1].toInt() and 0xFF)
        assertEquals(6, bytes.size)
    }

    @Test
    fun `32-bit service UUID encodes with type 0x05`() {
        val data = AdvertisingData {
            serviceUuid32(0x12345678)
        }
        val bytes = data.encode()
        // Length=5, Type=0x05, UUID in LE
        assertEquals(0x05, bytes[0].toInt() and 0xFF)
        assertEquals(0x05, bytes[1].toInt() and 0xFF)
        assertEquals(0x78.toByte(), bytes[2])
        assertEquals(0x56.toByte(), bytes[3])
        assertEquals(0x34.toByte(), bytes[4])
        assertEquals(0x12.toByte(), bytes[5])
    }

    @Test
    fun `128-bit service UUID encodes with type 0x07`() {
        val uuid = Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb")
        val data = AdvertisingData {
            serviceUuid128(uuid)
        }
        val bytes = data.encode()
        // Length=17, Type=0x07, 16 bytes UUID in LE
        assertEquals(0x11, bytes[0].toInt() and 0xFF)
        assertEquals(0x07, bytes[1].toInt() and 0xFF)
        assertEquals(18, bytes.size)
    }

    // -- TX Power (AD Type 0x0A) --

    @Test
    fun `TX power level encodes correctly`() {
        val data = AdvertisingData {
            txPowerLevel(-59)
        }
        val bytes = data.encode()
        // Length=2, Type=0x0A, value=-59 (0xC5)
        assertContentEquals(byteArrayOf(0x02, 0x0A, 0xC5.toByte()), bytes)
    }

    @Test
    fun `TX power zero encodes correctly`() {
        val data = AdvertisingData {
            txPowerLevel(0)
        }
        val bytes = data.encode()
        assertContentEquals(byteArrayOf(0x02, 0x0A, 0x00), bytes)
    }

    @Test
    fun `TX power positive encodes correctly`() {
        val data = AdvertisingData {
            txPowerLevel(4)
        }
        val bytes = data.encode()
        assertContentEquals(byteArrayOf(0x02, 0x0A, 0x04), bytes)
    }

    // -- Manufacturer Data (AD Type 0xFF) --

    @Test
    fun `manufacturer data encodes with company ID in LE`() {
        val payload = byteArrayOf(0x02, 0x15, 0x01)
        val data = AdvertisingData {
            manufacturerData(0x004C, payload) // Apple company ID
        }
        val bytes = data.encode()
        // Length, Type=0xFF, companyId LE (0x4C, 0x00), payload
        assertEquals(0xFF.toByte(), bytes[1])
        assertEquals(0x4C.toByte(), bytes[2])
        assertEquals(0x00, bytes[3])
        assertContentEquals(payload, bytes.copyOfRange(4, bytes.size))
    }

    // -- Service Data (AD Types 0x16, 0x20, 0x21) --

    @Test
    fun `service data 16-bit encodes with UUID prefix`() {
        val payload = byteArrayOf(0x01, 0x02, 0x03)
        val data = AdvertisingData {
            serviceData16(0x180D, payload)
        }
        val bytes = data.encode()
        // Length, Type=0x16, UUID LE (0x0D, 0x18), payload
        assertEquals(0x16.toByte(), bytes[1])
        assertEquals(0x0D.toByte(), bytes[2])
        assertEquals(0x18.toByte(), bytes[3])
        assertContentEquals(payload, bytes.copyOfRange(4, bytes.size))
    }

    @Test
    fun `service data 128-bit encodes with UUID prefix`() {
        val uuid = Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb")
        val payload = byteArrayOf(0x42)
        val data = AdvertisingData {
            serviceData128(uuid, payload)
        }
        val bytes = data.encode()
        // Length=18, Type=0x21, 16 bytes UUID + 1 byte payload
        assertEquals(0x21.toByte(), bytes[1])
        assertEquals(19, bytes.size)
    }

    // -- Combination --

    @Test
    fun `multiple AD types in single payload`() {
        val data = AdvertisingData {
            flags(AdvertisingFlags.LE_GENERAL_DISCOVERABLE, AdvertisingFlags.BR_EDR_NOT_SUPPORTED)
            completeLocalName("HRM")
            serviceUuid16(0x180D)
            txPowerLevel(-12)
        }
        val bytes = data.encode()
        // Should contain flag AD, name AD, uuid AD, txPower AD
        assertTrue(bytes.isNotEmpty())
        // Verify flags segment
        val flagsOffset = bytes.asList().indexOf(0x01.toByte())
        assertTrue(flagsOffset > 0)
        assertEquals(0x02, bytes[flagsOffset - 1].toInt() and 0xFF) // length
        assertEquals(0x06, bytes[flagsOffset + 1].toInt() and 0xFF) // 0x02 | 0x04
    }

    // -- Empty --

    @Test
    fun `empty advertising data encodes to empty array`() {
        val data = AdvertisingData { }
        val bytes = data.encode()
        assertContentEquals(byteArrayOf(), bytes)
    }

    @Test
    fun `default constructor produces empty data`() {
        val data = AdvertisingData { }
        assertEquals(0, data.flags)
        assertEquals(null, data.completeLocalName)
        assertEquals(emptyList(), data.serviceUuids16)
    }

    // -- Equality --

    @Test
    fun `equal data produces same hashCode`() {
        val a = AdvertisingData {
            flags(AdvertisingFlags.LE_GENERAL_DISCOVERABLE)
            completeLocalName("Test")
        }
        val b = AdvertisingData {
            flags(AdvertisingFlags.LE_GENERAL_DISCOVERABLE)
            completeLocalName("Test")
        }
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `different flags break equality`() {
        val a = AdvertisingData { flags(AdvertisingFlags.LE_GENERAL_DISCOVERABLE) }
        val b = AdvertisingData { flags(AdvertisingFlags.LE_LIMITED_DISCOVERABLE) }
        assertNotEquals(a, b)
    }

    @Test
    fun `different manufacturer data breaks equality`() {
        val a = AdvertisingData { manufacturerData(0x004C, byteArrayOf(0x01)) }
        val b = AdvertisingData { manufacturerData(0x004C, byteArrayOf(0x02)) }
        assertNotEquals(a, b)
    }

    @Test
    fun `same manufacturer data with same bytes is equal`() {
        val a = AdvertisingData { manufacturerData(0x004C, byteArrayOf(0x01, 0x02)) }
        val b = AdvertisingData { manufacturerData(0x004C, byteArrayOf(0x01, 0x02)) }
        assertEquals(a, b)
    }

    // -- Service Solicitation UUIDs --

    @Test
    fun `16-bit solicitation UUID encodes with type 0x14`() {
        val data = AdvertisingData {
            serviceSolicitationUuid16(0x180D)
        }
        val bytes = data.encode()
        assertEquals(0x14.toByte(), bytes[1])
        assertEquals(4, bytes.size)
    }

    @Test
    fun `128-bit solicitation UUID encodes with type 0x15`() {
        val uuid = Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb")
        val data = AdvertisingData {
            serviceSolicitationUuid128(uuid)
        }
        val bytes = data.encode()
        assertEquals(0x15.toByte(), bytes[1])
    }
}
