package com.atruedev.kmpble.lincheck

import com.atruedev.kmpble.gatt.internal.GattOperationQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.lincheck.datastructures.Operation
import org.jetbrains.lincheck.datastructures.StressOptions
import org.jetbrains.lincheck.datastructures.Validate
import org.junit.Test

/**
 * Lincheck stress for concurrent [GattOperationQueue.close] vs lifecycle ops (#663, #664).
 *
 * [close] may run from any teardown thread while [start] and [drain] run on the
 * queue's serialized dispatcher. All three touch channel lifecycle and
 * [GattOperationQueue]'s thread-safe [inFlightJobs] via [cancelInFlight].
 *
 * Suspend [GattOperationQueue.enqueue] cleanup (the other half of #663) is not
 * modeled here; see [com.atruedev.kmpble.gatt.internal.GattOperationQueueConcurrencyTest].
 * Concurrency regressions for this queue are gated by `./gradlew jvmTest` in CI.
 *
 * Uses [StressOptions] only - [ModelCheckingOptions] conflicts with the
 * coroutine launched inside [start].
 */
class GattOperationQueueCloseLincheckTest {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Unconfined)
    private val queue = GattOperationQueue(scope)

    @Operation
    fun close() = queue.close()

    @Operation
    fun start() = queue.start()

    @Operation
    fun drain() = queue.drain()

    @Validate
    fun cleanup() {
        queue.close()
        job.cancel()
    }

    @Test
    fun stressTest() =
        StressOptions()
            .iterations(50)
            .threads(3)
            .actorsPerThread(3)
            .check(this::class)
}
