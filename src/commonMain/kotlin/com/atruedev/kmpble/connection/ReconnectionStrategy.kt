package com.atruedev.kmpble.connection

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public sealed class ReconnectionStrategy {
    public data object None : ReconnectionStrategy()

    public data class ExponentialBackoff(
        val initialDelay: Duration = 1.seconds,
        val maxDelay: Duration = 30.seconds,
        val maxAttempts: Int = Int.MAX_VALUE,
    ) : ReconnectionStrategy()

    public data class LinearBackoff(
        val delay: Duration = 2.seconds,
        val maxAttempts: Int = 10,
    ) : ReconnectionStrategy()
}
