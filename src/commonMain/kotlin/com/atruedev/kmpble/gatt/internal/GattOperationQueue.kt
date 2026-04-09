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
 */
internal class GattOperationQueue(
    private val scope: CoroutineScope,
) {
    private class QueueEntry(
        val action: suspend () -> Unit,
        val cancel: (Throwable) -> Unit,
    )

    @Volatile
    private var channel = Channel<QueueEntry>(Channel.UNLIMITED)
    private var drainJob: Job? = null
    private var operationTimeout: Duration = DEFAULT_OPERATION_TIMEOUT

    fun start(timeout: Duration? = null) {
        drainJob?.cancel()
        channel.close()
        if (timeout != null) operationTimeout = timeout
        val ch = Channel<QueueEntry>(Channel.UNLIMITED)
        channel = ch
        drainJob =
            scope.launch {
                for (entry in ch) {
                    entry.action()
                }
            }
    }

    suspend fun <T> enqueue(
        timeout: Duration = operationTimeout,
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

        if (!channel.trySend(entry).isSuccess) throw NotConnectedException()

        return withTimeout(timeout) {
            deferred.await()
        }
    }

    /**
     * Stops accepting new operations and cancels all queued entries.
     *
     * Thread-safety invariant: [enqueue] checks [accepting] before inserting into [channel],
     * so once [accepting] is set to `false`, no new entries can be enqueued. The drain loop
     * only needs to clear entries that were already in the channel at that point.
     */
    fun drain() {
        val ch = channel
        ch.close()
        generateSequence { ch.tryReceive().getOrNull() }
            .forEach { it.cancel(NotConnectedException()) }
    }

    fun close() {
        drainJob?.cancel()
        drain()
    }
}

internal class NotConnectedException : Exception("Peripheral is not connected")

private val DEFAULT_OPERATION_TIMEOUT = 10.seconds
