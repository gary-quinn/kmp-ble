package com.atruedev.kmpble.connection.internal

import com.atruedev.kmpble.connection.RetryPolicy
import com.atruedev.kmpble.error.BleError
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.GattStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Wraps GATT operations with configurable retry logic and exponential backoff.
 *
 * Follows the [com.atruedev.kmpble.monitoring.ConnectionQualityMonitor] pattern:
 * a standalone wrapper that composes with platform implementations without
 * changing the [com.atruedev.kmpble.peripheral.Peripheral] interface.
 *
 * ## Usage
 * ```
 * val handler = GattOperationRetryHandler(RetryPolicy.DEFAULT)
 * val result = handler.withRetry("read") { peripheral.read(char) }
 * ```
 *
 * ## Error semantics
 * - [CancellationException] always propagates immediately (never retried).
 * - Non-retryable [BleError] codes propagate immediately.
 * - Retryable errors trigger backoff and are retried up to [RetryPolicy.maxAttempts].
 * - After all attempts exhausted, the last error propagates.
 *
 * @param policy The retry policy controlling attempt count and backoff.
 */
public class GattOperationRetryHandler(
    private val policy: RetryPolicy,
) {
    /**
     * Execute [block] with retry logic governed by [policy].
     *
     * @param operationName Human-readable name for logging/debugging (e.g., "read", "write").
     * @param block The GATT operation to execute. Should throw [BleException] on failure.
     * @return The result of a successful [block] invocation.
     */
    public suspend fun <T> withRetry(
        operationName: String,
        block: suspend () -> T,
    ): T {
        var lastError: BleException? = null

        for (attempt in 1..policy.maxAttempts) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BleException) {
                lastError = e
                if (attempt >= policy.maxAttempts) break

                val errorCode = extractErrorCode(e.error)
                if (errorCode == null || errorCode !in policy.retryableErrors) {
                    throw e
                }

                val backoff = computeBackoff(attempt)
                delay(backoff)
            }
        }

        // Exhausted all attempts -- propagate last error
        throw lastError!!
    }

    /**
     * Compute exponential backoff: initialDelay * multiplier^(attempt-1), capped at maxDelay.
     *
     * Attempt 1 is the initial call; retry 1 (attempt 2) gets initialDelay.
     */
    internal fun computeBackoff(attempt: Int): Duration {
        // attempt is 1-indexed; the first *retry* occurs after attempt 1.
        // Compute delay for the *next* attempt.
        val retryIndex = attempt - 1
        val raw =
            policy.initialDelay.inWholeMilliseconds *
                policy.backoffMultiplier.pow(retryIndex)
        return (min(raw.toLong(), policy.maxDelay.inWholeMilliseconds)).milliseconds
    }

    /**
     * Extract a numeric error code from a [BleError] for retry classification.
     *
     * Returns `null` if the error has no retryable code (not a GATT transient).
     * Maps known [GattStatus] variants to their Android canonical codes:
     * - [GattStatus.Failure] → 133 (GATT_ERROR)
     * - [GattStatus.ConnectionCongested] → 143 (GATT_CONNECTION_CONGESTED)
     * - [GattStatus.Unknown] → [GattStatus.Unknown.platformCode]
     * - All others → null (permanent failures: auth, encryption, etc.)
     */
    private fun extractErrorCode(error: BleError): Int? {
        if (error !is GattError) return null
        return when (val status = error.status) {
            is GattStatus.Failure -> 133
            is GattStatus.ConnectionCongested -> 143
            is GattStatus.Unknown -> status.platformCode
            else -> null
        }
    }

    private companion object {
        /**
         * Compute base^exp as Double, useful for backoff calculation.
         */
        private fun Double.pow(exp: Int): Double {
            var result = 1.0
            repeat(exp) { result *= this }
            return result
        }
    }
}
