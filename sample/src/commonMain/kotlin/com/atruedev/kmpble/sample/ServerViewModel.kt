package com.atruedev.kmpble.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.codec.writeFramed
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.l2cap.L2capChannel
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
    private val _l2capChannels = MutableStateFlow<List<L2capChannel>>(emptyList())

    fun openL2capServer(secure: Boolean = true) {
        launchWithErrorHandling {
            l2capListener?.close()
            val listener = L2capListener()
            l2capListener = listener
            l2capAcceptJob?.cancel()
            l2capAcceptJob =
                viewModelScope.launch {
                    listener.incoming.collect { channel ->
                        launch { streamReadings(channel) }
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

    private suspend fun streamReadings(channel: L2capChannel) {
        _l2capChannels.update { it + channel }
        appendL2capLog("Channel accepted (mtu=${channel.mtu}); streaming SensorReading")
        val start = TimeSource.Monotonic.markNow()
        try {
            while (channel.isOpen) {
                val reading =
                    SensorReading(
                        timestampMs = start.elapsedNow().inWholeMilliseconds,
                        celsius = 20.0 + Random.nextDouble(-2.0, 2.0),
                    )
                try {
                    channel.writeFramed(reading, SensorReadingCodec)
                    _l2capStreamedCount.update { it + 1 }
                } catch (e: Exception) {
                    appendL2capLog("Stream write failed: ${e.message}")
                    break
                }
                delay(STREAM_INTERVAL_MS)
            }
        } finally {
            _l2capChannels.update { it - channel }
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

    override fun onCleared() {
        closeL2capServer()
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
