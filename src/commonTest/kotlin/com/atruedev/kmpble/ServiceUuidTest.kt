package com.atruedev.kmpble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class ServiceUuidTest {

    @Test
    fun heartRateUuidMatchesSigSpec() {
        assertEquals("0000180d-0000-1000-8000-00805f9b34fb", ServiceUuid.HEART_RATE.toString())
    }

    @Test
    fun batteryUuidMatchesSigSpec() {
        assertEquals("0000180f-0000-1000-8000-00805f9b34fb", ServiceUuid.BATTERY.toString())
    }

    @Test
    fun deviceInfoUuidMatchesSigSpec() {
        assertEquals("0000180a-0000-1000-8000-00805f9b34fb", ServiceUuid.DEVICE_INFORMATION.toString())
    }

    @Test
    fun nordicUartIsNonSigUuid() {
        // Nordic UART is a full 128-bit UUID, not a 16-bit SIG UUID
        assertNotEquals("0000", ServiceUuid.NORDIC_UART.toString().substring(0, 4))
    }

    @Test
    fun allListIsNotEmpty() {
        assertTrue(ServiceUuid.ALL.size >= 53, "Expected at least 53 UUIDs, got ${ServiceUuid.ALL.size}")
    }

    @Test
    fun allUuidsAreUnique() {
        val uuids = ServiceUuid.ALL
        assertEquals(uuids.size, uuids.toSet().size, "Duplicate UUIDs found in ServiceUuid.ALL")
    }
}
