package com.atruedev.kmpble.connection

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Strategy applied when a peripheral disconnects unexpectedly.
 *
 * Set via [ConnectionOptions.reconnectionStrategy]. The strategy only activates on
 * unintentional disconnects — calling [com.atruedev.kmpble.peripheral.Peripheral.disconnect]
 * explicitly will not trigger reconnection.
 */
public sealed class ReconnectionStrategy {
    /** Do not reconnect automatically. */
    public data object None : ReconnectionStrategy()

    /**
     * Reconnect with exponential backoff.
     *
     * @property initialDelay Delay before the first attempt.
     * @property maxDelay Upper bound on the delay between attempts.
     * @property maxAttempts Maximum number of attempts before giving up.
     */
    public data class ExponentialBackoff(
        val initialDelay: Duration = 1.seconds,
        val maxDelay: Duration = 30.seconds,
        val maxAttempts: Int = Int.MAX_VALUE,
    ) : ReconnectionStrategy()

    /**
     * Reconnect with a fixed delay between attempts.
     *
     * @property delay Constant delay between reconnection attempts.
     * @property maxAttempts Maximum number of attempts before giving up.
     */
    public data class LinearBackoff(
        val delay: Duration = 2.seconds,
        val maxAttempts: Int = 10,
    ) : ReconnectionStrategy()
}
