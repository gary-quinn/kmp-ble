package com.atruedev.kmpble.observation

import app.cash.turbine.test
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.scanner.uuidFrom
import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class ObservationReconnectionTest {
    private val testServiceUuid = uuidFrom("180d")
    private val testCharUuid = uuidFrom("2a37")

    private fun createPeripheral(): FakePeripheral =
        FakePeripheral {
            service("180d") {
                characteristic("2a37") { properties(notify = true) }
                characteristic("2a38") { properties(read = true) }
            }
        }

    @Test
    fun observeSurvivesDisconnectAndReconnect() =
        runTest {
            val peripheral = createPeripheral()
            peripheral.connect()

            val char = peripheral.findCharacteristic(testServiceUuid, testCharUuid)!!

            peripheral.observe(char, BackpressureStrategy.Unbounded).test {
                peripheral.emitObservationValue(testServiceUuid, testCharUuid, byteArrayOf(0x01))
                peripheral.emitObservationValue(testServiceUuid, testCharUuid, byteArrayOf(0x02))

                val v1 = awaitItem()
                assertIs<Observation.Value>(v1)
                assertContentEquals(byteArrayOf(0x01), v1.data)

                val v2 = awaitItem()
                assertIs<Observation.Value>(v2)
                assertContentEquals(byteArrayOf(0x02), v2.data)

                peripheral.simulateDisconnect()
                assertIs<Observation.Disconnected>(awaitItem())

                peripheral.simulateReconnect()
                peripheral.emitObservationValue(testServiceUuid, testCharUuid, byteArrayOf(0x03))

                val v3 = awaitItem()
                assertIs<Observation.Value>(v3)
                assertContentEquals(byteArrayOf(0x03), v3.data)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun observeValuesTransparentlyReconnects() =
        runTest {
            val peripheral = createPeripheral()
            peripheral.connect()

            val char = peripheral.findCharacteristic(testServiceUuid, testCharUuid)!!

            peripheral.observeValues(char, BackpressureStrategy.Unbounded).test {
                peripheral.emitObservationValue(testServiceUuid, testCharUuid, byteArrayOf(0x01))
                assertContentEquals(byteArrayOf(0x01), awaitItem())

                peripheral.simulateDisconnect()
                // observeValues does not emit during disconnect — no awaitItem here

                peripheral.simulateReconnect()
                peripheral.emitObservationValue(testServiceUuid, testCharUuid, byteArrayOf(0x02))
                assertContentEquals(byteArrayOf(0x02), awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun observeCompletesOnPermanentDisconnect() =
        runTest {
            val peripheral = createPeripheral()
            peripheral.connect()

            val char = peripheral.findCharacteristic(testServiceUuid, testCharUuid)!!

            peripheral.observe(char, BackpressureStrategy.Unbounded).test {
                peripheral.emitObservationValue(testServiceUuid, testCharUuid, byteArrayOf(0x01))

                val v1 = awaitItem()
                assertIs<Observation.Value>(v1)

                peripheral.simulatePermanentDisconnect()
                assertIs<Observation.Disconnected>(awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun observeValuesCompletesOnPermanentDisconnect() =
        runTest {
            val peripheral = createPeripheral()
            peripheral.connect()

            val char = peripheral.findCharacteristic(testServiceUuid, testCharUuid)!!

            peripheral.observeValues(char, BackpressureStrategy.Unbounded).test {
                peripheral.emitObservationValue(testServiceUuid, testCharUuid, byteArrayOf(0x01))
                assertContentEquals(byteArrayOf(0x01), awaitItem())

                peripheral.simulatePermanentDisconnect()
                awaitComplete()
            }
        }

    @Test
    fun cccdRewrittenOnReconnect() =
        runTest {
            val peripheral = createPeripheral()
            peripheral.connect()

            val char = peripheral.findCharacteristic(testServiceUuid, testCharUuid)!!

            val job =
                launch {
                    peripheral.observe(char, BackpressureStrategy.Unbounded).collect { }
                }

            delay(50)

            var cccdWrites = peripheral.getCccdWrites()
            assertEquals(1, cccdWrites.size)
            assertTrue(cccdWrites[0].enabled)
            assertEquals(testServiceUuid, cccdWrites[0].serviceUuid)
            assertEquals(testCharUuid, cccdWrites[0].charUuid)

            peripheral.clearCccdWrites()

            peripheral.simulateDisconnect()
            delay(50)
            peripheral.simulateReconnect()
            delay(50)

            cccdWrites = peripheral.getCccdWrites()
            assertEquals(1, cccdWrites.size)
            assertTrue(cccdWrites[0].enabled)

            job.cancelAndJoin()
        }

    @Test
    fun characteristicRemovedOnReconnectCompletesObservation() =
        runTest {
            val peripheral = createPeripheral()
            peripheral.connect()

            val char = peripheral.findCharacteristic(testServiceUuid, testCharUuid)!!

            peripheral.observe(char, BackpressureStrategy.Unbounded).test {
                peripheral.emitObservationValue(testServiceUuid, testCharUuid, byteArrayOf(0x01))

                val v1 = awaitItem()
                assertIs<Observation.Value>(v1)

                peripheral.simulateDisconnect()
                assertIs<Observation.Disconnected>(awaitItem())

                val newServices =
                    listOf(
                        DiscoveredService(
                            uuid = testServiceUuid,
                            characteristics =
                                listOf(
                                    Characteristic(
                                        testServiceUuid,
                                        uuidFrom("2a38"),
                                        Characteristic.Properties(read = true),
                                    ),
                                ),
                        ),
                    )
                peripheral.simulateReconnect(newServices)

                // Characteristic removed — emits final Disconnected then completes
                assertIs<Observation.Disconnected>(awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun multipleObserversSameCharacteristic() =
        runTest {
            val peripheral = createPeripheral()
            peripheral.connect()

            val char = peripheral.findCharacteristic(testServiceUuid, testCharUuid)!!
            val observations1 = mutableListOf<Observation>()
            val observations2 = mutableListOf<Observation>()

            val job1 =
                launch {
                    peripheral.observe(char, BackpressureStrategy.Unbounded).collect { obs ->
                        observations1.add(obs)
                    }
                }

            val job2 =
                launch {
                    peripheral.observe(char, BackpressureStrategy.Unbounded).collect { obs ->
                        observations2.add(obs)
                    }
                }

            delay(50)

            peripheral.emitObservationValue(testServiceUuid, testCharUuid, byteArrayOf(0x01))
            delay(50)

            assertEquals(1, observations1.size)
            assertEquals(1, observations2.size)

            job1.cancelAndJoin()
            delay(50)

            val cccdWrites = peripheral.getCccdWrites()
            val disableWrites = cccdWrites.filter { !it.enabled }
            assertTrue(disableWrites.isEmpty())

            peripheral.emitObservationValue(testServiceUuid, testCharUuid, byteArrayOf(0x02))
            delay(50)

            assertEquals(1, observations1.size)
            assertEquals(2, observations2.size)

            job2.cancelAndJoin()
        }

    @Test
    fun collectorCancellationDisablesCccdWhenNoCollectorsRemain() =
        runTest {
            val peripheral = createPeripheral()
            peripheral.connect()

            val char = peripheral.findCharacteristic(testServiceUuid, testCharUuid)!!

            val job =
                launch {
                    peripheral.observe(char, BackpressureStrategy.Unbounded).collect { }
                }

            delay(50)

            var cccdWrites = peripheral.getCccdWrites()
            assertEquals(1, cccdWrites.size)
            assertTrue(cccdWrites[0].enabled)

            job.cancelAndJoin()
            delay(100)

            cccdWrites = peripheral.getCccdWrites()
            val disableWrites = cccdWrites.filter { !it.enabled }
            assertEquals(1, disableWrites.size)
        }

    @Test
    fun observeEmitsDisconnectedThenResumesOnReconnect() =
        runTest {
            val peripheral = createPeripheral()
            peripheral.connect()

            val char = peripheral.findCharacteristic(testServiceUuid, testCharUuid)!!

            peripheral.observe(char, BackpressureStrategy.Unbounded).test {
                peripheral.emitObservationValue(testServiceUuid, testCharUuid, byteArrayOf(0x01))

                val v1 = awaitItem()
                assertIs<Observation.Value>(v1)
                assertContentEquals(byteArrayOf(0x01), v1.data)

                peripheral.simulateDisconnect()
                assertIs<Observation.Disconnected>(awaitItem())

                peripheral.simulateReconnect()
                peripheral.emitObservationValue(testServiceUuid, testCharUuid, byteArrayOf(0x02))

                val v2 = awaitItem()
                assertIs<Observation.Value>(v2)
                assertContentEquals(byteArrayOf(0x02), v2.data)

                cancelAndIgnoreRemainingEvents()
            }
        }
}
