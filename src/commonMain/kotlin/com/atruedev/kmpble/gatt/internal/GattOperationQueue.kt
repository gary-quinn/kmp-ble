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

/** Serializes GATT operations into a single-consumer queue with timeout. */
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

    private val state =
        atomic(
            QueueState(
                channel = Channel(256),
                drainJob = null,
                operationTimeout = DEFAULT_OPERATION_TIMEOUT,
            ),
        )

    fun start(timeout: Duration? = null) {
        val prev = state.value
        drainChannel(prev.channel)
        prev.drainJob?.cancel()

        val ch = Channel<QueueEntry>(256)
        val job =
            scope.launch {
                for (entry in ch) {
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

        if (!state.value.channel
                .trySend(entry)
                .isSuccess
        ) {
            throw NotConnectedException()
        }

        return withTimeout(timeout) {
            deferred.await()
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
