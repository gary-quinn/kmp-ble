package com.atruedev.kmpble.connection

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Configurable retry policy for GATT operations that can fail transiently.
 *
 * GATT operations (read, write, MTU negotiation) can fail due to radio
 * interference, BLE stack congestion, or peripheral unavailability.
 * [RetryPolicy] configures automatic retry behavior with exponential backoff.
 *
 * ## Error filtering
 *
 * [retryableErrors] is a set of platform-specific status codes that trigger
 * a retry. Only errors whose code is in this set are retried; permanent
 * failures (authentication, MTU exceeded) propagate immediately.
 *
 * ## Presets
 *
 * - [RetryPolicy.Companion.NONE] — 1 attempt, no retry. Default, backward compatible.
 * - [RetryPolicy.Companion.DEFAULT] — 3 attempts, 100ms-2s exponential backoff.
 * - [RetryPolicy.Companion.AGGRESSIVE] — 5 attempts, 50ms initial delay.
 *
 * @property maxAttempts Total number of attempts including the first. Minimum 1.
 * @property initialDelay Delay before the first retry.
 * @property maxDelay Maximum delay between retries (caps exponential growth).
 * @property backoffMultiplier Multiplier applied to delay after each attempt.
 * @property retryableErrors Set of platform-specific status codes considered transient.
 */
public data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelay: Duration = 100.milliseconds,
    val maxDelay: Duration = 2.seconds,
    val backoffMultiplier: Double = 2.0,
    val retryableErrors: Set<Int> = DEFAULT_RETRYABLE_ERRORS,
) {
    init {
        require(maxAttempts > 0) {
            "maxAttempts must be positive, was $maxAttempts"
        }
        require(initialDelay >= 50.milliseconds) {
            "initialDelay must be at least 50ms, was $initialDelay"
        }
        require(initialDelay.isPositive() && initialDelay.isFinite()) {
            "initialDelay must be positive and finite, was $initialDelay"
        }
        require(maxDelay.isPositive() && maxDelay.isFinite()) {
            "maxDelay must be positive and finite, was $maxDelay"
        }
        require(backoffMultiplier >= 1.0) {
            "backoffMultiplier must be >= 1.0, was $backoffMultiplier"
        }
    }

    public companion object {
        /**
         * Android GATT_ERROR (133) and GATT_CONNECTION_CONGESTED (143).
         * These are the most common transient GATT failures on Android.
         * iOS does not expose numeric error codes in the same way, so
         * iOS integrations should use [CBError.Code] values or left-shift
         * them into a non-colliding range when building retryable sets.
         */
        public val DEFAULT_RETRYABLE_ERRORS: Set<Int> = setOf(133, 143)

        /** Single attempt, no retry. Backward-compatible default. */
        public val NONE: RetryPolicy = RetryPolicy(maxAttempts = 1)

        /** 3 attempts with 100ms-2s exponential backoff. */
        public val DEFAULT: RetryPolicy = RetryPolicy()

        /** 5 attempts with 50ms initial delay for aggressive recovery. */
        public val AGGRESSIVE: RetryPolicy =
            RetryPolicy(
                maxAttempts = 5,
                initialDelay = 50.milliseconds,
                maxDelay = 1.seconds,
            )
    }
}
