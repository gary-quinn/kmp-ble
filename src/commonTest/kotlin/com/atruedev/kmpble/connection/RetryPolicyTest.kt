package com.atruedev.kmpble.connection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RetryPolicyTest {
    @Test
    fun `default preset has 3 attempts and 100ms initial delay`() {
        val policy = RetryPolicy.DEFAULT
        assertEquals(3, policy.maxAttempts)
        assertEquals(100.milliseconds, policy.initialDelay)
        assertEquals(2.seconds, policy.maxDelay)
        assertEquals(2.0, policy.backoffMultiplier)
        assertEquals(RetryPolicy.DEFAULT_RETRYABLE_ERRORS, policy.retryableErrors)
    }

    @Test
    fun `NONE preset is single attempt`() {
        val policy = RetryPolicy.NONE
        assertEquals(1, policy.maxAttempts)
    }

    @Test
    fun `AGGRESSIVE preset has 5 attempts and 50ms delay`() {
        val policy = RetryPolicy.AGGRESSIVE
        assertEquals(5, policy.maxAttempts)
        assertEquals(50.milliseconds, policy.initialDelay)
        assertEquals(1.seconds, policy.maxDelay)
    }

    @Test
    fun `default retryable errors include GATT_ERROR and GATT_CONNECTION_CONGESTED`() {
        assertTrue(133 in RetryPolicy.DEFAULT_RETRYABLE_ERRORS)
        assertTrue(143 in RetryPolicy.DEFAULT_RETRYABLE_ERRORS)
    }

    @Test
    fun `custom retryable errors override defaults`() {
        val policy = RetryPolicy(retryableErrors = setOf(42))
        assertEquals(setOf(42), policy.retryableErrors)
    }

    @Test
    fun `maxAttempts zero rejected`() {
        assertFailsWith<IllegalArgumentException> {
            RetryPolicy(maxAttempts = 0)
        }
    }

    @Test
    fun `maxAttempts negative rejected`() {
        assertFailsWith<IllegalArgumentException> {
            RetryPolicy(maxAttempts = -1)
        }
    }

    @Test
    fun `initialDelay below 50ms rejected`() {
        assertFailsWith<IllegalArgumentException> {
            RetryPolicy(initialDelay = 49.milliseconds)
        }
    }

    @Test
    fun `initialDelay at exactly 50ms accepted`() {
        val policy = RetryPolicy(initialDelay = 50.milliseconds)
        assertEquals(50.milliseconds, policy.initialDelay)
    }

    @Test
    fun `backoffMultiplier below 1 rejected`() {
        assertFailsWith<IllegalArgumentException> {
            RetryPolicy(backoffMultiplier = 0.5)
        }
    }

    @Test
    fun `backoffMultiplier exactly 1 accepted`() {
        val policy = RetryPolicy(backoffMultiplier = 1.0)
        assertEquals(1.0, policy.backoffMultiplier)
    }

    @Test
    fun `all custom fields accepted`() {
        val policy =
            RetryPolicy(
                maxAttempts = 4,
                initialDelay = 200.milliseconds,
                maxDelay = 5.seconds,
                backoffMultiplier = 3.0,
                retryableErrors = setOf(100, 200),
            )
        assertEquals(4, policy.maxAttempts)
        assertEquals(200.milliseconds, policy.initialDelay)
        assertEquals(5.seconds, policy.maxDelay)
        assertEquals(3.0, policy.backoffMultiplier)
        assertEquals(setOf(100, 200), policy.retryableErrors)
    }
}
