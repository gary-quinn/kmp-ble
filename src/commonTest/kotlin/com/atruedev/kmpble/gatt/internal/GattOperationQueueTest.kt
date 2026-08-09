package com.atruedev.kmpble.gatt.internal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class GattOperationQueueTest {
    @Test
    fun enqueueRunsActionAndReturnsResult() =
        runTest {
            val queue = GattOperationQueue(backgroundScope)
            queue.start()

            val result = queue.enqueue(timeout = 5.seconds) { 42 }
            assertEquals(42, result)
        }

    @Test
    fun enqueueSerializesActions() =
        runTest {
            val queue = GattOperationQueue(backgroundScope)
            queue.start()

            val order = mutableListOf<Int>()
            val j1 = launch { queue.enqueue(timeout = 5.seconds) { order += 1 } }
            val j2 = launch { queue.enqueue(timeout = 5.seconds) { order += 2 } }
            j1.join()
            j2.join()
            assertEquals(listOf(1, 2), order)
        }

    @Test
    fun enqueueRejectsAfterClose() =
        runTest {
            val queue = GattOperationQueue(backgroundScope)
            queue.start()
            queue.drain()

            assertFailsWith<NotConnectedException> {
                queue.enqueue(timeout = 5.seconds) { 1 }
            }
        }

    @Test
    fun callerCancellationCancelsInFlightAction() =
        runTest {
            val queue = GattOperationQueue(backgroundScope)
            queue.start()

            var actionCancelled = false
            val job: Job =
                launch {
                    queue.enqueue(timeout = 5.seconds) {
                        try {
                            delay(10_000)
                        } catch (e: CancellationException) {
                            actionCancelled = true
                            throw e
                        }
                    }
                }
            // Let the action start, then cancel the caller.
            repeat(10) { yield() }
            job.cancel()
            job.join()

            assertTrue(actionCancelled, "in-flight action must observe caller cancellation")
        }

    @Test
    fun timeoutCancelsInFlightAction() =
        runTest {
            val queue = GattOperationQueue(backgroundScope)
            queue.start()

            var actionCancelled = false
            assertFailsWith<CancellationException> {
                queue.enqueue(timeout = 1.milliseconds) {
                    try {
                        delay(10_000)
                    } catch (e: CancellationException) {
                        actionCancelled = true
                        throw e
                    }
                }
            }
            repeat(10) { yield() }
            assertTrue(actionCancelled, "timed-out action must observe cancellation")
        }

    @Test
    fun cancelledActionStillRunsIsolatedFromNext() =
        runTest {
            val queue = GattOperationQueue(backgroundScope)
            queue.start()

            var slowCancelled = false
            val slow =
                launch {
                    runCatching {
                        queue.enqueue(timeout = 1.milliseconds) {
                            try {
                                delay(10_000)
                            } catch (e: CancellationException) {
                                slowCancelled = true
                                throw e
                            }
                        }
                    }
                }
            slow.join()

            // The next enqueue must still be served even though the previous
            // action was cancelled mid-flight.
            val next = queue.enqueue(timeout = 5.seconds) { "served" }
            assertEquals("served", next)
            assertTrue(slowCancelled)
        }
}
