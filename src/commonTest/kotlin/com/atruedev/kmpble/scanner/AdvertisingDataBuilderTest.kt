package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.connection.Phy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdvertisingDataBuilderTest {
    @Test
    fun `builds with default values`() {
        val ad = AdvertisingDataBuilder().build()

        assertEquals(-60, ad.rssi)
        assertTrue(ad.isConnectable)
        assertTrue(ad.isLegacy)
        assertEquals(Phy.Le1M, ad.primaryPhy)
        assertEquals(DataStatus.Complete, ad.dataStatus)
        assertTrue(ad.serviceUuids.isEmpty())
        assertTrue(ad.manufacturerData.isEmpty())
        assertTrue(ad.serviceData.isEmpty())
        assertNull(ad.name)
        assertNull(ad.txPower)
        assertNull(ad.secondaryPhy)
        assertNull(ad.advertisingSid)
        assertNull(ad.periodicAdvertisingInterval)
    }

    @Test
    fun `sets all top-level fields`() {
        val ad =
            AdvertisingDataBuilder()
                .apply {
                    identifier("AA:BB:CC:DD:EE:FF")
                    name("TestDevice")
                    rssi(-45)
                    txPower(4)
                    isConnectable(false)
                    isLegacy(false)
                    primaryPhy(Phy.Le2M)
                    secondaryPhy(Phy.LeCoded)
                    advertisingSid(7)
                    periodicAdvertisingInterval(100)
                    dataStatus(DataStatus.Truncated)
                    timestampNanos(123456789L)
                }.build()

        assertEquals(Identifier("AA:BB:CC:DD:EE:FF"), ad.identifier)
        assertEquals("TestDevice", ad.name)
        assertEquals(-45, ad.rssi)
        assertEquals(4, ad.txPower)
        assertFalse(ad.isConnectable)
        assertFalse(ad.isLegacy)
        assertEquals(Phy.Le2M, ad.primaryPhy)
        assertEquals(Phy.LeCoded, ad.secondaryPhy)
        assertEquals(7, ad.advertisingSid)
        assertEquals(100, ad.periodicAdvertisingInterval)
        assertEquals(DataStatus.Truncated, ad.dataStatus)
        assertEquals(123456789L, ad.timestampNanos)
    }

    @Test
    fun `sets service UUIDs from strings`() {
        val ad =
            AdvertisingDataBuilder()
                .apply {
                    serviceUuids("180d", "180f")
                }.build()

        assertEquals(2, ad.serviceUuids.size)
        assertTrue(ad.serviceUuids.contains(uuidFrom("180d")))
        assertTrue(ad.serviceUuids.contains(uuidFrom("180f")))
    }

    @Test
    fun `manufacturer data accumulates`() {
        val data1 = BleData(byteArrayOf(0x01, 0x02))
        val data2 = BleData(byteArrayOf(0x03))

        val ad =
            AdvertisingDataBuilder()
                .apply {
                    manufacturerData(0x004C, data1)
                    manufacturerData(0x0059, data2)
                }.build()

        assertEquals(2, ad.manufacturerData.size)
        assertTrue(ad.manufacturerData[0x004C]?.toByteArray()?.contentEquals(byteArrayOf(0x01, 0x02)) == true)
        assertTrue(ad.manufacturerData[0x0059]?.toByteArray()?.contentEquals(byteArrayOf(0x03)) == true)
    }

    @Test
    fun `service data accumulates`() {
        val data1 = BleData(byteArrayOf(0x0A))
        val data2 = BleData(byteArrayOf(0x0B))

        val ad =
            AdvertisingDataBuilder()
                .apply {
                    serviceData("180d", data1)
                    serviceData("180a", data2)
                }.build()

        assertEquals(2, ad.serviceData.size)
        assertTrue(ad.serviceData[uuidFrom("180d")]?.toByteArray()?.contentEquals(byteArrayOf(0x0A)) == true)
        assertTrue(ad.serviceData[uuidFrom("180a")]?.toByteArray()?.contentEquals(byteArrayOf(0x0B)) == true)
    }

    @Test
    fun `buildAdvertisement convenience function`() {
        val ad =
            buildAdvertisement {
                name("Convenient")
                rssi(-80)
            }

        assertEquals("Convenient", ad.name)
        assertEquals(-80, ad.rssi)
    }

    @Test
    fun `DSL via with block`() {
        val ad =
            AdvertisingDataBuilder {
                name("DSLTest")
                isConnectable(false)
                serviceUuids("1800")
            }.build()

        assertEquals("DSLTest", ad.name)
        assertFalse(ad.isConnectable)
        assertEquals(1, ad.serviceUuids.size)
    }

    @Test
    fun `auto-generated identifiers are unique per builder`() {
        val ad1 = AdvertisingDataBuilder().build()
        val ad2 = AdvertisingDataBuilder().build()

        assertTrue(ad1.identifier != ad2.identifier)
    }

    @Test
    fun `nullable fields default to null`() {
        val ad = AdvertisingDataBuilder().build()

        assertNull(ad.name)
        assertNull(ad.txPower)
        assertNull(ad.secondaryPhy)
        assertNull(ad.advertisingSid)
        assertNull(ad.periodicAdvertisingInterval)
        assertNull(ad.rawAdvertising)
    }

    @Test
    fun `raw advertising can be set`() {
        val raw = RawAdvertising.OnAir(BleData(byteArrayOf(0x02, 0x01, 0x06)))
        val ad =
            AdvertisingDataBuilder()
                .apply {
                    rawAdvertising(raw)
                }.build()

        assertEquals(raw, ad.rawAdvertising)
    }

    @Test
    fun `timestamp and data status are propagated`() {
        val ad =
            AdvertisingDataBuilder()
                .apply {
                    timestampNanos(999_000_000L)
                    dataStatus(DataStatus.Truncated)
                }.build()

        assertEquals(999_000_000L, ad.timestampNanos)
        assertEquals(DataStatus.Truncated, ad.dataStatus)
    }
}
