package com.atruedev.kmpble.observation

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
import kotlin.test.assertFalse
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
            val observations = mutableListOf<Observation>()
            var flowCompleted = false

            val job =
                launch {
                    peripheral.observe(char, BackpressureStrategy.Unbounded).collect { obs ->
                        observations.add(obs)
                    }
                    flowCompleted = true
                }

            // Give the collector time to start
            delay(50)

            // Emit some values
            peripheral.emitObservationValue(testServiceUuid, testCharUuid, byteArrayOf(0x01))
            peripheral.emitObservationValue(testServiceUuid, testCharUuid, byteArrayOf(0x02))
            delay(50)

            assertEquals(2, observations.size)
            assertIs<Observation.Value>(observations[0])
            assertContentEquals(byteArrayOf(0x01), (observations[0] as Observation.Value).data)

            // Simulate disconnect
            peripheral.simulateDisconnect()
            delay(50)

            // Should have received Observation.Disconnected
            assertEquals(3, observations.size)
            assertIs<Observation.Disconnected>(observations[2])

            // Flow should NOT be completed
            assertFalse(flowCompleted)

            // Simulate reconnect
            peripheral.simulateReconnect()
            delay(50)

            // Emit more values after reconnect
            peripheral.emitObservationValue(testServiceUuid, testCharUuid, byteArrayOf(0x03))
            delay(50)

            // New value should be received
            assertEquals(4, observations.size)
            assertIs<Observation.Value>(observations[3])
            assertContentEquals(byteArrayOf(0x03), (observations[3] as Observation.Value).data)

            // Flow should still NOT be completed
            assertFalse(flowCompleted)

            job.cancelAndJoin()
        }

    @Test
    fun observeValuesTransparentlyReconnects() =
        runTest {
            val peripheral = createPeripheral()
            peripheral.connect()

            val char = peripheral.findCharacteristic(testServiceUuid, testCharUuid)!!
            val values = mutableListOf<ByteArray>()
            var flowCompleted = false

            val job =
                launch {
                    peripheral.observeValues(char, BackpressureStrategy.Unbounded).collect { value ->
                        values.add(value)
                    }
                    flowCompleted = true
                }

            delay(50)

            // Emit value before disconnect
            peripheral.emitObservationValue(testServiceUuid, testCharUuid, byteArrayOf(0x01))
            delay(50)

            assertEquals(1, values.size)

            // Simulate disconnect
            peripheral.simulateDisconnect()
            delay(50)

            // observeValues should NOT emit anything during disconnect
            assertEquals(1, values.size)

            // Flow should NOT be completed
            assertFalse(flowCompleted)

            // Simulate reconnect
            peripheral.simulateReconnect()
            delay(50)

            // Emit value after reconnect
            peripheral.emitObservationValue(testServiceUuid, testCharUuid, byteArrayOf(0x02))
            delay(50)

            // New value should be received
            assertEquals(2, values.size)
            assertContentEquals(byteArrayOf(0x02), values[1])

            // Flow should still NOT be completed
            assertFalse(flowCompleted)

            job.cancelAndJoin()
        }

    @Test
    fun observeCompletesOnPermanentDisconnect() =
        runTest {
            val peripheral = createPeripheral()
            peripheral.connect()

            val char = peripheral.findCharacteristic(testServiceUuid, testCharUuid)!!
            val observations = mutableListOf<Observation>()
            var flowCompleted = false
            var completionCause: Throwable? = null

            val job =
                launch {
                    try {
                        peripheral.observe(char, BackpressureStrategy.Unbounded).collect { obs ->
                            observations.add(obs)
                        }
                    } finally {
                        flowCompleted = true
                    }
                }

            delay(50)

            // Emit a value
            peripheral.emitObservationValue(testServiceUuid, testCharUuid, byteArrayOf(0x01))
            delay(50)

            // Simulate permanent disconnect
            peripheral.simulatePermanentDisconnect()
            delay(100)

            // Should have received value and final Disconnected
            assertEquals(2, observations.size)
            assertIs<Observation.Value>(observations[0])
            assertIs<Observation.Disconnected>(observations[1])

            // Flow should be completed normally
            assertTrue(flowCompleted)
        }

    @Test
    fun observeValuesCompletesOnPermanentDisconnect() =
        runTest {
            val peripheral = createPeripheral()
            peripheral.connect()

            val char = peripheral.findCharacteristic(testServiceUuid, testCharUuid)!!
            val values = mutableListOf<ByteArray>()
            var flowCompleted = false

            val job =
                launch {
                    peripheral.observeValues(char, BackpressureStrategy.Unbounded).collect { value ->
                        values.add(value)
                    }
                    flowCompleted = true
                }

            delay(50)

            // Emit a value
            peripheral.emitObservationValue(testServiceUuid, testCharUuid, byteArrayOf(0x01))
            delay(50)

            // Simulate permanent disconnect
            peripheral.simulatePermanentDisconnect()
            delay(100)

            // Should have only the value (no Disconnected for observeValues)
            assertEquals(1, values.size)

            // Flow should be completed normally
            assertTrue(flowCompleted)
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

            // CCCD should be enabled on initial subscription
            var cccdWrites = peripheral.getCccdWrites()
            assertEquals(1, cccdWrites.size)
            assertTrue(cccdWrites[0].enabled)
            assertEquals(testServiceUuid, cccdWrites[0].serviceUuid)
            assertEquals(testCharUuid, cccdWrites[0].charUuid)

            // Clear CCCD writes
            peripheral.clearCccdWrites()

            // Simulate disconnect and reconnect
            peripheral.simulateDisconnect()
            delay(50)
            peripheral.simulateReconnect()
            delay(50)

            // CCCD should be re-enabled on reconnect
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
            val observations = mutableListOf<Observation>()
            var flowCompleted = false

            val job =
                launch {
                    peripheral.observe(char, BackpressureStrategy.Unbounded).collect { obs ->
                        observations.add(obs)
                    }
                    flowCompleted = true
                }

            delay(50)

            // Emit a value
            peripheral.emitObservationValue(testServiceUuid, testCharUuid, byteArrayOf(0x01))
            delay(50)

            // Simulate disconnect
            peripheral.simulateDisconnect()
            delay(50)

            // Simulate reconnect with different services (characteristic removed)
            val newServices =
                listOf(
                    DiscoveredService(
                        uuid = testServiceUuid,
                        characteristics =
                            listOf(
                                // Only 2a38, not 2a37
                                Characteristic(
                                    testServiceUuid,
                                    uuidFrom("2a38"),
                                    Characteristic.Properties(read = true),
                                ),
                            ),
                    ),
                )
            peripheral.simulateReconnect(newServices)
            delay(100)

            // Should have received: Value, Disconnected, and flow should complete
            assertTrue(observations.size >= 2)
            assertIs<Observation.Value>(observations[0])
            assertIs<Observation.Disconnected>(observations[1])

            // Flow should be completed
            assertTrue(flowCompleted)
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

            // Emit a value — both should receive it
            peripheral.emitObservationValue(testServiceUuid, testCharUuid, byteArrayOf(0x01))
            delay(50)

            assertEquals(1, observations1.size)
            assertEquals(1, observations2.size)

            // Cancel first collector
            job1.cancelAndJoin()
            delay(50)

            // CCCD should NOT be disabled (still has collector)
            val cccdWrites = peripheral.getCccdWrites()
            val disableWrites = cccdWrites.filter { !it.enabled }
            assertTrue(disableWrites.isEmpty())

            // Second collector should still receive values
            peripheral.emitObservationValue(testServiceUuid, testCharUuid, byteArrayOf(0x02))
            delay(50)

            assertEquals(1, observations1.size) // First didn't get it (cancelled)
            assertEquals(2, observations2.size) // Second got it

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

            // CCCD should be enabled
            var cccdWrites = peripheral.getCccdWrites()
            assertEquals(1, cccdWrites.size)
            assertTrue(cccdWrites[0].enabled)

            // Cancel the collector
            job.cancelAndJoin()
            delay(100)

            // CCCD should be disabled
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
            val observations = mutableListOf<Observation>()

            val job =
                launch {
                    peripheral.observe(char, BackpressureStrategy.Unbounded).collect { obs ->
                        observations.add(obs)
                    }
                }

            delay(50)

            // Emit initial value
            peripheral.emitObservationValue(testServiceUuid, testCharUuid, byteArrayOf(0x01))
            delay(50)

            // Disconnect
            peripheral.simulateDisconnect()
            delay(50)

            // Reconnect
            peripheral.simulateReconnect()
            delay(50)

            // Emit after reconnect
            peripheral.emitObservationValue(testServiceUuid, testCharUuid, byteArrayOf(0x02))
            delay(50)

            // Verify sequence: Value(1), Disconnected, Value(2)
            assertEquals(3, observations.size)
            assertIs<Observation.Value>(observations[0])
            assertContentEquals(byteArrayOf(0x01), (observations[0] as Observation.Value).data)
            assertIs<Observation.Disconnected>(observations[1])
            assertIs<Observation.Value>(observations[2])
            assertContentEquals(byteArrayOf(0x02), (observations[2] as Observation.Value).data)

            job.cancelAndJoin()
        }
}
