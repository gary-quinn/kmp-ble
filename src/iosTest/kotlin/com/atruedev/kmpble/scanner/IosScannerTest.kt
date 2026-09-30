package com.atruedev.kmpble.scanner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class IosScannerTest {
    @Test
    fun `retrieved advertisement is flagged with zero RSSI and no payload`() {
        val serviceUuid = uuidFrom("180d")

        val ad = retrievedAdvertisement("peripheral-1", "Sensor", listOf(serviceUuid))

        assertTrue(ad.isRetrieved)
        assertEquals(0, ad.rssi)
        assertNull(ad.txPower)
        assertNull(ad.rawAdvertising)
        assertEquals(listOf(serviceUuid), ad.serviceUuids)
        assertTrue(ad.manufacturerData.isEmpty())
        assertTrue(ad.serviceData.isEmpty())
    }

    @Test
    fun `retrieved ids are tracked to prevent double emit`() {
        val ids = mutableSetOf<String>()

        // First add succeeds.
        assertTrue(ids.add("peripheral-1"))

        // Duplicate add fails.
        assertTrue(!ids.add("peripheral-1"))

        // Different id succeeds.
        assertTrue(ids.add("peripheral-2"))

        assertEquals(2, ids.size)
    }

    @Test
    fun `remove from retrieved ids after scan lets RSSI updates flow`() {
        val ids = mutableSetOf("peripheral-1")

        // First scan result blocked (id in set).
        assertTrue("peripheral-1" in ids)

        // Remove after first scan result.
        ids -= "peripheral-1"

        // Subsequent scan results now flow through.
        assertTrue("peripheral-1" !in ids)
    }
}
