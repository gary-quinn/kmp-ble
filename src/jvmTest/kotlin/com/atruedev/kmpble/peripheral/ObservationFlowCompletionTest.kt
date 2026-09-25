package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.internal.ObservationManager
import com.atruedev.kmpble.peripheral.internal.ObservationToBytes
import com.atruedev.kmpble.peripheral.internal.buildObservationFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ObservationFlowCompletionTest {
    @Test
    fun cancellingTheLastCollectorDisablesNotifications() =
        runBlocking<Unit> {
            val manager = ObservationManager(Dispatchers.Default.limitedParallelism(1))
            val characteristic = Characteristic(Uuid.random(), Uuid.random(), Characteristic.Properties(notify = true))
            val enabled = CompletableDeferred<Unit>()
            val disabled = CompletableDeferred<Characteristic>()
            val flow =
                buildObservationFlow(
                    characteristic = characteristic,
                    backpressure = BackpressureStrategy.Latest,
                    observationManager = manager,
                    isReady = { true },
                    enable = { enabled.complete(Unit) },
                    disable = { disabled.complete(it) },
                    mapper = ObservationToBytes,
                )

            val collector = launch { flow.collect { } }
            withTimeout(2.seconds) { enabled.await() }
            collector.cancel()

            assertTrue(withTimeout(2.seconds) { disabled.await() } === characteristic)
        }
}
