package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.scanner.ScanEvent
import com.atruedev.kmpble.testing.FakePeripheralBuilder
import com.atruedev.kmpble.testing.IntegrationTestFixtures
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class BleIntegrationTest {
    @Test
    fun integrationFullScanConnectDiscoverReadObserveDisconnectFlow() =
        runBlocking {
            val peripheral =
                FakePeripheralBuilder()
                    .apply {
                        identifier = Identifier("AA:BB:CC:DD:EE:FF")
                        service("180d") {
                            characteristic("2a37") {
                                // Heart Rate Measurement
                                properties(notify = true, read = true)
                                onRead { byteArrayOf(0x06, 0x42, 0x00) } // HR = 66 bpm
                                onObserve {
                                    kotlinx.coroutines.flow.flow {
                                        emit(byteArrayOf(0x06, 0x42, 0x00)) // 66 bpm
                                        emit(byteArrayOf(0x06, 0x4A, 0x00)) // 74 bpm
                                        emit(byteArrayOf(0x06, 0x50, 0x00)) // 80 bpm
                                    }
                                }
                            }
                            characteristic("2a39") {
                                // Heart Rate Control Point
                                properties(write = true)
                                onWrite { data, _ -> }
                            }
                        }
                    }.build()

            // === STEP 1: SCAN ===
            val found =
                IntegrationTestFixtures.scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .take(1)
                    .first()
            assertEquals("IntegrationTestDevice", found.advertisement.name)
            assertEquals("AA:BB:CC:DD:EE:FF", found.advertisement.identifier.value)

            // === STEP 2: CONNECT ===
            peripheral.connect(ConnectionOptions())
            assertNotNull(peripheral.services.value)

            // === STEP 3: DISCOVER SERVICES ===
            val services = peripheral.services.value!!
            assertEquals(1, services.size)
            val hrService =
                services.first { it.uuid.toString() == "0000180d-0000-1000-8000-00805f9b34fb" }
            assertEquals(2, hrService.characteristics.size)

            // === STEP 4: READ CHARACTERISTIC ===
            val hrChar =
                hrService.characteristics.first { it.uuid.toString() == "00002a37-0000-1000-8000-00805f9b34fb" }
            val readValue = peripheral.read(hrChar)
            assertEquals(0x06, readValue[0].toInt())
            assertEquals(0x42, readValue[1].toInt())
            assertEquals(0x00, readValue[2].toInt())

            // === STEP 5: OBSERVE NOTIFICATIONS ===
            val observations = mutableListOf<Observation>()
            val observeJob =
                launch {
                    peripheral
                        .observe(hrChar, BackpressureStrategy.Unbounded)
                        .collect { observations.add(it) }
                }

            delay(10) // Wait for observation to be registered (CCCD enabled)

            // Emit simulated heart rate notifications
            peripheral.emitObservationValue("180d", "2a37", byteArrayOf(0x06, 0x42, 0x00))
            peripheral.emitObservationValue("180d", "2a37", byteArrayOf(0x06, 0x4A, 0x00))
            peripheral.emitObservationValue("180d", "2a37", byteArrayOf(0x06, 0x50, 0x00))

            delay(50)
            observeJob.cancel()

            // Verify observations received
            val valueObservations = observations.filterIsInstance<Observation.Value>()
            assertEquals(3, valueObservations.size)
            assertEquals(byteArrayOf(0x06, 0x42, 0x00), valueObservations[0].data)
            assertEquals(byteArrayOf(0x06, 0x4A, 0x00), valueObservations[1].data)
            assertEquals(byteArrayOf(0x06, 0x50, 0x00), valueObservations[2].data)

            // === STEP 6: DISCONNECT ===
            peripheral.disconnect()

            // Peripheral should be in disconnected state
            assertIs<State.Disconnected.ByRequest>(peripheral.state.value)

            // Cleanup
            IntegrationTestFixtures.scanner.close()
            peripheral.close()
        }

    @Test
    fun integrationReconnectionReEnablesCccdForActiveObservations() =
        runBlocking {
            val scanner =
                com.atruedev.kmpble.testing.FakeScanner {
                    advertisement {
                        identifier("11:22:33:44:55:66")
                        name("ReconnectDevice")
                        rssi(-60)
                        serviceUuids("180a")
                    }
                }

            val peripheral =
                FakePeripheralBuilder()
                    .apply {
                        identifier = Identifier("11:22:33:44:55:66")
                        service("180a") {
                            characteristic("2a29") {
                                // Manufacturer Name
                                properties(notify = true)
                                onObserve {
                                    kotlinx.coroutines.flow.flow {
                                        emit("AcmeCorp".encodeToByteArray())
                                    }
                                }
                            }
                        }
                    }.build()

            // Scan and connect
            val found =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .take(1)
                    .first()
            assertEquals("ReconnectDevice", found.advertisement.name)

            peripheral.connect(ConnectionOptions())
            val char =
                peripheral.services.value!!
                    .first { it.uuid.toString() == "0000180a-0000-1000-8000-00805f9b34fb" }
                    .characteristics
                    .first { it.uuid.toString() == "00002a29-0000-1000-8000-00805f9b34fb" }

            // Start observing
            val observations = mutableListOf<Observation>()
            val observeJob =
                launch {
                    peripheral
                        .observe(char, BackpressureStrategy.Unbounded)
                        .collect { observations.add(it) }
                }
            delay(10)

            // First notification
            peripheral.emitObservationValue("180a", "2a29", "AcmeCorp".encodeToByteArray())
            delay(20)

            // Simulate disconnect (simulated, not permanent)
            peripheral.simulateDisconnect()
            delay(20)

            // Verify observation got Disconnected event but not completed
            val disconnectedEvents = observations.filterIsInstance<Observation.Disconnected>()
            assertEquals(1, disconnectedEvents.size)

            // Simulate reconnect
            peripheral.simulateReconnect()
            delay(20)

            // Emit another notification after reconnect
            peripheral.emitObservationValue("180a", "2a29", "AcmeCorp V2".encodeToByteArray())
            delay(20)

            val valueObservations = observations.filterIsInstance<Observation.Value>()
            assertEquals(2, valueObservations.size)
            assertEquals("AcmeCorp".encodeToByteArray(), valueObservations[0].data)
            assertEquals("AcmeCorp V2".encodeToByteArray(), valueObservations[1].data)

            observeJob.cancel()
            scanner.close()
            peripheral.close()
        }
}
