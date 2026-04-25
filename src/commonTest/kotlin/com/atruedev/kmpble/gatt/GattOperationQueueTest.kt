package com.atruedev.kmpble.gatt

import com.atruedev.kmpble.gatt.internal.GattOperationQueue
import com.atruedev.kmpble.gatt.internal.NotConnectedException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class GattOperationQueueTest {
    @Test
    fun startSetsOperationTimeout() =
        runTest {
            val queue = GattOperationQueue(this)
            queue.start(timeout = 1.seconds)

            assertFailsWith<TimeoutCancellationException> {
                queue.enqueue { delay(5.seconds) }
            }

            queue.close()
        }

    @Test
    fun restartWithoutArgReusesTimeout() =
        runTest {
            val queue = GattOperationQueue(this)
            queue.start(timeout = 1.seconds)
            queue.drain()
            queue.start()

            assertFailsWith<TimeoutCancellationException> {
                queue.enqueue { delay(5.seconds) }
            }

            queue.close()
        }

    @Test
    fun fifoOrdering() =
        runTest {
            val queue = GattOperationQueue(this)
            queue.start()

            val order = mutableListOf<Int>()

            val gate1 = CompletableDeferred<Unit>()
            val job1 =
                async {
                    queue.enqueue {
                        gate1.await()
                        order.add(1)
                        "first"
                    }
                }

            val job2 =
                async {
                    queue.enqueue {
                        order.add(2)
                        "second"
                    }
                }

            gate1.complete(Unit)

            assertEquals("first", job1.await())
            assertEquals("second", job2.await())
            assertEquals(listOf(1, 2), order)

            queue.close()
        }

    @Test
    fun timeoutCancelsWait() =
        runTest {
            val queue = GattOperationQueue(this)
            queue.start()

            assertFailsWith<TimeoutCancellationException> {
                queue.enqueue(timeout = 1.seconds) { delay(10.seconds) }
            }

            queue.close()
        }

    @Test
    fun drainRejectsNewOperations() =
        runTest {
            val queue = GattOperationQueue(this)
            queue.start()
            queue.drain()

            assertFailsWith<NotConnectedException> {
                queue.enqueue { "should fail" }
            }

            queue.close()
        }

    @Test
    fun drainCancelsPendingEntries() =
        runTest {
            val queue = GattOperationQueue(this)
            queue.start()

            // Hold the drain loop on a gate so subsequent entries stay in the channel buffer.
            val gate = CompletableDeferred<Unit>()
            launch {
                runCatching {
                    queue.enqueue {
                        gate.await()
                        "blocker"
                    }
                }
            }
            // Let drainJob dequeue the blocker and suspend on gate.
            yield()

            // Victim entry sits in the channel buffer (drain loop is blocked).
            val victim = CompletableDeferred<Result<String>>()
            launch {
                victim.complete(runCatching { queue.enqueue { "victim" } })
            }
            yield()

            // drain() closes the channel - victim gets NotConnectedException.
            queue.drain()
            gate.complete(Unit)

            val failure = victim.await()
            assertTrue(failure.isFailure, "Queued entry should have been cancelled by drain")
            assertIs<NotConnectedException>(failure.exceptionOrNull())

            queue.close()
        }

    @Test
    fun restartAfterDrainAcceptsNewOperations() =
        runTest {
            val queue = GattOperationQueue(this)
            queue.start()
            queue.drain()

            assertFailsWith<NotConnectedException> {
                queue.enqueue { "rejected" }
            }

            queue.start()
            val result = queue.enqueue { "accepted" }
            assertEquals("accepted", result)

            queue.close()
        }

    @Test
    fun closeRejectsNewOperations() =
        runTest {
            val queue = GattOperationQueue(this)
            queue.start()
            queue.close()

            assertFailsWith<NotConnectedException> {
                queue.enqueue { "should fail" }
            }
        }
}
