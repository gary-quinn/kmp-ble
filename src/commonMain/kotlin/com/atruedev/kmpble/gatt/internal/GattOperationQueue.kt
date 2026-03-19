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

    private data class QueueEntry(
        val block: suspend () -> Any?,
        val result: CompletableDeferred<Any?>,
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
                    entry.result.completeExceptionally(NotConnectedException())
                    continue
                }
                try {
                    val value = entry.block()
                    entry.result.complete(value)
                } catch (e: Throwable) {
                    entry.result.completeExceptionally(e)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> enqueue(
        timeout: Duration = DEFAULT_OPERATION_TIMEOUT,
        block: suspend () -> T,
    ): T {
        if (!accepting) throw NotConnectedException()

        val deferred = CompletableDeferred<Any?>()
        channel.send(QueueEntry(block, deferred))

        return withTimeout(timeout) {
            deferred.await() as T
        }
    }

    fun drain() {
        accepting = false
        while (true) {
            val entry = channel.tryReceive().getOrNull() ?: break
            entry.result.completeExceptionally(NotConnectedException())
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
