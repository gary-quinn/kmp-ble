package com.atruedev.kmpble.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.codec.TypedL2capChannel
import com.atruedev.kmpble.codec.framedConnections
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.l2cap.L2capListener
import com.atruedev.kmpble.scanner.uuidFrom
import com.atruedev.kmpble.server.AdvertiseConfig
import com.atruedev.kmpble.server.AdvertiseInterval
import com.atruedev.kmpble.server.Advertiser
import com.atruedev.kmpble.server.ExtendedAdvertiseConfig
import com.atruedev.kmpble.server.ExtendedAdvertiser
import com.atruedev.kmpble.server.GattServer
import com.atruedev.kmpble.server.ServerConnectionEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.TimeSource

private val HEART_RATE_SERVICE = uuidFrom("180D")
private val HEART_RATE_MEASUREMENT = uuidFrom("2A37")
private const val STREAM_INTERVAL_MS = 100L

private const val BLOB_DEFAULT_TOTAL_BYTES: Long = 5L * 1024 * 1024
private const val BLOB_DEFAULT_FRAME_BYTES: Int = 4 * 1024
const val BLOB_MAX_FRAME_BYTES: Int = 60 * 1024
val BLOB_FRAME_OPTIONS: List<Int> = listOf(1024, 4 * 1024, 16 * 1024, BLOB_MAX_FRAME_BYTES)
val BLOB_TOTAL_OPTIONS: List<Long> =
    listOf(1L * 1024 * 1024, 5L * 1024 * 1024, 10L * 1024 * 1024, 25L * 1024 * 1024)

data class BlobSendStats(
    val bytesSent: Long,
    val framesSent: Int,
    val totalBytes: Long,
    val mtu: Int,
    val elapsedMs: Long,
    val done: Boolean,
)

/**
 * Local name prefix used by every advertiser in this sample app so the
 * scanner can pin matching devices to the top of the list.
 */
internal const val SAMPLE_NAME_PREFIX = "kmp-ble"

@OptIn(ExperimentalBleApi::class)
class ServerViewModel : ViewModel() {
    private val _heartRate = MutableStateFlow(72)
    val heartRate: StateFlow<Int> = _heartRate.asStateFlow()

    private val _serverOpen = MutableStateFlow(false)
    val serverOpen: StateFlow<Boolean> = _serverOpen.asStateFlow()

    private val _connectionLog = MutableStateFlow(emptyList<String>())
    val connectionLog: StateFlow<List<String>> = _connectionLog.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val server =
        GattServer {
            service(HEART_RATE_SERVICE) {
                characteristic(HEART_RATE_MEASUREMENT) {
                    properties {
                        read = true
                        notify = true
                    }
                    permissions { read = true }
                    onRead { _ -> BleData(byteArrayOf(0x00, _heartRate.value.toByte())) }
                }
            }
        }

    private val advertiser = Advertiser()
    private val extAdvertiser = ExtendedAdvertiser()

    val isAdvertising: StateFlow<Boolean> = advertiser.isAdvertising
    val activeSets: StateFlow<Set<Int>> = extAdvertiser.activeSets

    fun openServer() {
        launchWithErrorHandling {
            // Close first in case a prior instance is still open at the stack level
            // (ViewModel may survive across navigations without onCleared)
            server.close()
            server.open()
            _serverOpen.value = true
            collectConnectionEvents()
        }
    }

    fun closeServer() {
        server.close()
        _serverOpen.value = false
    }

    fun randomizeHeartRate() {
        _heartRate.value = (60..180).random()
    }

    fun notifyClients() {
        launchWithErrorHandling {
            server.notify(
                HEART_RATE_MEASUREMENT,
                null,
                BleData(byteArrayOf(0x00, _heartRate.value.toByte())),
            )
        }
    }

    fun stopLegacyAdvertising() {
        launchWithErrorHandling { advertiser.stopAdvertising() }
    }

    fun startLegacyAdvertising() {
        launchWithErrorHandling {
            advertiser.startAdvertising(
                AdvertiseConfig(
                    name = "$SAMPLE_NAME_PREFIX Sample",
                    serviceUuids = listOf(HEART_RATE_SERVICE),
                    connectable = true,
                ),
            )
        }
    }

    fun startExtendedSet(
        primary: Phy,
        secondary: Phy,
        name: String,
        interval: AdvertiseInterval,
    ) {
        launchWithErrorHandling {
            extAdvertiser.startAdvertisingSet(
                ExtendedAdvertiseConfig(
                    name = name,
                    serviceUuids = listOf(HEART_RATE_SERVICE),
                    connectable = true,
                    primaryPhy = primary,
                    secondaryPhy = secondary,
                    interval = interval,
                ),
            )
        }
    }

