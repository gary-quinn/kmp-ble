package com.atruedev.kmpble.sample

import com.atruedev.kmpble.codec.framedIncoming
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class L2capController(
    private val peripheral: Peripheral,
    private val scope: CoroutineScope,
) {
    private val _channel = MutableStateFlow<L2capChannel?>(null)
    val channel: StateFlow<L2capChannel?> = _channel.asStateFlow()

    private val _log = MutableStateFlow(emptyList<String>())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _readings = MutableStateFlow(emptyList<SensorReading>())
    val readings: StateFlow<List<SensorReading>> = _readings.asStateFlow()

    private var openJob: Job? = null
    private var incomingJob: Job? = null

    fun open(
        psm: Int,
        secure: Boolean = true,
    ) {
        openJob?.cancel()
        openJob =
            scope.launch {
                try {
                    val ch = peripheral.openL2capChannel(psm, secure)
                    _channel.value = ch
                    _readings.value = emptyList()
                    appendLog("Opened (PSM=$psm, MTU=${ch.mtu})")
                    incomingJob?.cancel()
                    incomingJob =
                        scope.launch {
                            ch
                                .framedIncoming(
                                    SensorReadingCodec,
                                    onDecodeFailure = { bytes ->
                                        appendLog("Decode failed (${bytes.size} bytes); dropped")
                                    },
                                ).collect { reading ->
                                    _readings.update { (it + reading).takeLast(READINGS_BUFFER) }
                                }
                            appendLog("Stream ended")
                        }
                } catch (e: Exception) {
                    appendLog("Open failed: ${e.message}")
                }
            }
    }

    fun write(data: ByteArray) {
        scope.launch {
            try {
                _channel.value?.write(data)
                appendLog("OUT: ${data.toHexString()}")
            } catch (e: Exception) {
                appendLog("Write failed: ${e.message}")
            }
        }
    }

    fun close() {
        openJob?.cancel()
        incomingJob?.cancel()
        _channel.value?.close()
        _channel.value = null
        appendLog("Closed")
    }

    private fun appendLog(msg: String) {
        _log.update { (listOf(msg) + it).take(50) }
    }

    private companion object {
        const val READINGS_BUFFER = 100
    }
}
