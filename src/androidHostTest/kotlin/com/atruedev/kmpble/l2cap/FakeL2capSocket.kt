package com.atruedev.kmpble.l2cap

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Test double for [L2capSocket] backed by piped streams.
 *
 * [remoteOutput] feeds data into the channel's read loop (simulates remote writes).
 * [localCapture] captures data the channel writes (simulates remote reads).
 *
 * Call [simulateRemoteClose] to close the remote end, triggering EOF on the read loop.
 */
internal class FakeL2capSocket(
    override val maxTransmitPacketSize: Int = 672,
) : L2capSocket {
    private val _isConnected = AtomicBoolean(true)
    override val isConnected: Boolean get() = _isConnected.get()

    private val remoteToLocal = PipedOutputStream()
    private val localInput = PipedInputStream(remoteToLocal, 8192)

    private val localToRemote = PipedOutputStream()
    private val remoteCapture = PipedInputStream(localToRemote, 8192)

    /** Write here to push data into the channel's read loop. */
    val remoteOutput: OutputStream get() = remoteToLocal

    /** Read from here to capture data the channel wrote. */
    val localCapture: InputStream get() = remoteCapture

    override val inputStream: InputStream get() = localInput
    override val outputStream: OutputStream get() = localToRemote

    private val closed = AtomicBoolean(false)

    /**
     * Simulate the remote device closing the connection.
     * The channel's read loop will see EOF or IOException.
     */
    fun simulateRemoteClose() {
        _isConnected.set(false)
        try {
            remoteToLocal.close()
        } catch (_: IOException) {
        }
    }

    fun simulateDisconnect() {
        _isConnected.set(false)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        _isConnected.set(false)
        try {
            localInput.close()
        } catch (_: IOException) {
        }
        try {
            localToRemote.close()
        } catch (_: IOException) {
        }
        try {
            remoteToLocal.close()
        } catch (_: IOException) {
        }
        try {
            remoteCapture.close()
        } catch (_: IOException) {
        }
    }
}
