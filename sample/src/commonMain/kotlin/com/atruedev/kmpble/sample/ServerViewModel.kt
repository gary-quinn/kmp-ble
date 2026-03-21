package com.atruedev.kmpble.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.scanner.uuidFrom
import com.atruedev.kmpble.server.AdvertiseConfig
import com.atruedev.kmpble.server.AdvertiseInterval
import com.atruedev.kmpble.server.Advertiser
import com.atruedev.kmpble.server.ExtendedAdvertiseConfig
import com.atruedev.kmpble.server.ExtendedAdvertiser
import com.atruedev.kmpble.server.GattServer
import com.atruedev.kmpble.server.ServerConnectionEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val HEART_RATE_SERVICE = uuidFrom("180D")
private val HEART_RATE_MEASUREMENT = uuidFrom("2A37")

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

    val server: GattServer = GattServer {
        service(HEART_RATE_SERVICE) {
            characteristic(HEART_RATE_MEASUREMENT) {
                properties { read = true; notify = true }
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
        viewModelScope.launch {
            try {
                server.open()
                _serverOpen.value = true
                collectConnectionEvents()
            } catch (e: Exception) {
                _error.value = "Open failed: ${e.message}"
            }
        }
    }

    fun closeServer() {
        server.close()
        _serverOpen.value = false
    }

    fun randomizeHeartRate() {
        _heartRate.value = (60..180).random()
    }

    fun notifyAll() {
        viewModelScope.launch {
            try {
                server.notify(
                    HEART_RATE_MEASUREMENT,
                    null,
                    BleData(byteArrayOf(0x00, _heartRate.value.toByte())),
                )
            } catch (e: Exception) {
                _error.value = "Notify failed: ${e.message}"
            }
        }
    }

    fun stopLegacyAdvertising() {
        advertiser.stopAdvertising()
    }

    fun startLegacyAdvertising() {
        try {
            advertiser.startAdvertising(
                AdvertiseConfig(
                    name = "kmp-ble Sample",
                    serviceUuids = listOf(HEART_RATE_SERVICE),
                    connectable = true,
                ),
            )
        } catch (e: Exception) {
            _error.value = "Start failed: ${e.message}"
        }
    }

    fun startExtendedSet(primary: Phy, secondary: Phy, name: String, interval: AdvertiseInterval) {
        viewModelScope.launch {
            try {
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
            } catch (e: Exception) {
                _error.value = "Start failed: ${e.message}"
            }
        }
    }

    fun stopExtendedSet(setId: Int) {
        viewModelScope.launch { extAdvertiser.stopAdvertisingSet(setId) }
    }

    fun stopAllExtendedSets() {
        viewModelScope.launch {
            for (setId in extAdvertiser.activeSets.value) {
                extAdvertiser.stopAdvertisingSet(setId)
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    private var connectionEventJob: Job? = null

    private fun collectConnectionEvents() {
        connectionEventJob?.cancel()
        connectionEventJob = viewModelScope.launch {
            server.connectionEvents.collect { event ->
                val msg = when (event) {
                    is ServerConnectionEvent.Connected -> "Connected: ${event.device.value.take(8)}"
                    is ServerConnectionEvent.Disconnected -> "Disconnected: ${event.device.value.take(8)}"
                }
                _connectionLog.value = (listOf(msg) + _connectionLog.value).take(20)
            }
        }
    }

    override fun onCleared() {
        advertiser.close()
        extAdvertiser.close()
        server.close()
    }
}
