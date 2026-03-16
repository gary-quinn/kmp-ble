package com.atruedev.kmpble.connection

import com.atruedev.kmpble.connection.internal.ReconnectionHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ReconnectionHandlerTest {

    @Test
    fun noneReturnsNull() {
        assertNull(ReconnectionHandler.computeDelay(ReconnectionStrategy.None, 0))
    }

    @Test
    fun linearReturnsFixedDelay() {
        val strategy = ReconnectionStrategy.LinearBackoff(delay = 2.seconds, maxAttempts = 5)
        assertEquals(2.seconds, ReconnectionHandler.computeDelay(strategy, 0))
        assertEquals(2.seconds, ReconnectionHandler.computeDelay(strategy, 3))
        assertEquals(2.seconds, ReconnectionHandler.computeDelay(strategy, 4))
    }

    @Test
    fun linearReturnsNullAtMaxAttempts() {
        val strategy = ReconnectionStrategy.LinearBackoff(delay = 2.seconds, maxAttempts = 3)
        assertNull(ReconnectionHandler.computeDelay(strategy, 3))
        assertNull(ReconnectionHandler.computeDelay(strategy, 10))
    }

    @Test
    fun exponentialGrowsAndCaps() {
        val strategy = ReconnectionStrategy.ExponentialBackoff(
            initialDelay = 1.seconds,
            maxDelay = 30.seconds,
            maxAttempts = 100,
        )
        assertEquals(1.seconds, ReconnectionHandler.computeDelay(strategy, 0))
        assertEquals(2.seconds, ReconnectionHandler.computeDelay(strategy, 1))
        assertEquals(4.seconds, ReconnectionHandler.computeDelay(strategy, 2))
        assertEquals(8.seconds, ReconnectionHandler.computeDelay(strategy, 3))
        assertEquals(16.seconds, ReconnectionHandler.computeDelay(strategy, 4))
        assertEquals(30.seconds, ReconnectionHandler.computeDelay(strategy, 5)) // capped
        assertEquals(30.seconds, ReconnectionHandler.computeDelay(strategy, 10)) // still capped
    }

    @Test
    fun exponentialReturnsNullAtMaxAttempts() {
        val strategy = ReconnectionStrategy.ExponentialBackoff(
            initialDelay = 1.seconds,
            maxDelay = 30.seconds,
            maxAttempts = 3,
        )
        assertNull(ReconnectionHandler.computeDelay(strategy, 3))
    }

    @Test
    fun exponentialDoesNotOverflowAtHighAttemptCount() {
        val strategy = ReconnectionStrategy.ExponentialBackoff(
            initialDelay = 100.milliseconds,
            maxDelay = 60.seconds,
            maxAttempts = Int.MAX_VALUE,
        )
        // attempt=30 is the max shift (guarded by min(attempt, 30))
        val delay30 = ReconnectionHandler.computeDelay(strategy, 30)!!
        val delay50 = ReconnectionHandler.computeDelay(strategy, 50)!!
        // Both should be capped at maxDelay, not overflow
        assertEquals(60.seconds, delay30)
        assertEquals(60.seconds, delay50)
    }

    @Test
    fun exponentialWithSmallInitialDelay() {
        val strategy = ReconnectionStrategy.ExponentialBackoff(
            initialDelay = 500.milliseconds,
            maxDelay = 10.seconds,
            maxAttempts = 10,
        )
        assertEquals(500.milliseconds, ReconnectionHandler.computeDelay(strategy, 0))
        assertEquals(1.seconds, ReconnectionHandler.computeDelay(strategy, 1))
        assertEquals(2.seconds, ReconnectionHandler.computeDelay(strategy, 2))
        assertEquals(4.seconds, ReconnectionHandler.computeDelay(strategy, 3))
        assertEquals(8.seconds, ReconnectionHandler.computeDelay(strategy, 4))
        assertEquals(10.seconds, ReconnectionHandler.computeDelay(strategy, 5)) // capped
    }
}
