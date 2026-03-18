package com.atruedev.kmpble.l2cap

import android.annotation.SuppressLint
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android implementation of [L2capChannel] using [BluetoothSocket].
 *
 * The socket provides blocking [InputStream]/[OutputStream] which are wrapped
 * in coroutines for the suspend-based API.
 *
 * ## Threading
 *
 * - Read loop runs on [Dispatchers.IO] to avoid blocking the main thread
 * - Write operations dispatch to [Dispatchers.IO] for blocking socket writes
 * - Close can be called from any thread
 *
 * ## MTU
 *
 * Android's L2CAP CoC uses a default MTU of 672 bytes, but the actual negotiated
 * MTU is not exposed via public API. We use [BluetoothSocket.maxTransmitPacketSize]
 * (API 23+) as the write-side limit, falling back to a conservative default.
 */
internal class AndroidL2capChannel(
    private val socket: BluetoothSocket,
    override val psm: Int,
    private val scope: CoroutineScope,
) : L2capChannel {

    private val closed = AtomicBoolean(false)

    private val inputStream: InputStream = socket.inputStream
    private val outputStream: OutputStream = socket.outputStream

    private val closedDeferred = CompletableDeferred<Unit>()

    override val mtu: Int
        @SuppressLint("NewApi")
        get() = try {
            maxOf(socket.maxTransmitPacketSize, DEFAULT_MTU)
        } catch (_: Exception) {
            DEFAULT_MTU
        }

    override val isOpen: Boolean
        get() = !closed.get() && socket.isConnected

    private val incomingChannel = Channel<ByteArray>(Channel.BUFFERED)

    override val incoming: Flow<ByteArray> = incomingChannel.receiveAsFlow()

    internal val readJob: Job

    init {
        readJob = startReadLoop()
    }

    /**
     * Suspend until the channel is closed (locally or remotely).
     * Used by [AndroidPeripheral][com.atruedev.kmpble.peripheral.AndroidPeripheral]
     * to track active channels without consuming [incoming] data.
     */
    internal suspend fun awaitClosed() {
        closedDeferred.await()
    }

    private fun startReadLoop(): Job = scope.launch(Dispatchers.IO) {
        val buffer = ByteArray(mtu.coerceAtLeast(READ_BUFFER_SIZE))

        try {
            while (isActive && !closed.get()) {
                try {
                    val bytesRead = inputStream.read(buffer)

                    if (bytesRead == -1) {
                        break
                    }

                    if (bytesRead > 0) {
                        val data = buffer.copyOf(bytesRead)
                        incomingChannel.send(data)
                    }
                } catch (_: IOException) {
                    if (!closed.get()) {
                        break
                    }
                    break
                }
            }
        } finally {
            incomingChannel.close()
            if (closed.compareAndSet(false, true)) {
                closeSocket()
            }
            closedDeferred.complete(Unit)
        }
    }

    override suspend fun write(data: ByteArray) {
        if (closed.get()) {
            throw L2capException.ChannelClosed()
        }

        if (!socket.isConnected) {
            throw L2capException.ChannelClosed("Socket is not connected")
        }

        withContext(Dispatchers.IO) {
            try {
                var totalWritten = 0
                while (totalWritten < data.size) {
                    if (closed.get()) {
                        throw L2capException.ChannelClosed("Channel closed during write")
                    }
                    outputStream.write(data, totalWritten, data.size - totalWritten)
                    totalWritten = data.size
                    outputStream.flush()
                }
            } catch (e: L2capException) {
                throw e
            } catch (e: IOException) {
                throw L2capException.WriteFailed(e.message ?: "Write failed", e)
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }

        readJob.cancel()
        closeSocket()
        incomingChannel.close()
        closedDeferred.complete(Unit)
    }

    @SuppressLint("NewApi")
    private fun closeSocket() {
        try {
            inputStream.close()
        } catch (_: IOException) { }

        try {
            outputStream.close()
        } catch (_: IOException) { }

        try {
            socket.close()
        } catch (_: IOException) { }
    }

    private companion object {
        const val DEFAULT_MTU = 672
        const val READ_BUFFER_SIZE = 4096
    }
}
