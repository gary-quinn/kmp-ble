package com.atruedev.kmpble.dfu.internal

import com.atruedev.kmpble.dfu.DfuError
import com.atruedev.kmpble.dfu.DfuOptions
import com.atruedev.kmpble.dfu.DfuProgress
import com.atruedev.kmpble.dfu.firmware.FirmwarePackage
import com.atruedev.kmpble.dfu.protocol.DfuProtocol
import com.atruedev.kmpble.dfu.transport.DfuTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

internal class DfuSession(
    private val transport: DfuTransport,
    private val protocol: DfuProtocol,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
) {
    private val aborted = MutableStateFlow(false)

    fun start(firmware: FirmwarePackage, options: DfuOptions): Flow<DfuProgress> =
        protocol.performDfu(transport, firmware, options)
            .onEach { if (aborted.value) throw DfuError.Aborted() }
            .catch { e ->
                when (e) {
                    is CancellationException -> emit(DfuProgress.Aborted)
                    is DfuError -> emit(DfuProgress.Failed(e))
                    else -> emit(DfuProgress.Failed(DfuError.TransferFailed(e.message ?: "Unknown error", e)))
                }
            }
            .onCompletion { transport.close() }
            .flowOn(dispatcher)

    fun abort() {
        aborted.value = true
    }
}
