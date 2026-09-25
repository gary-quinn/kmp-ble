package com.atruedev.kmpble.macos.internal

import com.atruedev.kmpble.backend.L2capListenerTransport
import com.atruedev.kmpble.backend.L2capStream
import com.atruedev.kmpble.backend.L2capStreamListener
import com.atruedev.kmpble.l2cap.L2capException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * One `CBL2CAPChannel`. The shim reads the input stream on its own run-loop thread and
 * delivers data and end-of-stream as events; writes block on the output stream, so they run
 * on [Dispatchers.IO].
 */
internal class MacosL2capStream(
    private val stack: MacosStack,
    private val channel: Long,
    override val psm: Int,
    override val mtu: Int = DEFAULT_L2CAP_MTU,
) : L2capStream {
    override fun setListener(listener: L2capStreamListener?) {
        if (listener == null) {
            stack.unregisterChannel(channel)
            return
        }
        stack.registerChannel(channel) { event ->
            when (event) {
                is MacosEvent.L2capData -> listener.onData(event.data)
                is MacosEvent.L2capClosed -> listener.onClosed(event.status.takeIf { it != 0 }?.let { STREAM_ERROR })
                else -> Unit
            }
        }
    }

    override suspend fun write(data: ByteArray) {
        val result = withContext(Dispatchers.IO) { stack.api.l2capWrite(channel, data) }
        if (result != 0) throw L2capException.WriteFailed("CoreBluetooth L2CAP output stream rejected the write")
    }

    override fun close() {
        stack.unregisterChannel(channel)
        stack.api.l2capClose(channel)
    }

    internal companion object {
        const val DEFAULT_L2CAP_MTU = 2048
        private const val STREAM_ERROR = "CoreBluetooth stream error"
    }
}

/** `CBPeripheralManager.publishL2CAPChannel` listener. */
internal class MacosL2capListenerTransport(
    private val stack: MacosStack,
    private val settleTimeout: Duration = SETTLE_TIMEOUT,
) : L2capListenerTransport {
    @Volatile private var accept: ((L2capStream) -> Unit)? = null

    @Volatile private var psm: Int = 0

    @Volatile private var registration: AutoCloseable? = null

    override fun setAcceptListener(listener: ((L2capStream) -> Unit)?) {
        accept = listener
    }

    override suspend fun publish(secure: Boolean): Int {
        val state =
            try {
                stack.awaitPeripheralManagerSettled(settleTimeout)
            } catch (e: MissingUsageDescriptionException) {
                throw L2capException.PublishFailed(e.message ?: "", e)
            }
        if (state != CbManagerState.POWERED_ON) throw L2capException.PublishFailed("Bluetooth is not powered on")
        return stack.l2capPublishLock.withLock {
            val published = CompletableDeferred<Int>()
            registration =
                stack.addPeripheralManagerListener { event ->
                    when (event) {
                        is MacosEvent.PmL2capPublished ->
                            if (event.status == 0) {
                                published.complete(event.psm)
                            } else {
                                published.completeExceptionally(
                                    L2capException.PublishFailed(
                                        event.message ?: "status ${event.status}",
                                    ),
                                )
                            }
                        is MacosEvent.PmL2capOpened ->
                            if (event.psm == psm && event.channel != 0L) {
                                val stream = MacosL2capStream(stack, event.channel, event.psm)
                                accept?.invoke(stream) ?: stream.close()
                            }
                        else -> Unit
                    }
                }
            try {
                stack.api.pmPublishL2cap(secure)
                published.await().also { psm = it }
            } catch (e: Throwable) {
                registration?.close()
                registration = null
                throw e
            }
        }
    }

    override fun close() {
        accept = null
        registration?.close()
        registration = null
        if (psm != 0) stack.api.pmUnpublishL2cap(psm)
        psm = 0
    }

    private companion object {
        val SETTLE_TIMEOUT = 10.seconds
    }
}
