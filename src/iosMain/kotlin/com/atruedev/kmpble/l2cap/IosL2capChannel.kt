@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.atruedev.kmpble.l2cap

import com.atruedev.kmpble.l2cap.internal.AbstractL2capChannel
import com.atruedev.kmpble.l2cap.internal.L2capRecoveryContext
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.CoreBluetooth.CBL2CAPChannel
import platform.Foundation.NSStreamStatusAtEnd
import platform.Foundation.NSStreamStatusClosed
import platform.Foundation.NSStreamStatusError
import kotlin.coroutines.coroutineContext

internal const val DEFAULT_L2CAP_MTU = 2048

internal class IosL2capChannel(
    private val cbChannel: CBL2CAPChannel,
    private val scope: CoroutineScope,
    mtu: Int,
    recovery: L2capRecoveryContext? = null,
) : AbstractL2capChannel(
        psm = cbChannel.PSM.toInt(),
        mtu = mtu,
        recovery = recovery,
    ) {
    private val readJob: Job

    init {
        val inputStream = cbChannel.inputStream
        val outputStream = cbChannel.outputStream
        if (inputStream == null || outputStream == null) {
            readJob = Job().apply { complete() }
            failWithSync(
                L2capChannelError.ChannelOpenFailed(
                    psm = psm,
                    state = state.value,
                    reason = "CoreBluetooth returned a channel without input or output streams",
                ),
            )
        } else {
            inputStream.open()
            outputStream.open()
            readJob =
                scope.launch(Dispatchers.Default) {
                    readLoop()
                }
        }
    }

    private suspend fun readLoop() {
        markOpen()

        val inputStream =
            cbChannel.inputStream ?: run {
                failWithSync(
                    L2capChannelError.ChannelOpenFailed(
                        psm = psm,
                        state = state.value,
                        reason = "Input stream became unavailable after open",
                    ),
                )
                return
            }

        val bufferSize = READ_BUFFER_SIZE
        val buffer = ByteArray(bufferSize)
        var consecutiveIdlePolls = 0

        try {
            while (isOpen) {
                coroutineContext.ensureActive()
                val status = inputStream.streamStatus
                if (status == NSStreamStatusAtEnd || status == NSStreamStatusClosed || status == NSStreamStatusError) {
                    break
                }

                if (inputStream.hasBytesAvailable) {
                    buffer.usePinned { pinned ->
                        val bytesRead =
                            inputStream
                                .read(
                                    pinned.addressOf(0).reinterpret<UByteVar>(),
                                    bufferSize.toULong(),
                                ).toInt()

                        when {
                            bytesRead > 0 -> deliverIncoming(buffer.copyOf(bytesRead))
                            bytesRead < 0 -> return
                        }
                    }
                    consecutiveIdlePolls = 0
                } else {
                    consecutiveIdlePolls++
                    delay(currentPollInterval(consecutiveIdlePolls))
                }
            }
        } finally {
            if (isOpen && coroutineContext.isActive) {
                failWithSync(
                    L2capChannelError.RemoteDisconnected(
                        psm = psm,
                        state = state.value,
                    ),
                )
            }
        }
    }

    override suspend fun write(data: ByteArray) {
        if (!isOpen) {
            throw L2capException.ChannelClosed()
        }

        val outputStream =
            cbChannel.outputStream
                ?: throw L2capException.WriteFailed("Output stream not available")

        withContext(Dispatchers.Default) {
            data.usePinned { pinned ->
                var totalWritten = 0
                while (totalWritten < data.size) {
                    if (!isOpen) {
                        throw L2capException.ChannelClosed("Channel closed during write")
                    }

                    val written =
                        outputStream
                            .write(
                                pinned.addressOf(totalWritten).reinterpret<UByteVar>(),
                                (data.size - totalWritten).toULong(),
                            ).toInt()

                    if (written < 0) {
                        throw L2capException.WriteFailed(
                            outputStream.streamError?.localizedDescription ?: "Unknown write error",
                        )
                    }

                    totalWritten += written
                    if (written == 0 && totalWritten < data.size) {
                        delay(MIN_POLL_INTERVAL_MS)
                    }
                }
            }
        }
    }

    override fun cancelReadJob() {
        readJob.cancel()
    }

    override fun tearDownTransport() {
        cbChannel.inputStream?.close()
        cbChannel.outputStream?.close()
    }

    private companion object {
        const val READ_BUFFER_SIZE = 4096
        const val MIN_POLL_INTERVAL_MS = 10L
        const val MAX_POLL_INTERVAL_MS = 100L

        fun currentPollInterval(consecutiveIdlePolls: Int): Long {
            val interval = MIN_POLL_INTERVAL_MS shl consecutiveIdlePolls.coerceAtMost(3)
            return interval.coerceAtMost(MAX_POLL_INTERVAL_MS)
        }
    }
}
