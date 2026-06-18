package com.atruedev.kmpble.conformance

import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.scanner.ScanEvent
import com.atruedev.kmpble.scanner.uuidFrom
import com.atruedev.kmpble.testing.FakePeripheralBuilder
import com.atruedev.kmpble.testing.FakeScanner
import com.atruedev.kmpble.testing.FakeScannerBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Shared conformance test suite for core BLE flows.
 *
 * Subclass in platform-specific test source sets (jvmTest, iosTest) to verify
 * behavioral consistency across KMP targets. Tests use [FakeScanner] and
 * [FakePeripheralBuilder] for deterministic, virtual-time execution.
 *
 * ## Platform subclasses
 * ```
 * // jvmTest
 * class JvmBleConformanceTest : BleConformanceTest()
 *
 * // iosTest
 * class IosBleConformanceTest : BleConformanceTest()
 * ```
 *
 * Override [buildScanner] or [buildPeripheral] to inject platform-specific
 * behavior variations.
 */
public abstract class BleConformanceTest {
    /** Factory for scanner instances. Override to inject platform behavior. */
    protected open fun buildScanner(block: FakeScannerBuilder.() -> Unit = {}): FakeScanner = FakeScanner(block)

    /** Factory for peripheral builder. Override to inject platform behavior. */
    protected open fun buildPeripheral(block: FakePeripheralBuilder.() -> Unit = {}) =
        FakePeripheralBuilder().apply(block).build()

    // -- Scan conformance --

    @Test
    fun `scan emits found event for advertising peripheral`() =
        runTest {
            val scanner =
                buildScanner {
                    advertisement {
                        identifier("AA:BB:CC:DD:EE:FF")
                        name("ConformanceDevice")
                        rssi(-50)
                        serviceUuids("180d")
                    }
                }

            val event =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .take(1)
                    .first()

            assertEquals("ConformanceDevice", event.advertisement.name)
            assertTrue(event.advertisement.rssi <= 0)
            scanner.close()
        }

    // -- Connection conformance --

    @Test
    fun `connect transitions peripheral to connected state`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }

            peripheral.connect(ConnectionOptions())

            assertTrue(peripheral.state.value is State.Connected)
            peripheral.close()
        }

    @Test
    fun `disconnect transitions peripheral to disconnecting state`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }

            peripheral.connect(ConnectionOptions())
            peripheral.disconnect()

            val state = peripheral.state.value
            assertTrue(
                state is State.Disconnecting || state is State.Disconnected,
                "Expected Disconnecting or Disconnected, got $state",
            )
            peripheral.close()
        }

    // -- GATT conformance --

    @Test
    fun `service discovery returns configured services`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("1800") {
                        characteristic("2a00") { properties(read = true) }
                    }
                    service("180a") {
                        characteristic("2a29") { properties(read = true) }
                    }
                }

            peripheral.connect(ConnectionOptions())
            val services = peripheral.refreshServices()

            assertEquals(2, services.size, "Should discover both configured services")
            assertEquals(
                uuidFrom("1800"),
                services[0].uuid,
            )
            assertEquals(
                uuidFrom("180a"),
                services[1].uuid,
            )
            peripheral.close()
        }

    @Test
    fun `gatt read returns configured value`() =
        runTest {
            val expectedData = byteArrayOf(0x4E, 0x6F, 0x75, 0x73)
            val peripheral =
                buildPeripheral {
                    service("180a") {
                        characteristic("2a29") {
                            properties(read = true)
                            onRead { expectedData }
                        }
                    }
                }

            peripheral.connect(ConnectionOptions())
            peripheral.refreshServices()
            val char =
                peripheral.services.value!!
                    .first { it.uuid == uuidFrom("180a") }
                    .characteristics
                    .first { it.uuid == uuidFrom("2a29") }

            val result = peripheral.read(char)

            assertContentEquals(expectedData, result)
            peripheral.close()
        }

    @Test
    fun `gatt write with response completes`() =
        runTest {
            val writeData = byteArrayOf(0x01, 0x02, 0x03)
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(write = true) }
                    }
                }

            peripheral.connect(ConnectionOptions())
            peripheral.refreshServices()
            val char =
                peripheral.services.value!!
                    .first { it.uuid == uuidFrom("180d") }
                    .characteristics
                    .first { it.uuid == uuidFrom("2a37") }

            peripheral.write(char, writeData, WriteType.WithResponse)
            // Write completes without throw — success
            peripheral.close()
        }

    @Test
    fun `notification observation receives emitted value`() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            val peripheral =
                FakePeripheralBuilder()
                    .observationDispatcher(dispatcher)
                    .apply {
                        service("180d") {
                            characteristic("2a37") { properties(notify = true) }
                        }
                    }.build()

            peripheral.connect(ConnectionOptions())
            scheduler.runCurrent()
            peripheral.refreshServices()
            val char =
                peripheral.services.value!!
                    .first { it.uuid == uuidFrom("180d") }
                    .characteristics
                    .first { it.uuid == uuidFrom("2a37") }

            val observations = mutableListOf<Observation>()
            val job: Job =
                backgroundScope.launch {
                    peripheral
                        .observe(char, BackpressureStrategy.Unbounded)
                        .collect { observations.add(it) }
                }
            scheduler.runCurrent()

            peripheral.emitObservationValue("180d", "2a37", byteArrayOf(0x42))
            scheduler.runCurrent()

            val value = observations.filterIsInstance<Observation.Value>().single()
            assertContentEquals(byteArrayOf(0x42), value.data)

            job.cancel()
            peripheral.close()
        }
    }

    // -- Reconnection conformance --

    @Test
    fun `reconnect after disconnect restores connected state`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }

            peripheral.connect(ConnectionOptions())
            assertTrue(peripheral.state.value is State.Connected)

            peripheral.disconnect()
            peripheral.connect(ConnectionOptions())
            assertTrue(peripheral.state.value is State.Connected)

            peripheral.close()
        }

    // -- State consistency --

    @Test
    fun `services property non-null only after discovery`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("1800") {
                        characteristic("2a00") { properties(read = true) }
                    }
                }

            // Before discovery, services may be null or empty
            peripheral.connect(ConnectionOptions())
            peripheral.refreshServices()

            val services = peripheral.services.value
            assertNotNull(services, "Services should be non-null after discovery")
            assertTrue(services.isNotEmpty(), "Services should not be empty after discovery")
            peripheral.close()
        }
}