    fun stopExtendedSet(setId: Int) {
        viewModelScope.launch { extAdvertiser.stopAdvertisingSet(setId) }
    }

    fun stopAllExtendedSets() {
        viewModelScope.launch {
            extAdvertiser.activeSets.value.forEach { setId ->
                extAdvertiser.stopAdvertisingSet(setId)
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    // --- L2CAP typed sensor stream ---
    //
    // On each accepted L2CAP channel, the server produces a SensorReading
    // every 100ms (CBOR-encoded, length-prefix framed) until the channel
    // closes. The matching consumer side is L2capController.readings.

    private val _l2capOpen = MutableStateFlow(false)
    val l2capOpen: StateFlow<Boolean> = _l2capOpen.asStateFlow()

    private val _l2capPsm = MutableStateFlow(0)
    val l2capPsm: StateFlow<Int> = _l2capPsm.asStateFlow()

    private val _l2capLog = MutableStateFlow(emptyList<String>())
    val l2capLog: StateFlow<List<String>> = _l2capLog.asStateFlow()

    private val _l2capStreamedCount = MutableStateFlow(0)
    val l2capStreamedCount: StateFlow<Int> = _l2capStreamedCount.asStateFlow()

    private var l2capListener: L2capListener? = null
    private var l2capAcceptJob: Job? = null
    private val _l2capChannels = MutableStateFlow<List<TypedL2capChannel<SensorReading>>>(emptyList())

    fun openL2capServer(secure: Boolean = true) {
        launchWithErrorHandling {
            l2capListener?.close()
            val listener = L2capListener()
            l2capListener = listener
            l2capAcceptJob?.cancel()
            l2capAcceptJob =
                viewModelScope.launch {
                    listener.framedConnections(SensorReadingCodec).collect { typed ->
                        launch { streamReadings(typed) }
                    }
                }
            listener.open(secure)
            _l2capPsm.value = listener.psm
            _l2capOpen.value = true
            appendL2capLog("Listener open (PSM=${listener.psm}, secure=$secure)")
        }
    }

    fun closeL2capServer() {
        l2capAcceptJob?.cancel()
        l2capAcceptJob = null
        l2capListener?.close()
        l2capListener = null
        _l2capChannels.value.forEach { it.close() }
        _l2capChannels.value = emptyList()
        _l2capOpen.value = false
        _l2capStreamedCount.value = 0
        appendL2capLog("Listener closed")
    }

    private suspend fun streamReadings(typed: TypedL2capChannel<SensorReading>) {
        _l2capChannels.update { it + typed }
        appendL2capLog("Channel accepted (mtu=${typed.mtu}); streaming SensorReading")
        val start = TimeSource.Monotonic.markNow()
        try {
            while (typed.isOpen) {
                val reading =
                    SensorReading(
                        timestampMs = start.elapsedNow().inWholeMilliseconds,
                        celsius = 20.0 + Random.nextDouble(-2.0, 2.0),
                    )
                try {
                    typed.write(reading)
                    _l2capStreamedCount.update { it + 1 }
                } catch (e: Exception) {
                    appendL2capLog("Stream write failed: ${e.message}")
                    break
                }
                delay(STREAM_INTERVAL_MS)
            }
        } finally {
            _l2capChannels.update { it - typed }
            appendL2capLog("Channel closed")
        }
    }

    private fun appendL2capLog(msg: String) {
        _l2capLog.update { (listOf(msg) + it).take(20) }
    }

    private var connectionEventJob: Job? = null

    // Called from openServer() which runs on Main via viewModelScope.launch.
    private fun collectConnectionEvents() {
        connectionEventJob?.cancel()
        connectionEventJob =
            viewModelScope.launch {
                server.connectionEvents.collect { event ->
                    val msg =
                        when (event) {
                            is ServerConnectionEvent.Connected -> "Connected: ${event.device.value.take(8)}"
                            is ServerConnectionEvent.Disconnected -> "Disconnected: ${event.device.value.take(8)}"
                        }
                    _connectionLog.value = (listOf(msg) + _connectionLog.value).take(20)
                }
            }
    }

    // --- L2CAP blob stream (large transfer demo) ---
    //
    // On each accepted L2CAP channel, the server pushes a configurable total
    // payload (default 5 MiB) as a sequence of length-prefix-framed BlobChunk
    // messages of [_blobFrameBytes] each. The receiver (BlobL2capController)
    // exposes the same stream split across three layers so the round-trip is
    // observable: app frame size (this chunk size), OS read chunk size
    // (channel.incoming ByteArray sizes), and L2CAP SDU MTU (channel.mtu).

    private val _blobOpen = MutableStateFlow(false)
    val blobOpen: StateFlow<Boolean> = _blobOpen.asStateFlow()

    private val _blobPsm = MutableStateFlow(0)
    val blobPsm: StateFlow<Int> = _blobPsm.asStateFlow()

    private val _blobLog = MutableStateFlow(emptyList<String>())
    val blobLog: StateFlow<List<String>> = _blobLog.asStateFlow()

    private val _blobTotalBytes = MutableStateFlow(BLOB_DEFAULT_TOTAL_BYTES)
    val blobTotalBytes: StateFlow<Long> = _blobTotalBytes.asStateFlow()

    private val _blobFrameBytes = MutableStateFlow(BLOB_DEFAULT_FRAME_BYTES)
    val blobFrameBytes: StateFlow<Int> = _blobFrameBytes.asStateFlow()

    private val _blobSendStats = MutableStateFlow<BlobSendStats?>(null)
    val blobSendStats: StateFlow<BlobSendStats?> = _blobSendStats.asStateFlow()

    private var blobListener: L2capListener? = null
    private var blobAcceptJob: Job? = null

    fun setBlobTotalBytes(bytes: Long) {
        _blobTotalBytes.value = bytes
    }

    fun setBlobFrameBytes(bytes: Int) {
        _blobFrameBytes.value = bytes.coerceIn(64, BLOB_MAX_FRAME_BYTES)
    }

    fun openBlobServer(secure: Boolean = true) {
        launchWithErrorHandling {
            blobListener?.close()
            val listener = L2capListener()
            blobListener = listener
            blobAcceptJob?.cancel()
            blobAcceptJob =
                viewModelScope.launch {
                    listener.framedConnections(BlobChunkCodec).collect { typed ->
                        launch { sendBlob(typed) }
                    }
                }
            listener.open(secure)
            _blobPsm.value = listener.psm
            _blobOpen.value = true
            appendBlobLog("Blob listener open (PSM=${listener.psm}, secure=$secure)")
        }
    }

    fun closeBlobServer() {
        blobAcceptJob?.cancel()
        blobAcceptJob = null
        blobListener?.close()
        blobListener = null
        _blobOpen.value = false
        _blobSendStats.value = null
        appendBlobLog("Blob listener closed")
    }

    private suspend fun sendBlob(typed: TypedL2capChannel<BlobChunk>) {
        val totalBytes = _blobTotalBytes.value
        val frameBytes = _blobFrameBytes.value
        val pattern = ByteArray(frameBytes) { (it and 0xFF).toByte() }
        val totalFrames = ((totalBytes + frameBytes - 1) / frameBytes).toInt()

        appendBlobLog(
            "Accepted (mtu=${typed.mtu}); sending ${totalBytes / 1024} KiB in " +
                "$totalFrames frames of ${frameBytes / 1024} KiB",
        )

        val mark = TimeSource.Monotonic.markNow()
        var bytesSent = 0L
        var framesSent = 0
        _blobSendStats.value = BlobSendStats(0, 0, totalBytes, typed.mtu, 0, false)

        try {
            while (typed.isOpen && bytesSent < totalBytes) {
                val remaining = (totalBytes - bytesSent).toInt().coerceAtMost(frameBytes)
                val chunk = if (remaining == frameBytes) pattern else pattern.copyOfRange(0, remaining)
                val eof = bytesSent + chunk.size >= totalBytes
                try {
                    typed.write(BlobChunk(framesSent, totalBytes, eof, chunk))
                } catch (e: Exception) {
                    appendBlobLog("Send failed @ seq=$framesSent: ${e.message}")
                    break
                }
                bytesSent += chunk.size
                framesSent++
                _blobSendStats.value =
                    BlobSendStats(
                        bytesSent = bytesSent,
                        framesSent = framesSent,
                        totalBytes = totalBytes,
                        mtu = typed.mtu,
                        elapsedMs = mark.elapsedNow().inWholeMilliseconds,
                        done = eof,
                    )
            }
            appendBlobLog(
                "Send done: ${bytesSent}B in $framesSent frames over " +
                    "${mark.elapsedNow().inWholeMilliseconds}ms",
            )
        } finally {
            typed.close()
        }
    }

    private fun appendBlobLog(msg: String) {
        _blobLog.update { (listOf(msg) + it).take(20) }
    }

    override fun onCleared() {
        closeL2capServer()
        closeBlobServer()
        advertiser.close()
        extAdvertiser.close()
        server.close()
    }

    private fun launchWithErrorHandling(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            }
        }
    }
}
