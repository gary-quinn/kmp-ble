package com.atruedev.kmpble.backend.internal

import com.atruedev.kmpble.backend.KmpBleBackendApi
import com.atruedev.kmpble.backend.L2capListenerTransport
import com.atruedev.kmpble.backend.L2capStream
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.l2cap.L2capException
import com.atruedev.kmpble.l2cap.L2capListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

@OptIn(KmpBleBackendApi::class)
internal class BackendL2capListener(
    private val transport: L2capListenerTransport,
) : L2capListener {
    private val _isOpen = MutableStateFlow(false)
    override val isOpen: StateFlow<Boolean> = _isOpen.asStateFlow()

    private val _psm = AtomicInteger(0)
    override val psm: Int get() = _psm.get()

    private val _incoming = MutableSharedFlow<L2capChannel>(extraBufferCapacity = INCOMING_BUFFER)
    override val incoming: SharedFlow<L2capChannel> = _incoming.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val accepted = Channel<L2capStream>(Channel.BUFFERED)
    private val opened = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    override suspend fun open(
        secure: Boolean,
        mtu: Int?,
    ) {
        if (closed.get()) throw L2capException.InvalidState("Listener has been closed")
        if (!opened.compareAndSet(false, true)) throw L2capException.InvalidState("Listener already open")
        if (mtu != null) require(mtu > 0) { "mtu must be positive, was $mtu" }

        transport.setAcceptListener { stream ->
            if (closed.get() || accepted.trySend(stream).isFailure) runCatching { stream.close() }
        }
        scope.launch {
            for (stream in accepted) {
                val channel = BackendL2capChannel(stream, mtu ?: stream.mtu, recovery = null)
                try {
                    _incoming.emit(channel)
                } catch (e: CancellationException) {
                    channel.close()
                    throw e
                }
            }
        }

        val assigned =
            try {
                withTimeout(PUBLISH_TIMEOUT) { transport.publish(secure) }
            } catch (e: TimeoutCancellationException) {
                close()
                throw L2capException.PublishFailed("L2CAP publish timed out after $PUBLISH_TIMEOUT", e)
            } catch (e: L2capException) {
                close()
                throw e
            } catch (e: CancellationException) {
                close()
                throw e
            } catch (e: Exception) {
                close()
                throw L2capException.PublishFailed(e.message ?: "L2CAP publish failed", e)
            }
        _psm.set(assigned)
        _isOpen.value = true
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        _isOpen.value = false
        transport.setAcceptListener(null)
        runCatching { transport.close() }
        accepted.close()
        while (true) {
            val orphan = accepted.tryReceive().getOrNull() ?: break
            runCatching { orphan.close() }
        }
        scope.cancel()
    }

    private companion object {
        const val INCOMING_BUFFER = 16
        val PUBLISH_TIMEOUT = 10.seconds
    }
}
