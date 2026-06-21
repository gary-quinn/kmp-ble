package com.atruedev.kmpble.connection.internal

import com.atruedev.kmpble.connection.RetryPolicy
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.GattStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class GattOperationRetryHandlerTest {
    @Test
    fun `successful operation returns result without retry`() =
        runTest {
            val handler = GattOperationRetryHandler(RetryPolicy.DEFAULT)
            val result = handler.withRetry("read") { "ok" }
            assertEquals("ok", result)
        }

    @Test
    fun `transient GattError Failure is retried`() =
        runTest {
            var attempts = 0
            val handler =
                GattOperationRetryHandler(
                    RetryPolicy(
                        maxAttempts = 3,
                        initialDelay = 50.milliseconds,
                    ),
                )
            val result =
                handler.withRetry("read") {
                    attempts++
                    if (attempts < 3) {
                        throw BleException(GattError("read", GattStatus.Failure))
                    }
                    "success-after-retry"
                }
            assertEquals(3, attempts)
            assertEquals("success-after-retry", result)
        }

    @Test
    fun `transient GattError ConnectionCongested is retried`() =
        runTest {
            var attempts = 0
            val handler =
                GattOperationRetryHandler(
                    RetryPolicy(maxAttempts = 2, initialDelay = 50.milliseconds),
                )
            val result =
                handler.withRetry("write") {
                    attempts++
                    if (attempts < 2) {
                        throw BleException(GattError("write", GattStatus.ConnectionCongested))
                    }
                    "done"
                }
            assertEquals(2, attempts)
            assertEquals("done", result)
        }

    @Test
    fun `non-retryable error propagates immediately`() =
        runTest {
            var attempts = 0
            val handler =
                GattOperationRetryHandler(
                    RetryPolicy(maxAttempts = 3, initialDelay = 50.milliseconds),
                )
            val ex =
                assertFailsWith<BleException> {
                    handler.withRetry("read") {
                        attempts++
                        throw BleException(
                            GattError("read", GattStatus.InsufficientAuthentication),
                        )
                    }
                }
            assertEquals(1, attempts)
            assertIs<GattError>(ex.error)
        }

    @Test
    fun `exhausted retries propagate last error`() =
        runTest {
            var attempts = 0
            val handler =
                GattOperationRetryHandler(
                    RetryPolicy(maxAttempts = 2, initialDelay = 50.milliseconds),
                )
            val ex =
                assertFailsWith<BleException> {
                    handler.withRetry("read") {
                        attempts++
                        throw BleException(GattError("read", GattStatus.Failure))
                    }
                }
            assertEquals(2, attempts)
            assertIs<GattError>(ex.error)
        }

    @Test
    fun `CancellationException propagates immediately without retry`() =
        runTest {
            var attempts = 0
            val handler =
                GattOperationRetryHandler(
                    RetryPolicy(maxAttempts = 3, initialDelay = 50.milliseconds),
                )
            val ex =
                assertFailsWith<CancellationException> {
                    coroutineScope {
                        val job =
                            launch {
                                handler.withRetry("read") {
                                    attempts++
                                    delay(10.seconds)
                                }
                            }
                        delay(50.milliseconds)
                        job.cancel()
                        job.join()
                    }
                }
            assertEquals(1, attempts)
        }

    @Test
    fun `retryDelay follows exponential backoff with cap`() {
        val policy =
            RetryPolicy(
                maxAttempts = 5,
                initialDelay = 100.milliseconds,
                maxDelay = 2.seconds,
            )
        val handler = GattOperationRetryHandler(policy)

        // Attempt 1: delay for *next* attempt = 100ms * 2^0 = 100ms
        assertEquals(100.milliseconds, handler.computeBackoff(1))
        // Attempt 2: delay for *next* attempt = 100ms * 2^1 = 200ms
        assertEquals(200.milliseconds, handler.computeBackoff(2))
        // Attempt 3: delay for *next* attempt = 100ms * 2^2 = 400ms
        assertEquals(400.milliseconds, handler.computeBackoff(3))
        // Attempt 4: 100ms * 2^3 = 800ms
        assertEquals(800.milliseconds, handler.computeBackoff(4))
        // Attempt 5: 100ms * 2^4 = 1600ms
        assertEquals(1600.milliseconds, handler.computeBackoff(5))
        // Attempt 6: 100ms * 2^5 = 3200ms, capped at 2000ms
        assertEquals(2.seconds, handler.computeBackoff(6))
    }

    @Test
    fun `retryDelay with multiplier of 1 produces constant delay`() {
        val policy =
            RetryPolicy(
                maxAttempts = 3,
                initialDelay = 100.milliseconds,
                backoffMultiplier = 1.0,
            )
        val handler = GattOperationRetryHandler(policy)

        assertEquals(100.milliseconds, handler.computeBackoff(1))
        assertEquals(100.milliseconds, handler.computeBackoff(2))
        assertEquals(100.milliseconds, handler.computeBackoff(3))
    }

    @Test
    fun `retryPolicy NONE does not retry`() =
        runTest {
            var attempts = 0
            val handler = GattOperationRetryHandler(RetryPolicy.NONE)
            assertFailsWith<BleException> {
                handler.withRetry("read") {
                    attempts++
                    throw BleException(GattError("read", GattStatus.Failure))
                }
            }
            assertEquals(1, attempts)
        }

    @Test
    fun `Unknown GattStatus with retryable code is retried`() =
        runTest {
            var attempts = 0
            val handler =
                GattOperationRetryHandler(
                    RetryPolicy(
                        maxAttempts = 2,
                        initialDelay = 50.milliseconds,
                        retryableErrors = setOf(133, 143, 42),
                    ),
                )
            val result =
                handler.withRetry("read") {
                    attempts++
                    if (attempts < 2) {
                        throw BleException(
                            GattError("read", GattStatus.Unknown(42, "CUSTOM_ERROR")),
                        )
                    }
                    "ok"
                }
            assertEquals(2, attempts)
        }

    @Test
    fun `Unknown GattStatus with non-retryable code is not retried`() =
        runTest {
            var attempts = 0
            val handler =
                GattOperationRetryHandler(
                    RetryPolicy(
                        maxAttempts = 2,
                        initialDelay = 50.milliseconds,
                        retryableErrors = setOf(133, 143),
                    ),
                )
            assertFailsWith<BleException> {
                handler.withRetry("read") {
                    attempts++
                    throw BleException(
                        GattError("read", GattStatus.Unknown(999, "UNKNOWN_ERROR")),
                    )
                }
            }
            assertEquals(1, attempts)
        }

    @Test
    fun `non-BleException is not caught and propagates immediately`() =
        runTest {
            var attempts = 0
            val handler =
                GattOperationRetryHandler(
                    RetryPolicy(maxAttempts = 3, initialDelay = 50.milliseconds),
                )
            assertFailsWith<IllegalStateException> {
                handler.withRetry("read") {
                    attempts++
                    throw IllegalStateException("unexpected")
                }
            }
            assertEquals(1, attempts)
        }
}
