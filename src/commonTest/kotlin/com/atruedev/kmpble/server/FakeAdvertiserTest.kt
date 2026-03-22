package com.atruedev.kmpble.server

import com.atruedev.kmpble.testing.FakeAdvertiser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class FakeAdvertiserTest {

    private val serviceUuid = Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb")

    @Test
    fun startAdvertising_sets_isAdvertising_true() = runTest {
        val advertiser = FakeAdvertiser()
        assertFalse(advertiser.isAdvertising.value)

        advertiser.startAdvertising(AdvertiseConfig(name = "Test"))
        assertTrue(advertiser.isAdvertising.value)
    }

    @Test
    fun stopAdvertising_sets_isAdvertising_false() = runTest {
        val advertiser = FakeAdvertiser()

        advertiser.startAdvertising(AdvertiseConfig(name = "Test"))
        assertTrue(advertiser.isAdvertising.value)

        advertiser.stopAdvertising()
        assertFalse(advertiser.isAdvertising.value)
    }

    @Test
    fun double_start_throws_AlreadyAdvertising() = runTest {
        val advertiser = FakeAdvertiser()

        advertiser.startAdvertising(AdvertiseConfig(name = "Test"))
        assertFailsWith<AdvertiserException.AlreadyAdvertising> {
            advertiser.startAdvertising(AdvertiseConfig(name = "Test2"))
        }
    }

    @Test
    fun getLastConfig_returns_config() = runTest {
        val advertiser = FakeAdvertiser()
        val config = AdvertiseConfig(
            name = "MyDevice",
            serviceUuids = listOf(serviceUuid),
            connectable = true,
            includeTxPower = true,
        )

        advertiser.startAdvertising(config)

        val captured = advertiser.getLastConfig()!!
        assertEquals("MyDevice", captured.name)
        assertEquals(listOf(serviceUuid), captured.serviceUuids)
        assertTrue(captured.connectable)
        assertTrue(captured.includeTxPower)
    }

    @Test
    fun getLastConfig_returns_null_before_start() {
        val advertiser = FakeAdvertiser()
        assertNull(advertiser.getLastConfig())
    }

    @Test
    fun close_stops_advertising() = runTest {
        val advertiser = FakeAdvertiser()

        advertiser.startAdvertising(AdvertiseConfig(name = "Test"))
        assertTrue(advertiser.isAdvertising.value)

        advertiser.close()
        assertFalse(advertiser.isAdvertising.value)
    }

    @Test
    fun stopAdvertising_when_not_advertising_is_safe() = runTest {
        val advertiser = FakeAdvertiser()
        advertiser.stopAdvertising()
        assertFalse(advertiser.isAdvertising.value)
    }

    @Test
    fun close_multiple_times_is_safe() = runTest {
        val advertiser = FakeAdvertiser()
        advertiser.startAdvertising(AdvertiseConfig(name = "Test"))

        advertiser.close()
        advertiser.close()
        assertFalse(advertiser.isAdvertising.value)
    }

    @Test
    fun can_restart_after_stop() = runTest {
        val advertiser = FakeAdvertiser()

        advertiser.startAdvertising(AdvertiseConfig(name = "First"))
        advertiser.stopAdvertising()
        assertFalse(advertiser.isAdvertising.value)

        advertiser.startAdvertising(AdvertiseConfig(name = "Second"))
        assertTrue(advertiser.isAdvertising.value)
        assertEquals("Second", advertiser.getLastConfig()?.name)
    }
}
