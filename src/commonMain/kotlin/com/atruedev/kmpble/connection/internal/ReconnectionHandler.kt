package com.atruedev.kmpble.connection.internal

import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.ReconnectionStrategy
import com.atruedev.kmpble.connection.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal class ReconnectionHandler(
    private val scope: CoroutineScope,
    private val stateFlow: StateFlow<State>,
    private val connectAction: suspend (ConnectionOptions) -> Unit,
    private val onMaxAttemptsExhausted: (suspend () -> Unit)? = null,
    private val random: Random = Random,
) {
    private var options: ConnectionOptions? = null
    private var job: Job? = null
    private var attempt = 0

    internal fun start(connectionOptions: ConnectionOptions) {
        if (connectionOptions.reconnectionStrategy is ReconnectionStrategy.None) return
        job?.cancel()
        options = connectionOptions
        attempt = 0

        job =
            scope.launch {
                stateFlow.collect { state ->
                    if (state is State.Disconnected && state !is State.Disconnected.ByRequest) {
                        val opts = options ?: return@collect
                        val nextDelay =
                            computeDelay(opts.reconnectionStrategy, attempt) ?: run {
                                onMaxAttemptsExhausted?.invoke()
                                stop()
                                return@collect
                            }
                        attempt++
                        delay(nextDelay)
                        try {
                            connectAction(opts)
                            attempt = 0
                        } catch (_: Exception) {
                            // Will re-enter this collector on next Disconnected state
                        }
                    } else if (state is State.Connected.Ready) {
                        attempt = 0
                    }
                }
            }
    }

    internal fun stop() {
        job?.cancel()
        job = null
        options = null
        attempt = 0
    }

    internal fun computeDelay(
        strategy: ReconnectionStrategy,
        attempt: Int,
    ): Duration? =
        when (strategy) {
            is ReconnectionStrategy.None -> null
            is ReconnectionStrategy.ExponentialBackoff -> {
                if (attempt >= strategy.maxAttempts) {
                    null
                } else {
                    val shift = min(attempt, MAX_BACKOFF_SHIFT)
                    val baseMs = strategy.initialDelay.inWholeMilliseconds
                    val maxMs = strategy.maxDelay.inWholeMilliseconds
                    val multiplier = 1L shl shift
                    val delayMs =
                        if (baseMs > maxMs / multiplier) {
                            maxMs
                        } else {
                            min(baseMs * multiplier, maxMs)
                        }
                    // ±20% jitter. At sub-5ms delays, truncation to Long zeros out the jitter -
                    // acceptable since sub-5ms backoff is not a thundering-herd risk.
                    val jitter = (delayMs * 0.2 * (2.0 * random.nextDouble() - 1.0)).toLong()
                    (delayMs + jitter).coerceAtLeast(1).milliseconds
                }
            }
            is ReconnectionStrategy.LinearBackoff -> {
                if (attempt >= strategy.maxAttempts) {
                    null
                } else {
                    strategy.delay
                }
            }
        }

    private companion object {
        const val MAX_BACKOFF_SHIFT = 30
    }
}
