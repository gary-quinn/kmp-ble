package com.atruedev.kmpble.gatt.internal

import com.atruedev.kmpble.connection.withGattOperationTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
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
    fun callerCancellationWaitsForInFlightCleanupBeforeReturning() =
        runTest {
            val queue = GattOperationQueue(backgroundScope)
            queue.start()

            var cleanupCompleted = false
            val job =
                launch {
                    runCatching {
                        queue.enqueue(timeout = 5.seconds) {
                            try {
                                delay(10_000)
                            } catch (e: CancellationException) {
                                cleanupCompleted = true
                                throw e
                            }
                        }
                    }
                }
            repeat(10) { yield() }
            job.cancel()
            job.join()

            assertTrue(
                cleanupCompleted,
                "cleanup must finish before the cancelled caller returns",
            )
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

    @Test
    fun callerCancelBeforeActionStartsSkipsQueuedAction() =
        runTest {
            val queue = GattOperationQueue(backgroundScope)
            queue.start()

            var slowRan = false
            var skippedRan = false
            val slow =
                launch {
                    queue.enqueue(timeout = 5.seconds) {
                        slowRan = true
                        delay(100)
                    }
                }
            val skipped =
                launch {
                    runCatching {
                        queue.enqueue(timeout = 5.seconds) {
                            skippedRan = true
                        }
                    }
                }
            // Let the slow action start and the second action queue behind it.
            repeat(10) { yield() }
            // Cancel the second caller before its action starts.
            skipped.cancel()
            skipped.join()
            slow.join()

            assertTrue(slowRan)
            assertFalse(skippedRan, "queued action cancelled before start must be skipped")
        }

    @Test
    fun closeCancelsInFlightAction() =
        runTest {
            val queue = GattOperationQueue(backgroundScope)
            queue.start()

            var actionCancelled = false
            val job: Job =
                launch {
                    runCatching {
                        queue.enqueue(timeout = 5.seconds) {
                            try {
                                delay(10_000)
                            } catch (e: CancellationException) {
                                actionCancelled = true
                                throw e
                            }
                        }
                    }
                }
            repeat(10) { yield() }
            queue.close()
            job.join()

            assertTrue(actionCancelled, "close() must cancel in-flight actions")
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun withoutScopeTheOperationDefaultApplies() =
        runTest {
            val queue = GattOperationQueue(backgroundScope)
            queue.start()

            val start = currentTime
            assertFailsWith<TimeoutCancellationException> {
                queue.enqueue(timeout = 5.seconds) { delay(20.seconds) }
            }
            assertEquals(5_000, currentTime - start)
        }

    @Test
    fun scopeLengthensOperationPastDefault() =
        runTest {
            val queue = GattOperationQueue(backgroundScope)
            queue.start()

            val result =
                withGattOperationTimeout(30.seconds) {
                    queue.enqueue(timeout = 5.seconds) {
                        delay(20.seconds)
                        "late"
                    }
                }
            assertEquals("late", result)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun scopeShortensOperationBelowDefault() =
        runTest {
            val queue = GattOperationQueue(backgroundScope)
            queue.start()

            val start = currentTime
            assertFailsWith<TimeoutCancellationException> {
                withGattOperationTimeout(1.seconds) {
                    queue.enqueue(timeout = 5.seconds) { delay(3.seconds) }
                }
            }
            assertEquals(1_000, currentTime - start)
        }

    @Test
    fun scopeAlsoOverridesQueueWideDefault() =
        runTest {
            val queue = GattOperationQueue(backgroundScope)
            queue.start(timeout = 1.seconds)

            val result =
                withGattOperationTimeout(30.seconds) {
                    queue.enqueueBle {
                        delay(20.seconds)
                        "late"
                    }
                }
            assertEquals("late", result)
        }

    @Test
    fun innermostScopeWins() =
        runTest {
            val queue = GattOperationQueue(backgroundScope)
            queue.start()

            val lengthened =
                withGattOperationTimeout(1.seconds) {
                    withGattOperationTimeout(30.seconds) {
                        queue.enqueue(timeout = 5.seconds) {
                            delay(20.seconds)
                            "inner"
                        }
                    }
                }
            assertEquals("inner", lengthened)

            withGattOperationTimeout(30.seconds) {
                assertFailsWith<TimeoutCancellationException> {
                    withGattOperationTimeout(1.seconds) {
                        queue.enqueue(timeout = 5.seconds) { delay(3.seconds) }
                    }
                }
                // The inner scope must not leak into the rest of the outer block.
                val outer =
                    queue.enqueue(timeout = 5.seconds) {
                        delay(20.seconds)
                        "outer"
                    }
                assertEquals("outer", outer)
            }
        }

    @Test
    fun scopeDoesNotOutliveItsBlock() =
        runTest {
            val queue = GattOperationQueue(backgroundScope)
            queue.start()

            withGattOperationTimeout(30.seconds) {
                queue.enqueue(timeout = 5.seconds) { delay(20.seconds) }
            }
            assertFailsWith<TimeoutCancellationException> {
                queue.enqueue(timeout = 5.seconds) { delay(20.seconds) }
            }
        }

    @Test
    fun scopeIsInheritedByChildCoroutines() =
        runTest {
            val queue = GattOperationQueue(backgroundScope)
            queue.start()

            val result =
                withGattOperationTimeout(30.seconds) {
                    coroutineScope {
                        async {
                            queue.enqueue(timeout = 5.seconds) {
                                delay(20.seconds)
                                "child"
                            }
                        }.await()
                    }
                }
            assertEquals("child", result)
        }

    @Test
    fun infiniteScopeRemovesTheLimit() =
        runTest {
            val queue = GattOperationQueue(backgroundScope)
            queue.start()

            val result =
                withGattOperationTimeout(Duration.INFINITE) {
                    queue.enqueue(timeout = 1.milliseconds) {
                        delay(1.hours)
                        "done"
                    }
                }
            assertEquals("done", result)
        }

    @Test
    fun scopeRejectsNonPositiveTimeout() =
        runTest {
            var ran = false
            assertFailsWith<IllegalArgumentException> {
                withGattOperationTimeout(Duration.ZERO) { ran = true }
            }
            assertFailsWith<IllegalArgumentException> {
                withGattOperationTimeout((-1).seconds) { ran = true }
            }
            assertFalse(ran, "block must not run when the timeout is rejected")
        }
}
