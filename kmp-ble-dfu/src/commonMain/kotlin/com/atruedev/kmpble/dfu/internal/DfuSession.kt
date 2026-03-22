package com.atruedev.kmpble.dfu.internal

import com.atruedev.kmpble.dfu.DfuError
import com.atruedev.kmpble.dfu.DfuOptions
import com.atruedev.kmpble.dfu.DfuProgress
import com.atruedev.kmpble.dfu.firmware.FirmwarePackage
import com.atruedev.kmpble.dfu.protocol.DfuProtocol
import com.atruedev.kmpble.dfu.transport.DfuTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.selects.select

internal class DfuSession(
    private val transport: DfuTransport,
    private val protocol: DfuProtocol,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
) {
    private val abortSignal = CompletableDeferred<Unit>()

    fun start(firmware: FirmwarePackage, options: DfuOptions): Flow<DfuProgress> {
        val abortAwareTransport = AbortAwareDfuTransport(transport, abortSignal)
        return protocol.performDfu(abortAwareTransport, firmware, options)
            .catch { e ->
                when (e) {
                    is CancellationException -> emit(DfuProgress.Aborted)
                    is DfuError.Aborted -> emit(DfuProgress.Aborted)
                    is DfuError -> emit(DfuProgress.Failed(e))
                    else -> emit(DfuProgress.Failed(DfuError.TransferFailed(e.message ?: "Unknown error", e)))
                }
            }
            .onCompletion { transport.close() }
            .flowOn(dispatcher)
    }

    fun abort() {
        abortSignal.complete(Unit)
    }
}

private class AbortAwareDfuTransport(
    private val delegate: DfuTransport,
    private val abortSignal: CompletableDeferred<Unit>,
) : DfuTransport {

    override val mtu: Int get() = delegate.mtu
    override val notifications: Flow<ByteArray> get() = delegate.notifications

    override suspend fun sendCommand(data: ByteArray): ByteArray {
        checkAborted()
        return coroutineScope {
            val commandDeferred = async { delegate.sendCommand(data) }
            select {
                commandDeferred.onAwait { it }
                abortSignal.onAwait {
                    commandDeferred.cancel()
                    throw DfuError.Aborted()
                }
            }
        }
    }

    override suspend fun sendData(data: ByteArray) {
        checkAborted()
        delegate.sendData(data)
    }

    override fun close() = delegate.close()

    private fun checkAborted() {
        if (abortSignal.isCompleted) throw DfuError.Aborted()
    }
}
