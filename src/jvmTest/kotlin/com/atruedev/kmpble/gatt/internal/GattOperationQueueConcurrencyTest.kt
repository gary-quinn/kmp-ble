package com.atruedev.kmpble.gatt.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * Stresses [GattOperationQueue.close] invoked from an unconfined thread while
 * [GattOperationQueue.enqueue] cleanup runs on the queue's serialized dispatcher.
 * A plain [MutableSet] backing [GattOperationQueue]'s in-flight tracking races here.
 */
class GattOperationQueueConcurrencyTest {
    @Test
    fun closeFromUnconfinedThreadRacesInFlightActionCleanup() {
        repeat(1_000) {
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
        }
    }
}
