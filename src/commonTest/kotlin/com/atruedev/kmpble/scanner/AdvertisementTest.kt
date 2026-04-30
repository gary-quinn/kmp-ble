package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class AdvertisementTest {
    private fun ad(rawAdvertising: RawAdvertising? = null) =
        Advertisement(
            identifier = Identifier("AA:BB:CC:DD:EE:FF"),
            name = null,
            rssi = -60,
            txPower = null,
            isConnectable = true,
            serviceUuids = emptyList(),
            manufacturerData = emptyMap(),
            serviceData = emptyMap(),
            timestampNanos = 0L,
            rawAdvertising = rawAdvertising,
        )

    @Test
    fun rawAdvertisingDefaultsToNull() {
        assertNull(ad().rawAdvertising)
    }

    @Test
    fun equalityIncludesOnAirBytes() {
        val bytes = byteArrayOf(0x02, 0x01, 0x06, 0x03, 0x03, 0x0A, 0x18)
        val a = ad(RawAdvertising.OnAir(BleData(bytes)))
        val b = ad(RawAdvertising.OnAir(BleData(bytes.copyOf())))

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun differentBytesBreakEquality() {
        val a = ad(RawAdvertising.OnAir(BleData(byteArrayOf(0x02, 0x01, 0x06))))
        val b = ad(RawAdvertising.OnAir(BleData(byteArrayOf(0x02, 0x01, 0x05))))

        assertNotEquals(a, b)
    }

    @Test
    fun nullDiffersFromPresent() {
        val a = ad(rawAdvertising = null)
        val b = ad(RawAdvertising.OnAir(BleData(byteArrayOf(0x02, 0x01, 0x06))))

        assertNotEquals(a, b)
    }

    @Test
    fun onAirDiffersFromReconstructedEvenWithSameBytes() {
        val bytes = byteArrayOf(0x02, 0x01, 0x06)
        val a = ad(RawAdvertising.OnAir(BleData(bytes)))
        val b = ad(RawAdvertising.Reconstructed(BleData(bytes.copyOf())))

        assertNotEquals(a, b)
    }

    @Test
    fun copyPreservesRawAdvertising() {
        val raw = RawAdvertising.OnAir(BleData(byteArrayOf(0x02, 0x01, 0x06)))
        val original = ad(raw)
        val updated = original.copy(rssi = -42)

        assertEquals(raw, updated.rawAdvertising)
        assertEquals(-42, updated.rssi)
    }
}
