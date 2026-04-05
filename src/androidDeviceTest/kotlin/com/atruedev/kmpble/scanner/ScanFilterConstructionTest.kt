package com.atruedev.kmpble.scanner

import android.os.ParcelUuid
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

/**
 * Validates that [AndroidScanner.buildOsFilters] correctly translates
 * cross-platform [ScanPredicate] groups into Android [android.bluetooth.le.ScanFilter] objects.
 *
 * These tests MUST run on a real Android runtime because [android.bluetooth.le.ScanFilter.Builder]
 * uses [android.os.Parcel] internally, which is unavailable on JVM host tests.
 */
@OptIn(ExperimentalUuidApi::class)
@RunWith(AndroidJUnit4::class)
class ScanFilterConstructionTest {
    @Test
    fun emptyFilterGroups_returnsNull() {
        val result = AndroidScanner.buildOsFilters(emptyList())
        assertNull(result)
    }

    @Test
    fun singleServiceUuid_producesScanFilter() {
        val uuid = uuidFrom("180d")
        val groups = listOf(listOf(ScanPredicate.ServiceUuid(uuid)))

        val filters = AndroidScanner.buildOsFilters(groups)

        assertNotNull(filters)
        assertEquals(1, filters.size)
        assertEquals(
            ParcelUuid(java.util.UUID.fromString(uuid.toString())),
            filters[0].serviceUuid,
        )
    }

    @Test
    fun namePredicate_producesScanFilter() {
        val groups = listOf(listOf(ScanPredicate.Name("MyDevice")))

        val filters = AndroidScanner.buildOsFilters(groups)

        assertNotNull(filters)
        assertEquals(1, filters.size)
        assertEquals("MyDevice", filters[0].deviceName)
    }

    @Test
    fun addressPredicate_producesScanFilter() {
        val mac = "AA:BB:CC:DD:EE:FF"
        val groups = listOf(listOf(ScanPredicate.Address(mac)))

        val filters = AndroidScanner.buildOsFilters(groups)

        assertNotNull(filters)
        assertEquals(1, filters.size)
        assertEquals(mac, filters[0].deviceAddress)
    }

    @Test
    fun manufacturerData_withoutMask_producesScanFilter() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val groups = listOf(listOf(ScanPredicate.ManufacturerData(0x004C, data, mask = null)))

        val filters = AndroidScanner.buildOsFilters(groups)

        assertNotNull(filters)
        assertEquals(1, filters.size)
        assertEquals(0x004C, filters[0].manufacturerId)
        assertTrue(data.contentEquals(filters[0].manufacturerData))
    }

    @Test
    fun manufacturerData_withMask_producesScanFilter() {
        val data = byteArrayOf(0x01, 0x02)
        val mask = byteArrayOf(0xFF.toByte(), 0x00)
        val groups = listOf(listOf(ScanPredicate.ManufacturerData(0x004C, data, mask)))

        val filters = AndroidScanner.buildOsFilters(groups)

        assertNotNull(filters)
        assertEquals(1, filters.size)
        assertTrue(mask.contentEquals(filters[0].manufacturerDataMask))
    }

    @Test
    fun serviceData_withoutMask_producesScanFilter() {
        val uuid = uuidFrom("180d")
        val data = byteArrayOf(0x0A, 0x0B)
        val groups = listOf(listOf(ScanPredicate.ServiceData(uuid, data, mask = null)))

        val filters = AndroidScanner.buildOsFilters(groups)

        assertNotNull(filters)
        assertEquals(1, filters.size)
        assertEquals(
            ParcelUuid(java.util.UUID.fromString(uuid.toString())),
            filters[0].serviceDataUuid,
        )
        assertTrue(data.contentEquals(filters[0].serviceData))
    }

    @Test
    fun postFilterOnlyPredicates_returnNull() {
        val groups =
            listOf(
                listOf(
                    ScanPredicate.NamePrefix("My"),
                    ScanPredicate.MinRssi(-70),
                ),
            )

        val filters = AndroidScanner.buildOsFilters(groups)

        assertNull(filters)
    }

    @Test
    fun mixedGroup_producesFilterForOsPredicatesOnly() {
        val uuid = uuidFrom("180d")
        val groups =
            listOf(
                listOf(
                    ScanPredicate.ServiceUuid(uuid),
                    ScanPredicate.NamePrefix("Sensor"),
                    ScanPredicate.MinRssi(-60),
                ),
            )

        val filters = AndroidScanner.buildOsFilters(groups)

        assertNotNull(filters)
        assertEquals(1, filters.size)
        assertEquals(
            ParcelUuid(java.util.UUID.fromString(uuid.toString())),
            filters[0].serviceUuid,
        )
    }

    @Test
    fun multipleGroups_producesMultipleFilters() {
        val uuid1 = uuidFrom("180d")
        val uuid2 = uuidFrom("180f")
        val groups =
            listOf(
                listOf(ScanPredicate.ServiceUuid(uuid1)),
                listOf(ScanPredicate.ServiceUuid(uuid2)),
            )

        val filters = AndroidScanner.buildOsFilters(groups)

        assertNotNull(filters)
        assertEquals(2, filters.size)
    }
}
