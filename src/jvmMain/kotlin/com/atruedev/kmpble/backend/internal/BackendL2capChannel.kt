package com.atruedev.kmpble.backend.internal

import com.atruedev.kmpble.backend.KmpBleBackendApi
import com.atruedev.kmpble.backend.L2capStream
import com.atruedev.kmpble.backend.L2capStreamListener
import com.atruedev.kmpble.l2cap.L2capChannelError
import com.atruedev.kmpble.l2cap.L2capException
import com.atruedev.kmpble.l2cap.internal.AbstractL2capChannel
import com.atruedev.kmpble.l2cap.internal.L2capRecoveryContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(KmpBleBackendApi::class)
internal class BackendL2capChannel(
    private val stream: L2capStream,
    mtu: Int,
    recovery: L2capRecoveryContext?,
) : AbstractL2capChannel(psm = stream.psm, mtu = mtu, recovery = recovery) {
    private sealed interface Inbound {
        class Data(
            val bytes: ByteArray,
        ) : Inbound

        class Closed(
            val reason: String?,
        ) : Inbound
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val inbound = Channel<Inbound>(Channel.UNLIMITED)
    private val closeActions = CopyOnWriteArrayList<() -> Unit>()
    private val readJob: Job

    init {
        stream.setListener(
            object : L2capStreamListener {
                override fun onData(data: ByteArray) {
                    inbound.trySend(Inbound.Data(data))
                }

                override fun onClosed(reason: String?) {
                    inbound.trySend(Inbound.Closed(reason))
                }
            },
        )
        markOpen()
        readJob =
            scope.launch {
                for (item in inbound) {
                    when (item) {
                        is Inbound.Data -> deliverIncoming(item.bytes)
                        is Inbound.Closed -> {
                            failWithSync(L2capChannelError.RemoteDisconnected(psm = psm, state = state.value))
                            return@launch
                        }
                    }
                }
            }
    }

    override suspend fun write(data: ByteArray) {
        if (!isOpen) throw L2capException.ChannelClosed()
        try {
            stream.write(data)
        } catch (e: L2capException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw L2capException.WriteFailed(e.message ?: "write failed", e)
        }
    }

    internal fun onLinkLost() {
        failWithSync(L2capChannelError.RemoteDisconnected(psm = psm, state = state.value))
    }

    internal fun onClosed(action: () -> Unit) {
        closeActions += action
    }

    override fun cancelReadJob() {
        readJob.cancel()
    }

    override fun tearDownTransport() {
        stream.setListener(null)
        runCatching { stream.close() }
        inbound.close()
        scope.cancel()
        closeActions.forEach { it() }
        closeActions.clear()
    }
}
