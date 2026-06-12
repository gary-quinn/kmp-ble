package com.atruedev.kmpble.observation

import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.scanner.uuidFrom
import com.atruedev.kmpble.testing.FakePeripheral
import com.atruedev.kmpble.testing.FakePeripheralBuilder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class, ExperimentalCoroutinesApi::class)
class ObservationReconnectionJvmTest {
    private val testServiceUuid = uuidFrom("180d")
    private val testCharUuid = uuidFrom("2a37")

    private fun createPeripheral(
        dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
    ): FakePeripheral =
        FakePeripheralBuilder()
            .observationDispatcher(dispatcher)
            .apply {
                service("180d") {
                    characteristic("2a37") { properties(notify = true) }
                    characteristic("2a38") { properties(read = true) }
                }
            }.build()

    @Test
    fun multipleObserversSameCharacteristic() =
        runTest {
            val peripheral = createPeripheral(StandardTestDispatcher())
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
            advanceUntilIdle()

            val cccdWrites = peripheral.getCccdWrites()
            val disableWrites = cccdWrites.filter { !it.enabled }
            assertTrue(disableWrites.isEmpty())

            peripheral.emitObservationValue(testServiceUuid, testCharUuid, byteArrayOf(0x02))
            delay(50)

            assertEquals(1, observations1.size)
            assertEquals(2, observations2.size)

            job2.cancelAndJoin()
            advanceUntilIdle()
        }

    @Test
    fun collectorCancellationDisablesCccdWhenNoCollectorsRemain() =
        runTest {
            val peripheral = createPeripheral(this.testDispatcher)
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
            advanceUntilIdle() // Ensure all pending coroutines (including CCCD disable) complete

            cccdWrites = peripheral.getCccdWrites()
            val disableWrites = cccdWrites.filter { !it.enabled }
            assertEquals(1, disableWrites.size)
        }
}
