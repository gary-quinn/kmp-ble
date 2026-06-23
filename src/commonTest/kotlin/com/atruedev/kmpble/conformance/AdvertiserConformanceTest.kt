package com.atruedev.kmpble.conformance

import com.atruedev.kmpble.server.AdvertiseConfig
import com.atruedev.kmpble.server.AdvertiseMode
import com.atruedev.kmpble.server.AdvertiseTxPower
import com.atruedev.kmpble.server.AdvertiserException
import com.atruedev.kmpble.testing.FakeAdvertiser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Advertiser conformance tests.
 *
 * Verifies the Advertiser interface contract across KMP platforms:
 * start/stop lifecycle, isAdvertising state transitions, error handling,
 * and AdvertiseConfig equality and default behavior.
 *
 * Platform-specific runners (JvmAdvertiserConformanceTest,
 * IosAdvertiserConformanceTest) extend this class and run all
 * inherited tests with the platform's advertiser implementation.
 */
public abstract class AdvertiserConformanceTest {
    /** Factory for advertiser test doubles. Override to inject platform behavior. */
    protected open fun buildAdvertiser(): FakeAdvertiser = FakeAdvertiser()

    private val serviceUuid = Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb")
    private val serviceUuid2 = Uuid.parse("0000180f-0000-1000-8000-00805f9b34fb")

    // --- Lifecycle: isAdvertising initial state ---

    @Test
    fun isAdvertising_starts_false() {
        val advertiser = buildAdvertiser()
        assertFalse(advertiser.isAdvertising.value)
    }

    // --- Lifecycle: start/stop transitions ---

    @Test
    fun startAdvertising_sets_isAdvertising_true() =
        runTest {
            val advertiser = buildAdvertiser()
            advertiser.startAdvertising(AdvertiseConfig(name = "Test"))
            assertTrue(advertiser.isAdvertising.value)
        }

    @Test
    fun stopAdvertising_sets_isAdvertising_false() =
        runTest {
            val advertiser = buildAdvertiser()
            advertiser.startAdvertising(AdvertiseConfig(name = "Test"))
            advertiser.stopAdvertising()
            assertFalse(advertiser.isAdvertising.value)
        }

    @Test
    fun stopAdvertising_when_not_advertising_is_safe() =
        runTest {
            val advertiser = buildAdvertiser()
            advertiser.stopAdvertising()
            assertFalse(advertiser.isAdvertising.value)
        }

    @Test
    fun can_restart_after_stop() =
        runTest {
            val advertiser = buildAdvertiser()

            advertiser.startAdvertising(AdvertiseConfig(name = "First"))
            assertTrue(advertiser.isAdvertising.value)

            advertiser.stopAdvertising()
            assertFalse(advertiser.isAdvertising.value)

            advertiser.startAdvertising(AdvertiseConfig(name = "Second"))
            assertTrue(advertiser.isAdvertising.value)
        }

    // --- Error handling ---

    @Test
    fun double_start_throws_AlreadyAdvertising() =
        runTest {
            val advertiser = buildAdvertiser()
            advertiser.startAdvertising(AdvertiseConfig(name = "Test"))

            assertFailsWith<AdvertiserException.AlreadyAdvertising> {
                advertiser.startAdvertising(AdvertiseConfig(name = "Test2"))
            }
        }

    @Test
    fun start_after_close_throws() =
        runTest {
            val advertiser = buildAdvertiser()
            advertiser.close()

            assertFailsWith<IllegalStateException> {
                advertiser.startAdvertising(AdvertiseConfig(name = "Test"))
            }
        }

    // --- Lifecycle: close behavior ---

    @Test
    fun close_stops_advertising() =
        runTest {
            val advertiser = buildAdvertiser()
            advertiser.startAdvertising(AdvertiseConfig(name = "Test"))
            assertTrue(advertiser.isAdvertising.value)

            advertiser.close()
            assertFalse(advertiser.isAdvertising.value)
        }

    @Test
    fun close_multiple_times_is_safe() =
        runTest {
            val advertiser = buildAdvertiser()
            advertiser.startAdvertising(AdvertiseConfig(name = "Test"))

            advertiser.close()
            advertiser.close()
            assertFalse(advertiser.isAdvertising.value)
        }

