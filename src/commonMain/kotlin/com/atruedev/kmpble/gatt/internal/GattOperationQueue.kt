package com.atruedev.kmpble.gatt.internal

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Serializes GATT operations into a single-consumer queue with timeout support.
 *
 * Acceptance is controlled by channel lifecycle: [start] opens a new channel,
 * [drain] closes it. [Channel.trySend] on a closed channel fails atomically,
 * eliminating the TOCTOU window that a separate flag would introduce.
 *
 * [start], [drain], and [close] are confined to the owning peripheral's
 * serialized dispatcher (`limitedParallelism(1)`).
 * [enqueue] reads the `@Volatile` [state] snapshot from any coroutine context.
 */
internal class GattOperationQueue(
    private val scope: CoroutineScope,
) {
    private class QueueEntry(
        val action: suspend () -> Unit,
        val cancel: (Throwable) -> Unit,
    )

    private data class QueueState(
        val channel: Channel<QueueEntry>,
        val drainJob: Job?,
        val operationTimeout: Duration,
    )

    @Volatile
    private var state = QueueState(
        channel = Channel(Channel.UNLIMITED),
        drainJob = null,
        operationTimeout = DEFAULT_OPERATION_TIMEOUT,
    )

    fun start(timeout: Duration? = null) {
        val prev = state
        prev.drainJob?.cancel()
        drainChannel(prev.channel)

        val ch = Channel<QueueEntry>(Channel.UNLIMITED)
        val job = scope.launch {
            for (entry in ch) {
                entry.action()
            }
        }
        state = QueueState(
            channel = ch,
            drainJob = job,
            operationTimeout = timeout ?: prev.operationTimeout,
        )
    }

    suspend fun <T> enqueue(
        timeout: Duration = state.operationTimeout,
        block: suspend () -> T,
    ): T {
        val deferred = CompletableDeferred<T>()
        val entry =
            QueueEntry(
                action = {
                    try {
                        deferred.complete(block())
                    } catch (e: Throwable) {
                        deferred.completeExceptionally(e)
                    }
                },
                cancel = { deferred.completeExceptionally(it) },
            )

        if (!state.channel.trySend(entry).isSuccess) throw NotConnectedException()

        return withTimeout(timeout) {
            deferred.await()
        }
    }

    fun drain() {
        drainChannel(state.channel)
    }

    fun close() {
        val s = state
        drainChannel(s.channel)
        s.drainJob?.cancel()
    }

    private fun drainChannel(ch: Channel<QueueEntry>) {
        ch.close()
        while (true) {
            val entry = ch.tryReceive().getOrNull() ?: break
            entry.cancel(NotConnectedException())
        }
    }
}

internal class NotConnectedException : Exception("Peripheral is not connected")

private val DEFAULT_OPERATION_TIMEOUT = 10.seconds
