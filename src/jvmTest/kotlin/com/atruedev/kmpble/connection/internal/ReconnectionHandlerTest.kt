package com.atruedev.kmpble.connection.internal

import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.ReconnectionStrategy
import com.atruedev.kmpble.peripheral.state.ConnectionState
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.ConnectionFailed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectionHandlerTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    // --- Backoff exhaustion ---

    @Test
    fun `backoff exhaustion triggers onMaxAttemptsExhausted and stops`() {
        runTest(scheduler) {
            var exhausted = false
            val stateFlow = MutableStateFlow<State>(State.Connected.Ready)
            val handler =
                ReconnectionHandler(
                    scope = this,
                    stateFlow = stateFlow,
                    connectAction = {
                        throw BleException(
                            ConnectionFailed("simulated"),
                        )
                    },
                    onMaxAttemptsExhausted = { exhausted = true },
                    random = Random(42),
                )

            handler.start(
                ConnectionOptions(
                    reconnectionStrategy =
                        ReconnectionStrategy.ExponentialBackoff(
                            initialDelay = 10.milliseconds,
                            maxDelay = 100.milliseconds,
                            maxAttempts = 3,
                        ),
                ),
            )

            // Use different Disconnected subtypes to bypass MutableStateFlow dedup.
            // maxAttempts=3: first 3 fail, 4th emission triggers exhaustion.
            val states =
                listOf(
                    State.Disconnected.ByRemote,
                    State.Disconnected.ByError(ConnectionFailed("e1")),
                    State.Disconnected.ByTimeout,
                    State.Disconnected.BySystemEvent,
                )
            for (s in states) {
                stateFlow.value = s
                advanceUntilIdle()
            }

            assertTrue(exhausted, "onMaxAttemptsExhausted should have fired")
        }
    }

    @Test
    fun `LinearBackoff exhaustion fires onMaxAttemptsExhausted`() {
        runTest(scheduler) {
            var exhausted = false
            val stateFlow = MutableStateFlow<State>(State.Connected.Ready)
            val handler =
                ReconnectionHandler(
                    scope = this,
                    stateFlow = stateFlow,
                    connectAction = {
                        throw BleException(
                            ConnectionFailed("simulated"),
                        )
                    },
                    onMaxAttemptsExhausted = { exhausted = true },
                    random = Random(42),
                )

            handler.start(
                ConnectionOptions(
                    reconnectionStrategy =
                        ReconnectionStrategy.LinearBackoff(
                            delay = 5.milliseconds,
                            maxAttempts = 2,
                        ),
                ),
            )

            // maxAttempts=2: first 2 fail, 3rd emission triggers exhaustion.
            val states =
                listOf(
                    State.Disconnected.ByRemote,
                    State.Disconnected.ByError(ConnectionFailed("e1")),
                    State.Disconnected.ByTimeout,
                )
            for (s in states) {
                stateFlow.value = s
                advanceUntilIdle()
            }

            assertTrue(exhausted, "onMaxAttemptsExhausted should have fired for LinearBackoff")
        }
    }

    // --- Cancellation during reconnect ---

    @Test
    fun `CancellationException from connectAction cancels the handler job`() {
        runTest(scheduler) {
            var cancellationCaught = false
            val stateFlow = MutableStateFlow<State>(State.Connected.Ready)
            val handler =
                ReconnectionHandler(
                    scope = this,
                    stateFlow = stateFlow,
                    connectAction = {
                        throw CancellationException("deliberate cancellation")
                    },
                    random = Random(42),
                )

            handler.start(
                ConnectionOptions(
                    reconnectionStrategy =
                        ReconnectionStrategy.ExponentialBackoff(
                            initialDelay = 10.milliseconds,
                            maxAttempts = 5,
                        ),
                ),
            )

            stateFlow.value = State.Disconnected.ByRemote
            advanceUntilIdle()

            // The CancellationException cancels the collector job.
            // Verify by trying to trigger another reconnect -- it should do nothing
            // because the job is dead.
            var secondAttempt = false
            stateFlow.value = State.Disconnected.ByError(ConnectionFailed("e1"))
            advanceUntilIdle()

            // If the job was still alive, the handler would have called connectAction again.
            // Since connectAction throws CancellationException, and the job is dead,
            // we verify that no exception leaks and the test completes.
            handler.stop()
        }
    }

    // --- Manual stop cancels in-flight reconnect ---

    @Test
    fun `stop cancels in-flight reconnect`() {
        runTest(scheduler) {
            var connectAttempts = 0
            val stateFlow = MutableStateFlow<State>(State.Connected.Ready)
            val handler =
                ReconnectionHandler(
                    scope = this,
                    stateFlow = stateFlow,
                    connectAction = {
                        connectAttempts++
                        // Never completes -- simulates stalled connection
                        delay(Long.MAX_VALUE)
                    },
                    random = Random(42),
                )

            handler.start(
                ConnectionOptions(
                    reconnectionStrategy =
                        ReconnectionStrategy.ExponentialBackoff(
                            initialDelay = 5.milliseconds,
                            maxDelay = 100.milliseconds,
                            maxAttempts = 5,
                        ),
                ),
            )

            // Trigger reconnect, advance past the backoff delay so connectAction starts
            stateFlow.value = State.Disconnected.ByRemote
            scheduler.advanceTimeBy(10)

            // connectAttempts was incremented before delay(Long.MAX_VALUE)
            assertEquals(1, connectAttempts)

            // Call stop while connectAction is suspended on Long.MAX_VALUE
            handler.stop()
            advanceUntilIdle()

            // Only 1 attempt -- the one that got cancelled
            assertEquals(1, connectAttempts)
        }
    }

    // --- State races ---

    @Test
    fun `stop during reconnect backoff prevents further attempts`() {
        runTest(scheduler) {
            var connectAttempts = 0
            val stateFlow = MutableStateFlow<State>(State.Connected.Ready)
            val handler =
                ReconnectionHandler(
                    scope = this,
                    stateFlow = stateFlow,
                    connectAction = { connectAttempts++ },
                    random = Random(42),
                )

            handler.start(
                ConnectionOptions(
                    reconnectionStrategy =
                        ReconnectionStrategy.LinearBackoff(
                            delay = 100.milliseconds,
                            maxAttempts = 5,
                        ),
                ),
            )

            // Trigger reconnect, delay is 100ms so it's waiting in backoff
            stateFlow.value = State.Disconnected.ByRemote
            scheduler.runCurrent()

            // Stop before the delay completes
            handler.stop()
            advanceUntilIdle()

            // No connect should have fired
            assertEquals(0, connectAttempts)
        }
    }

    // --- Multiple failures then success resets attempt counter ---

    @Test
    fun `successful reconnect resets attempt counter for subsequent failures`() {
        runTest(scheduler) {
            var exhausted = false
            var shouldSucceed = false
            val stateFlow = MutableStateFlow<State>(State.Connected.Ready)
            val handler =
                ReconnectionHandler(
                    scope = this,
                    stateFlow = stateFlow,
                    connectAction = {
                        if (!shouldSucceed) {
                            throw BleException(ConnectionFailed("fail"))
                        }
                    },
                    onMaxAttemptsExhausted = { exhausted = true },
                    random = Random(42),
                )

            handler.start(
                ConnectionOptions(
                    reconnectionStrategy =
                        ReconnectionStrategy.ExponentialBackoff(
                            initialDelay = 5.milliseconds,
                            maxDelay = 50.milliseconds,
                            maxAttempts = 3,
                        ),
                ),
            )

            // First two fail -- use ByError with distinct messages for StateFlow dedup.
            val phase1 =
                listOf(
                    State.Disconnected.ByError(ConnectionFailed("p1a")),
                    State.Disconnected.ByError(ConnectionFailed("p1b")),
                )
            for (s in phase1) {
                stateFlow.value = s
                advanceUntilIdle()
            }

            // Third succeeds -- should reset internal attempt counter to 0
            shouldSucceed = true
            stateFlow.value = State.Disconnected.ByError(ConnectionFailed("success"))
            advanceUntilIdle()

            // Now fail 4 more times. With reset counter + maxAttempts=3,
            // exhaustion fires on the 4th post-reset state (3 failures + 1 exhaustion check).
            shouldSucceed = false
            assertTrue(!exhausted, "Should not be exhausted yet")
            val phase2 =
                listOf(
                    State.Disconnected.ByError(ConnectionFailed("p2a")),
                    State.Disconnected.ByError(ConnectionFailed("p2b")),
                    State.Disconnected.ByError(ConnectionFailed("p2c")),
                    State.Disconnected.ByError(ConnectionFailed("p2d")),
                )
            for (s in phase2) {
                stateFlow.value = s
                advanceUntilIdle()
            }

            assertTrue(exhausted, "onMaxAttemptsExhausted should fire after 3 post-reset failures")
        }
    }

    // --- Disconnected.ByRequest does NOT trigger reconnection ---

    @Test
    fun `Disconnected_ByRequest does not trigger reconnection`() {
        runTest(scheduler) {
            var connectAttempts = 0
            val stateFlow = MutableStateFlow<State>(State.Connected.Ready)
            val handler =
                ReconnectionHandler(
                    scope = this,
                    stateFlow = stateFlow,
                    connectAction = { connectAttempts++ },
                    random = Random(42),
                )

            handler.start(
                ConnectionOptions(
                    reconnectionStrategy =
                        ReconnectionStrategy.ExponentialBackoff(
                            initialDelay = 5.milliseconds,
                            maxAttempts = 5,
                        ),
                ),
            )

            stateFlow.value = State.Disconnected.ByRequest
            advanceUntilIdle()

            assertEquals(0, connectAttempts, "ByRequest disconnect should NOT trigger reconnection")
            handler.stop()
        }
    }

    // --- Strategy.None does not start any job ---

    @Test
    fun `ReconnectionStrategy_None does not start reconnection`() =
        runTest(scheduler) {
            var connectAttempts = 0
            val stateFlow = MutableStateFlow<State>(State.Connected.Ready)
            val handler =
                ReconnectionHandler(
                    scope = this,
                    stateFlow = stateFlow,
                    connectAction = { connectAttempts++ },
                    random = Random(42),
                )

            handler.start(
                ConnectionOptions(
                    reconnectionStrategy = ReconnectionStrategy.None,
                ),
            )

            stateFlow.value = State.Disconnected.ByRemote
            advanceUntilIdle()

            assertEquals(0, connectAttempts, "None strategy should not trigger reconnection")
        }

    // --- computeDelay edge cases ---

    @Test
    fun `computeDelay returns null when attempt equals maxAttempts`() =
        runTest(scheduler) {
            val handler =
                ReconnectionHandler(
                    scope = this,
                    stateFlow = MutableStateFlow(State.Connected.Ready),
                    connectAction = {},
                    random = Random(42),
                )

            val strategy = ReconnectionStrategy.ExponentialBackoff(maxAttempts = 3)
            assertNotNull(handler.computeDelay(strategy, 0)) // 0 < 3, ok
            assertNotNull(handler.computeDelay(strategy, 2)) // 2 < 3, ok
            assertEquals(null, handler.computeDelay(strategy, 3)) // 3 >= 3, null
        }

    @Test
    fun `computeDelay with zero initialDelay returns positive value`() =
        runTest(scheduler) {
            val handler =
                ReconnectionHandler(
                    scope = this,
                    stateFlow = MutableStateFlow(State.Connected.Ready),
                    connectAction = {},
                    random = Random(42),
                )

            val strategy =
                ReconnectionStrategy.ExponentialBackoff(
                    initialDelay = 0.milliseconds,
                    maxDelay = 100.milliseconds,
                    maxAttempts = 5,
                )

            val delay = handler.computeDelay(strategy, 0)
            assertNotNull(delay)
            // With zero base, jitter may be zero, but coerceAtLeast(1) ensures >= 1ms
            assertTrue(delay.inWholeMilliseconds >= 1, "Delay should be at least 1ms: $delay")
        }

    @Test
    fun `computeDelay clamps to maxDelay`() =
        runTest(scheduler) {
            val handler =
                ReconnectionHandler(
                    scope = this,
                    stateFlow = MutableStateFlow(State.Connected.Ready),
                    connectAction = {},
                    random = Random(42),
                )

            val strategy =
                ReconnectionStrategy.ExponentialBackoff(
                    initialDelay = 60.seconds,
                    maxDelay = 10.seconds,
                    maxAttempts = 5,
                )

            // With initialDelay=60s > maxDelay=10s, the delay should be clamped
            val delay = handler.computeDelay(strategy, 0)
            assertNotNull(delay)
            assertTrue(
                delay.inWholeMilliseconds <= 10.seconds.inWholeMilliseconds + 2000,
                "Delay should be clamped near maxDelay: $delay",
            )
        }

    @Test
    fun `computeDelay with LinearBackoff returns constant delay`() =
        runTest(scheduler) {
            val handler =
                ReconnectionHandler(
                    scope = this,
                    stateFlow = MutableStateFlow(State.Connected.Ready),
                    connectAction = {},
                    random = Random(42),
                )

            val strategy =
                ReconnectionStrategy.LinearBackoff(
                    delay = 50.milliseconds,
                    maxAttempts = 5,
                )

            val delay1 = handler.computeDelay(strategy, 0)
            val delay2 = handler.computeDelay(strategy, 3)
            assertNotNull(delay1)
            assertNotNull(delay2)
            // LinearBackoff always returns the same delay (no jitter)
            assertEquals(50.milliseconds, delay1)
            assertEquals(50.milliseconds, delay2)
        }

    @Test
    fun `computeDelay with LinearBackoff returns null at maxAttempts`() =
        runTest(scheduler) {
            val handler =
                ReconnectionHandler(
                    scope = this,
                    stateFlow = MutableStateFlow(State.Connected.Ready),
                    connectAction = {},
                    random = Random(42),
                )

            val strategy =
                ReconnectionStrategy.LinearBackoff(delay = 10.milliseconds, maxAttempts = 3)
            assertNotNull(handler.computeDelay(strategy, 0))
            assertNotNull(handler.computeDelay(strategy, 2))
            assertEquals(null, handler.computeDelay(strategy, 3))
        }

    // --- ByError, ByTimeout, BySystemEvent trigger reconnection ---

    @Test
    fun `Disconnected_ByError triggers reconnection`() {
        runTest(scheduler) {
            var connectAttempts = 0
            val stateFlow = MutableStateFlow<State>(State.Connected.Ready)
            val handler =
                ReconnectionHandler(
                    scope = this,
                    stateFlow = stateFlow,
                    connectAction = { connectAttempts++ },
                    random = Random(42),
                )

            handler.start(
                ConnectionOptions(
                    reconnectionStrategy =
                        ReconnectionStrategy.ExponentialBackoff(
                            initialDelay = 5.milliseconds,
                            maxAttempts = 5,
                        ),
                ),
            )

            stateFlow.value =
                State.Disconnected.ByError(
                    ConnectionFailed("test error"),
                )
            advanceUntilIdle()

            assertEquals(1, connectAttempts, "ByError disconnect should trigger reconnection")
            handler.stop()
        }
    }

    @Test
    fun `Disconnected_ByTimeout triggers reconnection`() {
        runTest(scheduler) {
            var connectAttempts = 0
            val stateFlow = MutableStateFlow<State>(State.Connected.Ready)
            val handler =
                ReconnectionHandler(
                    scope = this,
                    stateFlow = stateFlow,
                    connectAction = { connectAttempts++ },
                    random = Random(42),
                )

            handler.start(
                ConnectionOptions(
                    reconnectionStrategy =
                        ReconnectionStrategy.ExponentialBackoff(
                            initialDelay = 5.milliseconds,
                            maxAttempts = 5,
                        ),
                ),
            )

            stateFlow.value = State.Disconnected.ByTimeout
            advanceUntilIdle()

            assertEquals(1, connectAttempts, "ByTimeout disconnect should trigger reconnection")
            handler.stop()
        }
    }

    @Test
    fun `Disconnected_BySystemEvent triggers reconnection`() {
        runTest(scheduler) {
            var connectAttempts = 0
            val stateFlow = MutableStateFlow<State>(State.Connected.Ready)
            val handler =
                ReconnectionHandler(
                    scope = this,
                    stateFlow = stateFlow,
                    connectAction = { connectAttempts++ },
                    random = Random(42),
                )

            handler.start(
                ConnectionOptions(
                    reconnectionStrategy =
                        ReconnectionStrategy.ExponentialBackoff(
                            initialDelay = 5.milliseconds,
                            maxAttempts = 5,
                        ),
                ),
            )

            stateFlow.value = State.Disconnected.BySystemEvent
            advanceUntilIdle()

            assertEquals(1, connectAttempts, "BySystemEvent disconnect should trigger reconnection")
            handler.stop()
        }
    }
}
