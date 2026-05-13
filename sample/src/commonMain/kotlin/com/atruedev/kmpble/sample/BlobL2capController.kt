package com.atruedev.kmpble.sample

import com.atruedev.kmpble.codec.LengthPrefixFramer
import com.atruedev.kmpble.codec.unframedBy
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Receive-side controller for the L2CAP blob-stream demo.
 *
 * The producer in [ServerViewModel.openBlobServer] streams a configurable
 * total payload as length-prefix-framed [BlobChunk] messages. This controller
 * splits the pipeline into three observable layers so the round-trip is
 * visible end-to-end:
 *
 * 1. **L2CAP SDU MTU** = [L2capChannel.mtu]. Negotiated by the controllers
 *    (LE Credit-Based Connection Request, see Core 5.x Vol 3 Part A); the
 *    spec ceiling is 65535 octets, real phones often 672 to a few KiB.
 * 2. **OS read chunk size** = the size of each [ByteArray] emitted from
 *    [L2capChannel.incoming] before framing. Driven by the platform's
 *    socket read buffer (e.g. Android's 4 KiB read buffer in
 *    `AndroidL2capChannel`); does not preserve SDU boundaries.
 * 3. **App frame size** = the size of each decoded [BlobChunk.bytes]
 *    payload, equal to the producer's `frameBytes` setting.
 */
class BlobL2capController(
    private val peripheral: Peripheral,
    private val scope: CoroutineScope,
) {
    data class Stats(
        val isOpen: Boolean = false,
        val mtu: Int = 0,
        val expectedBytes: Long = 0,
        val receivedBytes: Long = 0,
        val frameCount: Int = 0,
        val frameBytesMin: Int = 0,
        val frameBytesMax: Int = 0,
        val osChunkCount: Int = 0,
        val osChunkBytesMin: Int = 0,
        val osChunkBytesMax: Int = 0,
        val osChunkBytesTotal: Long = 0,
        val elapsedMs: Long = 0,
        val done: Boolean = false,
    ) {
        val progressFraction: Float
            get() = if (expectedBytes > 0) (receivedBytes.toFloat() / expectedBytes.toFloat()).coerceIn(0f, 1f) else 0f

        val throughputBytesPerSec: Double
            get() = if (elapsedMs > 0) receivedBytes * 1000.0 / elapsedMs else 0.0

        val osChunkBytesAvg: Double
            get() = if (osChunkCount > 0) osChunkBytesTotal.toDouble() / osChunkCount else 0.0
    }

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    private val _log = MutableStateFlow(emptyList<String>())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private var openJob: Job? = null
    private var channel: L2capChannel? = null

    fun open(
        psm: Int,
        secure: Boolean = true,
    ) {
        openJob?.cancel()
        openJob =
            scope.launch {
                try {
                    val ch = peripheral.openL2capChannel(psm, secure)
                    channel = ch
                    _stats.value = Stats(isOpen = true, mtu = ch.mtu)
                    appendLog("Opened (PSM=$psm, mtu=${ch.mtu})")
                    consume(ch)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    appendLog("Open failed: ${e.message}")
                    _stats.update { it.copy(isOpen = false) }
                }
            }
    }

    fun close() {
        openJob?.cancel()
        openJob = null
        channel?.close()
        channel = null
        _stats.update { it.copy(isOpen = false) }
        appendLog("Closed")
    }

    private suspend fun consume(ch: L2capChannel) {
        val framer = LengthPrefixFramer()
        val mark: TimeMark = TimeSource.Monotonic.markNow()
        try {
            ch.incoming
                .onEach { osChunk -> recordOsChunk(osChunk.size, mark) }
                .unframedBy(framer)
                .onEach { frame -> recordFrame(frame.size) }
                .map { BlobChunkCodec.decode(it) }
                .collect { chunk -> recordChunk(chunk, mark) }
            appendLog("Stream ended")
            _stats.update { it.copy(isOpen = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            appendLog("Stream error: ${e.message}")
            _stats.update { it.copy(isOpen = false) }
        }
    }

    private fun recordOsChunk(
        size: Int,
        mark: TimeMark,
    ) {
        _stats.update { s ->
            val min = if (s.osChunkCount == 0) size else minOf(s.osChunkBytesMin, size)
            val max = maxOf(s.osChunkBytesMax, size)
            s.copy(
                osChunkCount = s.osChunkCount + 1,
                osChunkBytesMin = min,
                osChunkBytesMax = max,
                osChunkBytesTotal = s.osChunkBytesTotal + size,
                elapsedMs = mark.elapsedNow().inWholeMilliseconds,
            )
        }
    }

    private fun recordFrame(size: Int) {
        _stats.update { s ->
            val min = if (s.frameCount == 0) size else minOf(s.frameBytesMin, size)
            val max = maxOf(s.frameBytesMax, size)
            s.copy(
                frameBytesMin = min,
                frameBytesMax = max,
            )
        }
    }

    private fun recordChunk(
        chunk: BlobChunk,
        mark: TimeMark,
    ) {
        _stats.update { s ->
            s.copy(
                expectedBytes = if (s.expectedBytes == 0L) chunk.totalBytes else s.expectedBytes,
                receivedBytes = s.receivedBytes + chunk.bytes.size,
                frameCount = s.frameCount + 1,
                done = chunk.eof,
                elapsedMs = mark.elapsedNow().inWholeMilliseconds,
            )
        }
        if (chunk.eof) {
            appendLog("EOF @ seq=${chunk.seq}; ${_stats.value.receivedBytes}B in ${_stats.value.elapsedMs}ms")
        }
    }

    private fun appendLog(msg: String) {
        _log.update { (listOf(msg) + it).take(20) }
    }
}
