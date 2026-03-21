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

internal class GattOperationQueue(private val scope: CoroutineScope) {

    private class QueueEntry(
        val action: suspend () -> Unit,
        val cancel: (Throwable) -> Unit,
    )

    private val channel = Channel<QueueEntry>(Channel.UNLIMITED)
    private var drainJob: Job? = null

    @Volatile
    private var accepting = false

    fun start() {
        drainJob?.cancel()
        accepting = true
        drainJob = scope.launch {
            for (entry in channel) {
                if (!accepting) {
                    entry.cancel(NotConnectedException())
                    continue
                }
                entry.action()
            }
        }
    }

    suspend fun <T> enqueue(
        timeout: Duration = DEFAULT_OPERATION_TIMEOUT,
        block: suspend () -> T,
    ): T {
        if (!accepting) throw NotConnectedException()

        val deferred = CompletableDeferred<T>()
        val entry = QueueEntry(
            action = {
                try {
                    deferred.complete(block())
                } catch (e: Throwable) {
                    deferred.completeExceptionally(e)
                }
            },
            cancel = { deferred.completeExceptionally(it) },
        )
        channel.send(entry)

        return withTimeout(timeout) {
            deferred.await()
        }
    }

    fun drain() {
        accepting = false
        while (true) {
            val entry = channel.tryReceive().getOrNull() ?: break
            entry.cancel(NotConnectedException())
        }
    }

    fun close() {
        drain()
        drainJob?.cancel()
        channel.close()
    }
}

internal class NotConnectedException : Exception("Peripheral is not connected")

private val DEFAULT_OPERATION_TIMEOUT = 10.seconds