    // --- Config capture ---

    @Test
    fun config_is_captured_on_start() =
        runTest {
            val advertiser = buildAdvertiser()
            val config =
                AdvertiseConfig(
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
    fun config_null_before_start() {
        val advertiser = buildAdvertiser()
        assertNull(advertiser.getLastConfig())
    }

    @Test
    fun config_updated_on_restart() =
        runTest {
            val advertiser = buildAdvertiser()

            advertiser.startAdvertising(AdvertiseConfig(name = "First"))
            assertEquals("First", advertiser.getLastConfig()?.name)

            advertiser.stopAdvertising()
            advertiser.startAdvertising(AdvertiseConfig(name = "Second"))
            assertEquals("Second", advertiser.getLastConfig()?.name)
        }

    // --- AdvertiseConfig defaults ---

    @Test
    fun config_defaults() {
        val config = AdvertiseConfig()
        assertNull(config.name)
        assertTrue(config.serviceUuids.isEmpty())
        assertTrue(config.manufacturerData.isEmpty())
        assertTrue(config.connectable)
        assertFalse(config.includeTxPower)
        assertEquals(AdvertiseMode.Balanced, config.mode)
        assertEquals(AdvertiseTxPower.Medium, config.txPower)
    }

    // --- AdvertiseConfig equality ---

    @Test
    fun config_equality_same_fields() {
        val a = AdvertiseConfig(name = "Test", serviceUuids = listOf(serviceUuid))
        val b = AdvertiseConfig(name = "Test", serviceUuids = listOf(serviceUuid))
        assertEquals(a, b)
    }

    @Test
    fun config_inequality_different_name() {
        val a = AdvertiseConfig(name = "Test1")
        val b = AdvertiseConfig(name = "Test2")
        assertNotEquals(a, b)
    }

    @Test
    fun config_inequality_different_service_uuids() {
        val a = AdvertiseConfig(serviceUuids = listOf(serviceUuid))
        val b = AdvertiseConfig(serviceUuids = listOf(serviceUuid2))
        assertNotEquals(a, b)
    }

    @Test
    fun config_inequality_different_connectable() {
        val a = AdvertiseConfig(connectable = true)
        val b = AdvertiseConfig(connectable = false)
        assertNotEquals(a, b)
    }

    @Test
    fun config_inequality_different_mode() {
        val a = AdvertiseConfig(mode = AdvertiseMode.LowPower)
        val b = AdvertiseConfig(mode = AdvertiseMode.LowLatency)
        assertNotEquals(a, b)
    }

    @Test
    fun config_inequality_different_tx_power() {
        val a = AdvertiseConfig(txPower = AdvertiseTxPower.Low)
        val b = AdvertiseConfig(txPower = AdvertiseTxPower.High)
        assertNotEquals(a, b)
    }

    @Test
    fun config_equality_manufacturer_data_same_keys_and_values() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val a = AdvertiseConfig(manufacturerData = mapOf(1 to data, 2 to byteArrayOf(0x04)))
        val b = AdvertiseConfig(manufacturerData = mapOf(1 to data.copyOf(), 2 to byteArrayOf(0x04)))
        assertEquals(a, b)
    }

    @Test
    fun config_inequality_manufacturer_data_different_keys() {
        val a = AdvertiseConfig(manufacturerData = mapOf(1 to byteArrayOf(0x01)))
        val b = AdvertiseConfig(manufacturerData = mapOf(2 to byteArrayOf(0x01)))
        assertNotEquals(a, b)
    }

    @Test
    fun config_inequality_manufacturer_data_different_values() {
        val a = AdvertiseConfig(manufacturerData = mapOf(1 to byteArrayOf(0x01)))
        val b = AdvertiseConfig(manufacturerData = mapOf(1 to byteArrayOf(0x02)))
        assertNotEquals(a, b)
    }

    // --- AdvertiseConfig hashCode consistency ---

    @Test
    fun config_hash_code_consistent_with_equality() {
        val a = AdvertiseConfig(name = "Test", serviceUuids = listOf(serviceUuid))
        val b = AdvertiseConfig(name = "Test", serviceUuids = listOf(serviceUuid))
        assertEquals(a.hashCode(), b.hashCode())
    }

    // --- AdvertiseMode enum coverage ---

    @Test
    fun advertise_mode_has_all_values() {
        val modes = AdvertiseMode.values()
        assertEquals(3, modes.size)
        assertTrue(modes.contains(AdvertiseMode.LowPower))
        assertTrue(modes.contains(AdvertiseMode.Balanced))
        assertTrue(modes.contains(AdvertiseMode.LowLatency))
    }

    // --- AdvertiseTxPower enum coverage ---

    @Test
    fun advertise_tx_power_has_all_values() {
        val powers = AdvertiseTxPower.values()
        assertEquals(4, powers.size)
        assertTrue(powers.contains(AdvertiseTxPower.UltraLow))
        assertTrue(powers.contains(AdvertiseTxPower.Low))
        assertTrue(powers.contains(AdvertiseTxPower.Medium))
        assertTrue(powers.contains(AdvertiseTxPower.High))
    }

    // --- isAdvertising is a StateFlow ---

    @Test
    fun isAdvertising_is_StateFlow() {
        val advertiser = buildAdvertiser()
        // Verify the type contract: isAdvertising must be observable and reflect initial state
        assertFalse(advertiser.isAdvertising.value)
    }

    // --- Full lifecycle: start -> advertise -> stop -> close ---

    @Test
    fun full_lifecycle_start_stop_close() =
        runTest {
            val advertiser = buildAdvertiser()

            // Initial state
            assertFalse(advertiser.isAdvertising.value)
            assertNull(advertiser.getLastConfig())

            // Start advertising
            val config =
                AdvertiseConfig(
                    name = "FullLifecycle",
                    serviceUuids = listOf(serviceUuid),
                    connectable = false,
                    mode = AdvertiseMode.LowPower,
                    txPower = AdvertiseTxPower.Low,
                )
            advertiser.startAdvertising(config)
            assertTrue(advertiser.isAdvertising.value)
            assertEquals(config, advertiser.getLastConfig())

            // Stop advertising
            advertiser.stopAdvertising()
            assertFalse(advertiser.isAdvertising.value)

            // Close
            advertiser.close()
            assertFalse(advertiser.isAdvertising.value)
        }

    // --- Multiple service UUIDs ---

    @Test
    fun config_with_multiple_service_uuids() =
        runTest {
            val advertiser = buildAdvertiser()
            val config = AdvertiseConfig(serviceUuids = listOf(serviceUuid, serviceUuid2))

            advertiser.startAdvertising(config)
            assertEquals(listOf(serviceUuid, serviceUuid2), advertiser.getLastConfig()?.serviceUuids)
        }

    // --- Beacon mode: connectable false ---

    @Test
    fun beacon_mode_connectable_false() =
        runTest {
            val advertiser = buildAdvertiser()
            val config =
                AdvertiseConfig(
                    name = "Beacon",
                    connectable = false,
                    mode = AdvertiseMode.LowPower,
                )

            advertiser.startAdvertising(config)
            assertFalse(advertiser.getLastConfig()!!.connectable)
        }

    // --- Full config with all fields ---

    @Test
    fun full_config_all_fields_captured() =
        runTest {
            val advertiser = buildAdvertiser()
            val config =
                AdvertiseConfig(
                    name = "FullConfig",
                    serviceUuids = listOf(serviceUuid, serviceUuid2),
                    manufacturerData = mapOf(1 to byteArrayOf(0x01, 0x02), 2 to byteArrayOf(0x03)),
                    connectable = true,
                    includeTxPower = true,
                    mode = AdvertiseMode.LowLatency,
                    txPower = AdvertiseTxPower.High,
                )

            advertiser.startAdvertising(config)

            val captured = advertiser.getLastConfig()!!
            assertEquals("FullConfig", captured.name)
            assertEquals(listOf(serviceUuid, serviceUuid2), captured.serviceUuids)
            assertEquals(2, captured.manufacturerData.size)
            assertTrue(captured.connectable)
            assertTrue(captured.includeTxPower)
            assertEquals(AdvertiseMode.LowLatency, captured.mode)
            assertEquals(AdvertiseTxPower.High, captured.txPower)
        }
}
