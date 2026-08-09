package com.atruedev.kmpble.gatt.internal

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
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
 * [enqueue] reads the [kotlinx.atomicfu.atomic] [state] snapshot from any
 * coroutine context.
 *
 * ## Cancellation propagation
 *
 * Each action runs as a child job of the queue scope, joined by the drain loop.
 * When the caller's coroutine is cancelled or its [withTimeout] fires, the
 * in-flight action's child job is cancelled, so the action observes
 * [kotlinx.coroutines.CancellationException] and can clean up (e.g. abort a
 * reliable-write transaction). An action that has not yet started is marked
 * cancelled and skipped by the drain loop. Without this, a cancelled caller
 * would leave the action running to completion in the background -- harmless
 * for a plain read, fatal for a multi-chunk reliable write that could commit
 * after the caller already gave up.
 */
internal class GattOperationQueue(
    private val scope: CoroutineScope,
) {
    private class QueueEntry(
        val action: suspend () -> Unit,
        val cancel: (Throwable) -> Unit,
    ) {
        /** The drain loop's child job running [action]; null until it starts. */
        val job = atomic<Job?>(null)

        /** Set when the caller is cancelled before the action started. */
        val cancelled = atomic<Throwable?>(null)
    }

    private data class QueueState(
        val channel: Channel<QueueEntry>,
        val drainJob: Job?,
        val operationTimeout: Duration,
    )

    private val state =
        atomic(
            QueueState(
                channel = Channel(Channel.UNLIMITED),
                drainJob = null,
                operationTimeout = DEFAULT_OPERATION_TIMEOUT,
            ),
        )

    fun start(timeout: Duration? = null) {
        val prev = state.value
        drainChannel(prev.channel)
        prev.drainJob?.cancel()

        val ch = Channel<QueueEntry>(Channel.UNLIMITED)
        val job =
            scope.launch {
                for (entry in ch) {
                    entry.cancelled.value?.let { cause ->
                        entry.cancel(cause)
                        continue
                    }
                    entry.action()
                }
            }
        state.value =
            QueueState(
                channel = ch,
                drainJob = job,
                operationTimeout = timeout ?: prev.operationTimeout,
            )
    }

    suspend fun <T> enqueue(
        timeout: Duration = state.value.operationTimeout,
        block: suspend () -> T,
    ): T {
        val deferred = CompletableDeferred<T>()
        lateinit var entry: QueueEntry
        entry =
            QueueEntry(
                action = {
                    val child =
                        scope.launch {
                            try {
                                deferred.complete(block())
                            } catch (e: Throwable) {
                                deferred.completeExceptionally(e)
                            }
                        }
                    entry.job.value = child
                    // If the caller was cancelled while this action was queued but
                    // not yet started, kill it immediately.
                    if (entry.cancelled.value != null) child.cancel()
                    child.join()
                },
                cancel = { deferred.completeExceptionally(it) },
            )

        if (!state.value.channel
                .trySend(entry)
                .isSuccess
        ) {
            throw NotConnectedException()
        }

        return try {
            withTimeout(timeout) {
                deferred.await()
            }
        } catch (e: Throwable) {
            // Cancel the in-flight action (if running) or mark it cancelled (if
            // still queued) so it does not keep executing after the caller gave up.
            entry.job.value?.cancel()
            entry.cancelled.compareAndSet(null, e)
            throw e
        }
    }

    fun drain() {
        drainChannel(state.value.channel)
    }

    fun close() {
        val s = state.value
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
