package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.CompanyId
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.scanner.internal.matchesFilters
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PostFilterTest {
    private fun ad(
        name: String? = null,
        rssi: Int = -60,
        serviceUuids: List<Uuid> = emptyList(),
        manufacturerData: Map<Int, BleData> = emptyMap(),
        serviceData: Map<Uuid, BleData> = emptyMap(),
        identifier: String = "AA:BB:CC:DD:EE:FF",
    ) = Advertisement(
        identifier = Identifier(identifier),
        name = name,
        rssi = rssi,
        txPower = null,
        isConnectable = true,
        serviceUuids = serviceUuids,
        manufacturerData = manufacturerData,
        serviceData = serviceData,
        timestampNanos = 0L,
    )

    private fun bleData(vararg bytes: Byte) = BleData(bytes)

    @Test
    fun emptyFiltersMatchEverything() {
        assertTrue(ad(name = "Anything").matchesFilters(emptyList()))
    }

    @Test
    fun nameExactMatch() {
        val filters = listOf(listOf(ScanPredicate.Name("HeartSensor")))
        assertTrue(ad(name = "HeartSensor").matchesFilters(filters))
        assertFalse(ad(name = "OtherDevice").matchesFilters(filters))
        assertFalse(ad(name = null).matchesFilters(filters))
    }

    @Test
    fun namePrefixMatch() {
        val filters = listOf(listOf(ScanPredicate.NamePrefix("Heart")))
        assertTrue(ad(name = "HeartSensor").matchesFilters(filters))
        assertTrue(ad(name = "HeartRate").matchesFilters(filters))
        assertFalse(ad(name = "MyHeart").matchesFilters(filters))
        assertFalse(ad(name = null).matchesFilters(filters))
    }

    @Test
    fun serviceUuidMatch() {
        val uuid = uuidFrom("180d")
        val filters = listOf(listOf(ScanPredicate.ServiceUuid(uuid)))
        assertTrue(ad(serviceUuids = listOf(uuid)).matchesFilters(filters))
        assertTrue(ad(serviceUuids = listOf(uuidFrom("180a"), uuid)).matchesFilters(filters))
        assertFalse(ad(serviceUuids = emptyList()).matchesFilters(filters))
        assertFalse(ad(serviceUuids = listOf(uuidFrom("180a"))).matchesFilters(filters))
    }

    @Test
    fun rssiThresholdMatch() {
        val filters = listOf(listOf(ScanPredicate.MinRssi(-70)))
        assertTrue(ad(rssi = -60).matchesFilters(filters))
        assertTrue(ad(rssi = -70).matchesFilters(filters))
        assertFalse(ad(rssi = -71).matchesFilters(filters))
        assertFalse(ad(rssi = -100).matchesFilters(filters))
    }

    @Test
    fun manufacturerDataMatchByCompanyIdOnly() {
        val filters = listOf(listOf(ScanPredicate.ManufacturerData(CompanyId.APPLE, null, null)))
        assertTrue(ad(manufacturerData = mapOf(CompanyId.APPLE to bleData(1, 2, 3))).matchesFilters(filters))
        assertFalse(
            ad(manufacturerData = mapOf(CompanyId.NORDIC_SEMICONDUCTOR to bleData(1, 2, 3))).matchesFilters(filters),
        )
        assertFalse(ad(manufacturerData = emptyMap()).matchesFilters(filters))
    }

    @Test
    fun manufacturerDataMatchWithMask() {
        val filters =
            listOf(
                listOf(
                    ScanPredicate.ManufacturerData(
                        companyId = CompanyId.APPLE,
                        data = byteArrayOf(0x02, 0x15),
                        mask = byteArrayOf(0xFF.toByte(), 0xFF.toByte()),
                    ),
                ),
            )
        // Matches: first 2 bytes are 02 15
        assertTrue(
            ad(manufacturerData = mapOf(CompanyId.APPLE to bleData(0x02, 0x15, 0x01, 0x02)))
                .matchesFilters(filters),
        )
        // Doesn't match: different data
        assertFalse(
            ad(manufacturerData = mapOf(CompanyId.APPLE to bleData(0x02, 0x16, 0x01, 0x02)))
                .matchesFilters(filters),
        )
    }

    @Test
    fun andGroupAllMustMatch() {
        val uuid = uuidFrom("180d")
        val filters =
            listOf(
                listOf(
                    ScanPredicate.Name("HeartSensor"),
                    ScanPredicate.ServiceUuid(uuid),
                ),
            )
        // Both match
        assertTrue(ad(name = "HeartSensor", serviceUuids = listOf(uuid)).matchesFilters(filters))
        // Name matches but UUID doesn't
        assertFalse(ad(name = "HeartSensor", serviceUuids = emptyList()).matchesFilters(filters))
        // UUID matches but name doesn't
        assertFalse(ad(name = "Other", serviceUuids = listOf(uuid)).matchesFilters(filters))
    }

    @Test
    fun orGroupsAnyCanMatch() {
        val uuid = uuidFrom("180d")
        val filters =
            listOf(
                listOf(ScanPredicate.Name("HeartSensor")),
                listOf(ScanPredicate.ServiceUuid(uuid)),
            )
        // First group matches
        assertTrue(ad(name = "HeartSensor").matchesFilters(filters))
        // Second group matches
        assertTrue(ad(serviceUuids = listOf(uuid)).matchesFilters(filters))
        // Both match
        assertTrue(ad(name = "HeartSensor", serviceUuids = listOf(uuid)).matchesFilters(filters))
        // Neither matches
        assertFalse(ad(name = "Other", serviceUuids = emptyList()).matchesFilters(filters))
    }

    @Test
    fun addressMatchCaseInsensitive() {
        val filters = listOf(listOf(ScanPredicate.Address("AA:BB:CC:DD:EE:FF")))
        assertTrue(ad(identifier = "AA:BB:CC:DD:EE:FF").matchesFilters(filters))
        assertTrue(ad(identifier = "aa:bb:cc:dd:ee:ff").matchesFilters(filters))
        assertFalse(ad(identifier = "11:22:33:44:55:66").matchesFilters(filters))
    }
}
