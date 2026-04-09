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
 * Lincheck stress test for [GattOperationQueue] lifecycle operations.
 *
 * Tests only the non-suspend lifecycle methods: [start], [drain].
 * The suspend [GattOperationQueue.enqueue] is excluded because Lincheck
 * controls threads, not coroutine dispatchers.
 *
 * Uses [StressOptions] only — [ModelCheckingOptions] conflicts with the
 * coroutine launched inside [start].
 *
 * [Dispatchers.Unconfined] avoids spawning worker threads per scenario;
 * Lincheck already controls thread scheduling.
 */
class GattOperationQueueLifecycleLincheckTest {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Unconfined)
    private val queue = GattOperationQueue(scope)

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
            .threads(2)
            .actorsPerThread(3)
            .check(this::class)
}
