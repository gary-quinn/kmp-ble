package com.atruedev.kmpble.scanner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class BlueZAdvertisementParserTest {
    @Test
    fun mapsCoreFields() {
        val heartRate = Uuid.parse("0000180D-0000-1000-8000-00805F9B34FB")
        val snapshot =
            BlueZDeviceSnapshot(
                dbusPath = "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF",
                address = "AA:BB:CC:DD:EE:FF",
                name = "HR Sensor",
                rssi = -62,
                txPower = -8,
                serviceUuids = listOf(heartRate.toString()),
                manufacturerData = mapOf(0x004C to byteArrayOf(0x02, 0x15)),
                serviceData = emptyMap(),
                advertisingFlags = byteArrayOf(0x06),
            )

        val ad = snapshot.toAdvertisement()

        assertEquals("AA:BB:CC:DD:EE:FF", ad.identifier.value)
        assertEquals("HR Sensor", ad.name)
        assertEquals(-62, ad.rssi)
        assertEquals(-8, ad.txPower)
        assertEquals(listOf(heartRate), ad.serviceUuids)
        val manufacturerEntry = ad.manufacturerData.entries.single()
        assertEquals(0x004C, manufacturerEntry.key)
        val payload = manufacturerEntry.value.toByteArray()
        assertEquals(listOf(0x02.toByte(), 0x15.toByte()), payload.toList())
        assertTrue(ad.isConnectable)
        assertEquals(snapshot.dbusPath, ad.platformContext)
    }

    @Test
    fun mergePrefersNewRssiAndKeepsExistingName() {
        val base =
            BlueZDeviceSnapshot(
                dbusPath = "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF",
                address = "AA:BB:CC:DD:EE:FF",
                name = "Sensor",
                rssi = -70,
                txPower = null,
                serviceUuids = emptyList(),
                manufacturerData = emptyMap(),
                serviceData = emptyMap(),
                advertisingFlags = null,
            )
        val update =
            base.copy(
                name = null,
                rssi = -55,
            )

        val merged = base.merge(update)
        assertEquals("Sensor", merged.name)
        assertEquals(-55, merged.rssi)
    }

    @Test
    fun snapshotFromPropertyMapParsesManufacturerData() {
        val snapshot =
            snapshotFromPropertyMap(
                dbusPath = "/org/bluez/hci0/dev_11_22_33_44_55_66",
                properties =
                    mapOf(
                        "Address" to "11:22:33:44:55:66",
                        "RSSI" to (-48).toShort(),
                        "ManufacturerData" to mapOf(0x004C.toShort() to byteArrayOf(0x10)),
                    ),
            )

        requireNotNull(snapshot)
        assertEquals("11:22:33:44:55:66", snapshot.address)
        assertEquals(-48, snapshot.rssi)
        assertEquals(0x004C, snapshot.manufacturerData.keys.single())
    }

    @Test
    fun connectableWhenNoFlagsDefaultsFalse() {
        val snapshot =
            BlueZDeviceSnapshot(
                dbusPath = "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF",
                address = "AA:BB:CC:DD:EE:FF",
                name = null,
                rssi = -80,
                txPower = null,
                serviceUuids = emptyList(),
                manufacturerData = emptyMap(),
                serviceData = emptyMap(),
                advertisingFlags = null,
            )

        assertFalse(snapshot.toAdvertisement().isConnectable)
    }

    @Test
    fun nonDiscoverableFlagsMarkNotConnectable() {
        val snapshot =
            BlueZDeviceSnapshot(
                dbusPath = "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF",
                address = "AA:BB:CC:DD:EE:FF",
                name = null,
                rssi = -80,
                txPower = null,
                serviceUuids = emptyList(),
                manufacturerData = emptyMap(),
                serviceData = emptyMap(),
                advertisingFlags = byteArrayOf(0x00),
            )

        assertFalse(snapshot.toAdvertisement().isConnectable)
    }

    @Test
    fun advertisingFlagsFromArrayListProperty() {
        val flags = arrayListOf<Any>(6, 0)
        val snapshot =
            snapshotFromPropertyMap(
                dbusPath = "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF",
                properties =
                    mapOf(
                        "Address" to "AA:BB:CC:DD:EE:FF",
                        "RSSI" to -55,
                        "AdvertisingFlags" to flags,
                    ),
            )

        requireNotNull(snapshot)
        assertEquals(listOf(6.toByte(), 0.toByte()), snapshot.advertisingFlags!!.toList())
        assertTrue(snapshot.toAdvertisement().isConnectable)
    }

    @Test
    fun manufacturerDataFromArrayListPayload() {
        val payload = arrayListOf<Number>(0x02, 0x15)
        val snapshot =
            snapshotFromPropertyMap(
                dbusPath = "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF",
                properties =
                    mapOf(
                        "Address" to "AA:BB:CC:DD:EE:FF",
                        "RSSI" to -60,
                        "ManufacturerData" to mapOf(0x004C.toShort() to payload),
                    ),
            )

        requireNotNull(snapshot)
        assertEquals(
            listOf(0x02.toByte(), 0x15.toByte()),
            snapshot.manufacturerData.values
                .single()
                .toList(),
        )
        assertEquals(
            0x004C,
            snapshot
                .toAdvertisement()
                .manufacturerData.keys
                .single(),
        )
    }

    @Test
    fun serviceDataFromArrayListPayload() {
        val serviceUuid = "0000180D-0000-1000-8000-00805F9B34FB"
        val payload = arrayListOf<Number>(0x01)
        val snapshot =
            snapshotFromPropertyMap(
                dbusPath = "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF",
                properties =
                    mapOf(
                        "Address" to "AA:BB:CC:DD:EE:FF",
                        "RSSI" to -60,
                        "ServiceData" to mapOf(serviceUuid to payload),
                    ),
            )

        requireNotNull(snapshot)
        assertEquals(
            listOf(0x01.toByte()),
            snapshot.serviceData.values
                .single()
                .toList(),
        )
        assertEquals(
            Uuid.parse(serviceUuid),
            snapshot
                .toAdvertisement()
                .serviceData.keys
                .single(),
        )
    }

    @Test
    fun changedPropertiesAcceptArrayListAdvertisingFlags() {
        val delta =
            snapshotFromChangedProperties(
                dbusPath = "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF",
                address = "AA:BB:CC:DD:EE:FF",
                changed =
                    mapOf(
                        "AdvertisingFlags" to arrayListOf<Any>(6),
                        "RSSI" to -50,
                    ),
            )

        assertEquals(listOf(6.toByte()), delta.advertisingFlags!!.toList())
        assertEquals(-50, delta.rssi)
    }
}
