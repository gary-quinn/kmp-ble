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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal class ReconnectionHandler(
    private val scope: CoroutineScope,
    private val stateFlow: StateFlow<State>,
    private val connectAction: suspend (ConnectionOptions) -> Unit,
    private val onMaxAttemptsExhausted: (suspend () -> Unit)? = null,
) {
    private var options: ConnectionOptions? = null
    private var job: Job? = null
    private var attempt = 0

    internal fun start(connectionOptions: ConnectionOptions) {
        if (connectionOptions.reconnectionStrategy is ReconnectionStrategy.None) return
        job?.cancel()
        options = connectionOptions
        attempt = 0

        job = scope.launch {
            stateFlow.collect { state ->
                if (state is State.Disconnected && state !is State.Disconnected.ByRequest) {
                    val opts = options ?: return@collect
                    val nextDelay = computeDelay(opts.reconnectionStrategy, attempt) ?: run {
                        onMaxAttemptsExhausted?.invoke()
                        stop()
                        return@collect
                    }
                    attempt++
                    delay(nextDelay)
                    try {
                        connectAction(opts)
                        attempt = 0
                    } catch (_: Throwable) {
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

    internal companion object {
        internal fun computeDelay(strategy: ReconnectionStrategy, attempt: Int): Duration? =
            when (strategy) {
                is ReconnectionStrategy.None -> null
                is ReconnectionStrategy.ExponentialBackoff -> {
                    if (attempt >= strategy.maxAttempts) null
                    else {
                        val delayMs = strategy.initialDelay.inWholeMilliseconds *
                            (1L shl min(attempt, 30))
                        val capped = min(delayMs, strategy.maxDelay.inWholeMilliseconds)
                        capped.milliseconds
                    }
                }
                is ReconnectionStrategy.LinearBackoff -> {
                    if (attempt >= strategy.maxAttempts) null
                    else strategy.delay
                }
            }
    }
}
