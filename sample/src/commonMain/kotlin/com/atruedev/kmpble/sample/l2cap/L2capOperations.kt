package com.atruedev.kmpble.sample.l2cap

import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.peripheral.Peripheral
import com.atruedev.kmpble.sample.detail.DeviceDetailUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

internal class L2capOperations(
    private val peripheral: Peripheral,
    private val _uiState: MutableStateFlow<DeviceDetailUiState>,
    private val scope: CoroutineScope,
) {
    private var channel: L2capChannel? = null
    private var receiveJob: Job? = null

    private companion object {
        const val MAX_MESSAGES = 100
    }

    fun openChannel(psm: Int) {
        _uiState.update { it.copy(l2capState = L2capState.Opening) }

        scope.launch {
            try {
                val ch = peripheral.openL2capChannel(psm)
                channel = ch
                _uiState.update { it.copy(l2capState = L2capState.Open(psm)) }
                startReceiving(ch)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(l2capState = L2capState.Error("Open failed: ${e.message}")) }
            }
        }
    }

    private fun startReceiving(ch: L2capChannel) {
        receiveJob?.cancel()
        receiveJob =
            scope.launch {
                try {
                    ch.incoming.collect { data ->
                        appendMessage(L2capMessage.Direction.RECEIVED, data)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _uiState.update { it.copy(l2capState = L2capState.Closed) }
                }
            }
    }

    fun send(data: ByteArray) {
        val ch = channel ?: return
        scope.launch {
            try {
                ch.write(data)
                appendMessage(L2capMessage.Direction.SENT, data)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(l2capState = L2capState.Error("Send failed: ${e.message}")) }
            }
        }
    }

    fun closeChannel() {
        receiveJob?.cancel()
        receiveJob = null
        channel?.close()
        channel = null
        _uiState.update { it.copy(l2capState = L2capState.Closed) }
    }

    private fun appendMessage(
        direction: L2capMessage.Direction,
        data: ByteArray,
    ) {
        _uiState.update { state ->
            val messages = state.l2capMessages.toMutableList()
            messages.add(L2capMessage(direction, data, Clock.System.now()))
            if (messages.size > MAX_MESSAGES) {
                messages.removeAt(0)
            }
            state.copy(l2capMessages = messages)
        }
    }
}
