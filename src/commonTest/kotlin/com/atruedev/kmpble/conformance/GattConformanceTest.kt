package com.atruedev.kmpble.conformance

import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.scanner.uuidFrom
import com.atruedev.kmpble.testing.FakePeripheralBuilder
import com.atruedev.kmpble.testing.emitObservationValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * GATT operation conformance tests.
 *
 * Verifies service discovery, characteristic read/write, and notification
 * observation across KMP platforms.
 */
public abstract class GattConformanceTest : BleConformanceTest() {
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
            // Write completes without throw - success
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
}
