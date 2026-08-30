package com.atruedev.kmpble.l2cap

import com.atruedev.kmpble.l2cap.internal.AbstractL2capChannel
import com.atruedev.kmpble.l2cap.internal.L2capRecoveryContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Android implementation of [L2capChannel] backed by an [L2capSocket].
 */
internal class AndroidL2capChannel(
    private val socket: L2capSocket,
    psm: Int,
    private val scope: CoroutineScope,
    recovery: L2capRecoveryContext? = null,
    mtuOverride: Int? = null,
) : AbstractL2capChannel(
        psm = psm,
        mtu = mtuOverride ?: resolveMtu(socket),
        recovery = recovery,
    ) {
    private val inputStream: InputStream = socket.inputStream
    private val outputStream: OutputStream = socket.outputStream

    init {
        markOpen()
    }

    private val readJob: Job =
        scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(mtu.coerceAtLeast(READ_BUFFER_SIZE))

            try {
                while (isActive && isOpen) {
                    try {
                        val bytesRead = inputStream.read(buffer)

                        if (bytesRead == -1) {
                            break
                        }

                        if (bytesRead > 0) {
                            deliverIncoming(buffer.copyOf(bytesRead))
                        }
                    } catch (_: IOException) {
                        break
                    }
                }
            } finally {
                if (isOpen && isActive) {
                    failWithSync(
                        L2capChannelError.RemoteDisconnected(
                            psm = psm,
                            state = L2capChannelState.Open,
                        ),
                    )
                }
            }
        }

    override suspend fun write(data: ByteArray) {
        if (!isOpen) {
            throw L2capException.ChannelClosed()
        }

        if (!socket.isConnected) {
            throw L2capException.ChannelClosed("Socket is not connected")
        }

        withContext(Dispatchers.IO) {
            try {
                outputStream.write(data)
                outputStream.flush()
            } catch (e: IOException) {
                throw L2capException.WriteFailed(e.message ?: "Write failed", e)
            }
        }
    }

    override fun cancelReadJob() {
        readJob.cancel()
    }

    override fun flushPendingWrites() {
        try {
            outputStream.flush()
        } catch (_: IOException) {
        }
    }

    override fun tearDownTransport() {
        try {
            inputStream.close()
        } catch (_: IOException) {
        }

        try {
            outputStream.close()
        } catch (_: IOException) {
        }

        try {
            socket.close()
        } catch (_: IOException) {
        }
    }

    internal companion object {
        const val DEFAULT_MTU = 672
        const val READ_BUFFER_SIZE = 4096

        fun resolveMtu(socket: L2capSocket): Int =
            try {
                maxOf(socket.maxTransmitPacketSize, DEFAULT_MTU)
            } catch (_: Exception) {
                DEFAULT_MTU
            }
    }
}
