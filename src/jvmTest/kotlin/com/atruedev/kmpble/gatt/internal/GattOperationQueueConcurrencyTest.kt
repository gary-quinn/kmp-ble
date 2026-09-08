package com.atruedev.kmpble.gatt.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

/**
 * JVM stress for #663 / #664: [close] from an unconfined thread while [enqueue]
 * cleanup mutates [inFlightJobs] on the queue's serialized dispatcher.
 *
 * Paired with [com.atruedev.kmpble.lincheck.GattOperationQueueCloseLincheckTest]
 * (concurrent [close] vs lifecycle) and gated by `./gradlew jvmTest` in CI.
 */
class GattOperationQueueConcurrencyTest {
    @Test
    fun closeFromUnconfinedThreadRacesInFlightActionCleanup() {
        // Assertion: completes without ConcurrentModificationException / corruption.
        val iterations = 1_000
        var completed = 0
        repeat(iterations) {
            val job = SupervisorJob()
            val scope = CoroutineScope(job + Dispatchers.Default.limitedParallelism(1))
            val queue = GattOperationQueue(scope)
            queue.start(timeout = 100.milliseconds)

            val enqueueJob =
                scope.launch {
                    runCatching { queue.enqueue(timeout = 100.milliseconds) { /* fast op */ } }
                }

            val closeThread = Thread { queue.close() }
            closeThread.start()

            runBlocking { enqueueJob.join() }
            closeThread.join()
            job.cancel()
            completed++
        }
        assertEquals(iterations, completed)
    }
}
