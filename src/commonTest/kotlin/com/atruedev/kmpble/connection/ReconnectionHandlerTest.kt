package com.atruedev.kmpble.connection

import com.atruedev.kmpble.connection.internal.ReconnectionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ReconnectionHandlerTest {
    private val noJitter =
        object : Random() {
            override fun nextBits(bitCount: Int): Int = 0

            override fun nextDouble(): Double = 0.5
        }

    private fun handler(random: Random = noJitter) =
        ReconnectionHandler(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Job()),
            stateFlow = MutableStateFlow(State.Disconnected.ByRequest),
            connectAction = {},
            random = random,
        )

    @Test
    fun noneReturnsNull() {
        assertNull(handler().computeDelay(ReconnectionStrategy.None, 0))
    }

    @Test
    fun linearReturnsFixedDelay() {
        val h = handler()
        val strategy = ReconnectionStrategy.LinearBackoff(delay = 2.seconds, maxAttempts = 5)
        assertEquals(2.seconds, h.computeDelay(strategy, 0))
        assertEquals(2.seconds, h.computeDelay(strategy, 3))
        assertEquals(2.seconds, h.computeDelay(strategy, 4))
    }

    @Test
    fun linearReturnsNullAtMaxAttempts() {
        val h = handler()
        val strategy = ReconnectionStrategy.LinearBackoff(delay = 2.seconds, maxAttempts = 3)
        assertNull(h.computeDelay(strategy, 3))
        assertNull(h.computeDelay(strategy, 10))
    }

    @Test
    fun exponentialGrowsAndCaps() {
        val h = handler()
        val strategy =
            ReconnectionStrategy.ExponentialBackoff(
                initialDelay = 1.seconds,
                maxDelay = 30.seconds,
                maxAttempts = 100,
            )
        assertEquals(1.seconds, h.computeDelay(strategy, 0))
        assertEquals(2.seconds, h.computeDelay(strategy, 1))
        assertEquals(4.seconds, h.computeDelay(strategy, 2))
        assertEquals(8.seconds, h.computeDelay(strategy, 3))
        assertEquals(16.seconds, h.computeDelay(strategy, 4))
        assertEquals(30.seconds, h.computeDelay(strategy, 5))
        assertEquals(30.seconds, h.computeDelay(strategy, 10))
    }

    @Test
    fun exponentialReturnsNullAtMaxAttempts() {
        val h = handler()
        val strategy =
            ReconnectionStrategy.ExponentialBackoff(
                initialDelay = 1.seconds,
                maxDelay = 30.seconds,
                maxAttempts = 3,
            )
        assertNull(h.computeDelay(strategy, 3))
    }

    @Test
    fun exponentialDoesNotOverflowAtHighAttemptCount() {
        val h = handler()
        val strategy =
            ReconnectionStrategy.ExponentialBackoff(
                initialDelay = 100.milliseconds,
                maxDelay = 60.seconds,
                maxAttempts = Int.MAX_VALUE,
            )
        assertEquals(60.seconds, h.computeDelay(strategy, 30))
        assertEquals(60.seconds, h.computeDelay(strategy, 50))
    }

    @Test
    fun exponentialWithSmallInitialDelay() {
        val h = handler()
        val strategy =
            ReconnectionStrategy.ExponentialBackoff(
                initialDelay = 500.milliseconds,
                maxDelay = 10.seconds,
                maxAttempts = 10,
            )
        assertEquals(500.milliseconds, h.computeDelay(strategy, 0))
        assertEquals(1.seconds, h.computeDelay(strategy, 1))
        assertEquals(2.seconds, h.computeDelay(strategy, 2))
        assertEquals(4.seconds, h.computeDelay(strategy, 3))
        assertEquals(8.seconds, h.computeDelay(strategy, 4))
        assertEquals(10.seconds, h.computeDelay(strategy, 5))
    }

    @Test
    fun exponentialJitterBounds() {
        val strategy =
            ReconnectionStrategy.ExponentialBackoff(
                initialDelay = 1.seconds,
                maxDelay = 60.seconds,
                maxAttempts = 10,
            )
        val minRandom =
            object : Random() {
                override fun nextBits(bitCount: Int): Int = 0

                override fun nextDouble(): Double = 0.0
            }
        val maxRandom =
            object : Random() {
                override fun nextBits(bitCount: Int): Int = 0

                override fun nextDouble(): Double = 1.0
            }

        assertEquals(800.milliseconds, handler(minRandom).computeDelay(strategy, 0))
        assertEquals(1200.milliseconds, handler(maxRandom).computeDelay(strategy, 0))
    }

    @Test
    fun linearIsNotAffectedByJitter() {
        val extremeRandom =
            object : Random() {
                override fun nextBits(bitCount: Int): Int = 0

                override fun nextDouble(): Double = 1.0
            }
        val strategy = ReconnectionStrategy.LinearBackoff(delay = 2.seconds, maxAttempts = 5)
        assertEquals(2.seconds, handler(extremeRandom).computeDelay(strategy, 0))
    }
}
