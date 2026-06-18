package com.atruedev.kmpble.beacon

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.scanner.Advertisement
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BeaconTest {
    // --- iBeacon ---

    @Test
    fun `parse iBeacon from valid manufacturer data`() {
        // iBeacon: UUID=E2C56DB5-DFFB-48D2-B060-D0F5A71096E0, major=1, minor=1, power=-59
        val bytes =
            byteArrayOf(
                0x02,
                0x15.toByte(), // type=0x02, length=0x15
                0xE2.toByte(),
                0xC5.toByte(),
                0x6D.toByte(),
                0xB5.toByte(), // UUID bytes 0-3
                0xDF.toByte(),
                0xFB.toByte(),
                0x48.toByte(),
                0xD2.toByte(), // UUID bytes 4-7
                0xB0.toByte(),
                0x60.toByte(),
                0xD0.toByte(),
                0xF5.toByte(), // UUID bytes 8-11
                0xA7.toByte(),
                0x10.toByte(),
                0x96.toByte(),
                0xE0.toByte(), // UUID bytes 12-15
                0x00,
                0x01, // major=1
                0x00,
                0x01, // minor=1
                0xC5.toByte(), // measuredPower=-59
            )

        val ad = createAdvertisement(manufacturerData = mapOf(0x004C to BleData(bytes)))
        val beacon = Beacon.parse(ad)

        assertTrue(beacon is Beacon.IBeacon)
        val ibeacon = beacon as Beacon.IBeacon
        assertEquals("e2c56db5-dffb-48d2-b060-d0f5a71096e0", ibeacon.proximityUuid.toString())
        assertEquals(1, ibeacon.major)
        assertEquals(1, ibeacon.minor)
        assertEquals(-59, ibeacon.measuredPower)
    }

    @Test
    fun `parse iBeacon returns null for non-Apple manufacturer data`() {
        val bytes =
            byteArrayOf(
                0x02,
                0x15.toByte(),
                0xE2.toByte(),
                0xC5.toByte(),
                0x6D.toByte(),
                0xB5.toByte(),
                0xDF.toByte(),
                0xFB.toByte(),
                0x48.toByte(),
                0xD2.toByte(),
                0xB0.toByte(),
                0x60.toByte(),
                0xD0.toByte(),
                0xF5.toByte(),
                0xA7.toByte(),
                0x10.toByte(),
                0x96.toByte(),
                0xE0.toByte(),
                0x00,
                0x01,
                0x00,
                0x01,
                0xC5.toByte(),
            )
        // Wrong company ID (0x004D instead of 0x004C)
        val ad = createAdvertisement(manufacturerData = mapOf(0x004D to BleData(bytes)))
        assertNull(Beacon.parse(ad))
    }

    @Test
    fun `parse iBeacon returns null for wrong type byte`() {
        val bytes =
            byteArrayOf(
                0x03,
                0x15.toByte(), // wrong type (0x03 instead of 0x02)
                0xE2.toByte(),
                0xC5.toByte(),
                0x6D.toByte(),
                0xB5.toByte(),
                0xDF.toByte(),
                0xFB.toByte(),
                0x48.toByte(),
                0xD2.toByte(),
                0xB0.toByte(),
                0x60.toByte(),
                0xD0.toByte(),
                0xF5.toByte(),
                0xA7.toByte(),
                0x10.toByte(),
                0x96.toByte(),
                0xE0.toByte(),
                0x00,
                0x01,
                0x00,
                0x01,
                0xC5.toByte(),
            )
        val ad = createAdvertisement(manufacturerData = mapOf(0x004C to BleData(bytes)))
        assertNull(Beacon.parse(ad))
    }

    @Test
    fun `parse iBeacon returns null for insufficient data`() {
        val bytes = byteArrayOf(0x02, 0x15.toByte(), 0x01, 0x02) // too short
        val ad = createAdvertisement(manufacturerData = mapOf(0x004C to BleData(bytes)))
        assertNull(Beacon.parse(ad))
    }

    @Test
    fun `parse iBeacon handles positive measuredPower`() {
        // iBeacon with measuredPower = +4 (0x04)
        val bytes =
            byteArrayOf(
                0x02,
                0x15.toByte(),
                0xE2.toByte(),
                0xC5.toByte(),
                0x6D.toByte(),
                0xB5.toByte(),
                0xDF.toByte(),
                0xFB.toByte(),
                0x48.toByte(),
                0xD2.toByte(),
                0xB0.toByte(),
                0x60.toByte(),
                0xD0.toByte(),
                0xF5.toByte(),
                0xA7.toByte(),
                0x10.toByte(),
                0x96.toByte(),
                0xE0.toByte(),
                0x00,
                0x01,
                0x00,
                0x01,
                0x04,
            )
        val ad = createAdvertisement(manufacturerData = mapOf(0x004C to BleData(bytes)))
        val beacon = Beacon.parse(ad) as Beacon.IBeacon
        assertEquals(4, beacon.measuredPower)
    }

    // --- Eddystone-UID ---

    @Test
    fun `parse EddystoneUID from valid service data`() {
        // Eddystone-UID: ranging=-18, namespace=6E4A0C0D9C8E4B3AA1F2, instance=C5D6E7F8A9B0
        val bytes =
            byteArrayOf(
                0x00, // frame type = UID
                0xEE.toByte(), // ranging data = -18
                0x6E.toByte(),
                0x4A.toByte(),
                0x0C.toByte(),
                0x0D.toByte(),
                0x9C.toByte(), // namespace 0-4
                0x8E.toByte(),
                0x4B.toByte(),
                0x3A.toByte(),
                0xA1.toByte(),
                0xF2.toByte(), // namespace 5-9
                0xC5.toByte(),
                0xD6.toByte(),
                0xE7.toByte(),
                0xF8.toByte(),
                0xA9.toByte(),
                0xB0.toByte(), // instance
            )

        val eddystoneUuid = Beacon.EDDYSTONE_SERVICE_UUID
        val ad = createAdvertisement(serviceData = mapOf(eddystoneUuid to BleData(bytes)))
        val beacon = Beacon.parse(ad)

        assertTrue(beacon is Beacon.EddystoneUID)
        val uid = beacon as Beacon.EddystoneUID
        assertEquals(-18, uid.rangingData)
        assertContentEquals(
            byteArrayOf(
                0x6E.toByte(),
                0x4A.toByte(),
                0x0C.toByte(),
                0x0D.toByte(),
                0x9C.toByte(),
                0x8E.toByte(),
                0x4B.toByte(),
                0x3A.toByte(),
                0xA1.toByte(),
                0xF2.toByte(),
            ),
            uid.namespace,
        )
        assertContentEquals(
            byteArrayOf(
                0xC5.toByte(),
                0xD6.toByte(),
                0xE7.toByte(),
                0xF8.toByte(),
                0xA9.toByte(),
                0xB0.toByte(),
            ),
            uid.instance,
        )
    }

    @Test
    fun `parse EddystoneUID returns null for insufficient data`() {
        val bytes = byteArrayOf(0x00, 0xEE.toByte(), 0x01) // too short
        val eddystoneUuid = Beacon.EDDYSTONE_SERVICE_UUID
        val ad = createAdvertisement(serviceData = mapOf(eddystoneUuid to BleData(bytes)))
        assertNull(Beacon.parse(ad))
    }

    // --- Eddystone-URL ---

    @Test
    fun `parse EddystoneURL https scheme with dot-com expansion`() {
        // https://nousresearch.com/
        // Scheme 0x03 = https://, "nousresearch" literal, 0x00 = .com/
        val bytes =
            byteArrayOf(
                0x10, // frame type = URL
                0xEE.toByte(), // txPower = -18
                0x03, // scheme = https://
                0x6E.toByte(),
                0x6F.toByte(),
                0x75.toByte(),
                0x73.toByte(), // nous
                0x72.toByte(),
                0x65.toByte(),
                0x73.toByte(),
                0x65.toByte(), // rese
                0x61.toByte(),
                0x72.toByte(),
                0x63.toByte(),
                0x68.toByte(), // arch
                0x00, // .com/
            )
        val eddystoneUuid = Beacon.EDDYSTONE_SERVICE_UUID
        val ad = createAdvertisement(serviceData = mapOf(eddystoneUuid to BleData(bytes)))
        val beacon = Beacon.parse(ad)

        assertTrue(beacon is Beacon.EddystoneURL)
        val urlBeacon = beacon as Beacon.EddystoneURL
        assertEquals(-18, urlBeacon.txPower)
        assertEquals("https://nousresearch.com/", urlBeacon.url)
    }

    @Test
    fun `parse EddystoneURL http scheme without www`() {
        // http://example.org/
        // Scheme 0x02 = http://, "example" literal, 0x01 = .org/
        val bytes =
            byteArrayOf(
                0x10,
                0x00, // txPower = 0
                0x02, // scheme = http://
                0x65.toByte(),
                0x78.toByte(),
                0x61.toByte(),
                0x6D.toByte(), // exam
                0x70.toByte(),
                0x6C.toByte(),
                0x65.toByte(), // ple
                0x01, // .org/
            )
        val eddystoneUuid = Beacon.EDDYSTONE_SERVICE_UUID
        val ad = createAdvertisement(serviceData = mapOf(eddystoneUuid to BleData(bytes)))
        val beacon = Beacon.parse(ad) as Beacon.EddystoneURL

        assertEquals("http://example.org/", beacon.url)
    }

    @Test
    fun `parse EddystoneURL returns null for unknown scheme`() {
        val bytes = byteArrayOf(0x10, 0x00, 0xFF.toByte()) // invalid scheme
        val eddystoneUuid = Beacon.EDDYSTONE_SERVICE_UUID
        val ad = createAdvertisement(serviceData = mapOf(eddystoneUuid to BleData(bytes)))
        assertNull(Beacon.parse(ad))
    }

    @Test
    fun `parse EddystoneURL returns null for insufficient data`() {
        val bytes = byteArrayOf(0x10, 0x00) // no scheme byte
        val eddystoneUuid = Beacon.EDDYSTONE_SERVICE_UUID
        val ad = createAdvertisement(serviceData = mapOf(eddystoneUuid to BleData(bytes)))
        assertNull(Beacon.parse(ad))
    }

    // --- Eddystone-TLM ---

    @Test
    fun `parse EddystoneTLM from valid service data`() {
        // TLM: battery=3228mV, temp=5.0C, count=42, uptime=360.0s
        val bytes =
            byteArrayOf(
                0x20, // frame type = TLM
                0x00, // version = 0 (unencrypted)
                0x0C.toByte(),
                0x9C.toByte(), // battery = 3228 mV
                0x05,
                0x00, // temperature = 5.0C (1280/256)
                0x00,
                0x00,
                0x00,
                0x2A, // adv count = 42
                0x00,
                0x00,
                0x0E.toByte(),
                0x10, // uptime = 3600 tenths = 360.0s
            )
        val eddystoneUuid = Beacon.EDDYSTONE_SERVICE_UUID
        val ad = createAdvertisement(serviceData = mapOf(eddystoneUuid to BleData(bytes)))
        val beacon = Beacon.parse(ad)

        assertTrue(beacon is Beacon.EddystoneTLM)
        val tlm = beacon as Beacon.EddystoneTLM
        assertEquals(3228, tlm.batteryVoltageMv)
        assertEquals(5.0f, tlm.temperatureCelsius)
        assertEquals(42L, tlm.advertisementCount)
        assertEquals(360.0, tlm.uptimeSeconds)
    }

    @Test
    fun `parse EddystoneTLM handles unsupported battery and temperature`() {
        // TLM with battery=0 (unsupported) and temperature=0x8000 (unsupported)
        val bytes =
            byteArrayOf(
                0x20,
                0x00,
                0x00,
                0x00, // battery = 0 (unsupported)
                0x80.toByte(),
                0x00, // temperature = 0x8000 (unsupported)
                0x00,
                0x00,
                0x00,
                0x01, // adv count = 1
                0x00,
                0x00,
                0x00,
                0x0A, // uptime = 10 tenths = 1.0s
            )
        val eddystoneUuid = Beacon.EDDYSTONE_SERVICE_UUID
        val ad = createAdvertisement(serviceData = mapOf(eddystoneUuid to BleData(bytes)))
        val beacon = Beacon.parse(ad) as Beacon.EddystoneTLM

        assertEquals(null, beacon.batteryVoltageMv)
        assertEquals(null, beacon.temperatureCelsius)
        assertEquals(1L, beacon.advertisementCount)
        assertEquals(1.0, beacon.uptimeSeconds)
    }

    @Test
    fun `parse EddystoneTLM returns null for insufficient data`() {
        val bytes = byteArrayOf(0x20, 0x00, 0x01) // too short
        val eddystoneUuid = Beacon.EDDYSTONE_SERVICE_UUID
        val ad = createAdvertisement(serviceData = mapOf(eddystoneUuid to BleData(bytes)))
        assertNull(Beacon.parse(ad))
    }

    // --- Convenience extensions ---

    @Test
    fun `isBeacon returns true for iBeacon advertisement`() {
        val bytes =
            byteArrayOf(
                0x02,
                0x15.toByte(),
                0xE2.toByte(),
                0xC5.toByte(),
                0x6D.toByte(),
                0xB5.toByte(),
                0xDF.toByte(),
                0xFB.toByte(),
                0x48.toByte(),
                0xD2.toByte(),
                0xB0.toByte(),
                0x60.toByte(),
                0xD0.toByte(),
                0xF5.toByte(),
                0xA7.toByte(),
                0x10.toByte(),
                0x96.toByte(),
                0xE0.toByte(),
                0x00,
                0x01,
                0x00,
                0x01,
                0xC5.toByte(),
            )
        val ad = createAdvertisement(manufacturerData = mapOf(0x004C to BleData(bytes)))
        assertTrue(ad.isBeacon())
    }

    @Test
    fun `isBeacon returns false for non-beacon advertisement`() {
        val ad = createAdvertisement()
        assertTrue(!ad.isBeacon())
    }

    @Test
    fun `parseBeacon extension works identically to Beacon dot parse`() {
        val bytes =
            byteArrayOf(
                0x02,
                0x15.toByte(),
                0xE2.toByte(),
                0xC5.toByte(),
                0x6D.toByte(),
                0xB5.toByte(),
                0xDF.toByte(),
                0xFB.toByte(),
                0x48.toByte(),
                0xD2.toByte(),
                0xB0.toByte(),
                0x60.toByte(),
                0xD0.toByte(),
                0xF5.toByte(),
                0xA7.toByte(),
                0x10.toByte(),
                0x96.toByte(),
                0xE0.toByte(),
                0x00,
                0x01,
                0x00,
                0x01,
                0xC5.toByte(),
            )
        val ad = createAdvertisement(manufacturerData = mapOf(0x004C to BleData(bytes)))

        val viaCompanion = Beacon.parse(ad)
        val viaExtension = ad.parseBeacon()

        assertNotNull(viaCompanion)
        assertNotNull(viaExtension)
        assertEquals(viaCompanion, viaExtension)
    }

    @Test
    fun `parse returns null for empty advertisement`() {
        val ad = createAdvertisement()
        assertNull(Beacon.parse(ad))
    }

    @Test
    fun `parse returns null for wrong Eddystone frame type`() {
        // frame type 0xFF is not recognized
        val bytes = byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x00)
        val eddystoneUuid = Beacon.EDDYSTONE_SERVICE_UUID
        val ad = createAdvertisement(serviceData = mapOf(eddystoneUuid to BleData(bytes)))
        assertNull(Beacon.parse(ad))
    }

    // --- EddystoneUID equality ---

    @Test
    fun `EddystoneUID with identical bytes are equal`() {
        val ns = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val inst = byteArrayOf(11, 12, 13, 14, 15, 16)
        val ad = createAdvertisement()

        val uid1 = Beacon.EddystoneUID(ad, ns.copyOf(), inst.copyOf(), -18)
        val uid2 = Beacon.EddystoneUID(ad, ns.copyOf(), inst.copyOf(), -18)

        assertEquals(uid1, uid2)
        assertEquals(uid1.hashCode(), uid2.hashCode())
    }

    @Test
    fun `EddystoneUID with different instance bytes are not equal`() {
        val ns = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val inst1 = byteArrayOf(11, 12, 13, 14, 15, 16)
        val inst2 = byteArrayOf(11, 12, 13, 14, 15, 99)
        val ad = createAdvertisement()

        val uid1 = Beacon.EddystoneUID(ad, ns.copyOf(), inst1, -18)
        val uid2 = Beacon.EddystoneUID(ad, ns.copyOf(), inst2, -18)

        assertTrue(uid1 != uid2)
    }

    // --- EddystoneTLM negative temperature ---

    @Test
    fun `parse EddystoneTLM handles negative temperature`() {
        // TLM with temp = -5.0C (-1280/256)
        val bytes =
            byteArrayOf(
                0x20,
                0x00,
                0x0B.toByte(),
                0xB8.toByte(), // battery = 3000 mV
                0xFB.toByte(),
                0x00, // temperature = -1280 = -5.0C (signed 8.8)
                0x00,
                0x00,
                0x00,
                0x01, // adv count = 1
                0x00,
                0x00,
                0x00,
                0x64, // uptime = 100 tenths = 10.0s
            )
        val eddystoneUuid = Beacon.EDDYSTONE_SERVICE_UUID
        val ad = createAdvertisement(serviceData = mapOf(eddystoneUuid to BleData(bytes)))
        val beacon = Beacon.parse(ad) as Beacon.EddystoneTLM

        assertEquals(-5.0f, beacon.temperatureCelsius)
    }

    // --- helper ---

    private fun createAdvertisement(
        manufacturerData: Map<Int, BleData> = emptyMap(),
        serviceData: Map<kotlin.uuid.Uuid, BleData> = emptyMap(),
    ): Advertisement =
        Advertisement(
            identifier = Identifier("AA:BB:CC:DD:EE:FF"),
            name = null,
            rssi = -55,
            txPower = null,
            isConnectable = true,
            serviceUuids = emptyList(),
            manufacturerData = manufacturerData,
            serviceData = serviceData,
            timestampNanos = 0L,
        )
}
