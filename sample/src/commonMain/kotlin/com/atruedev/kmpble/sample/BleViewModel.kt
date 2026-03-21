package com.atruedev.kmpble.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.benchmark.LatencyTracker
import com.atruedev.kmpble.benchmark.ThroughputMeter
import com.atruedev.kmpble.benchmark.bleStopwatch
import com.atruedev.kmpble.bonding.BondRemovalResult
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.peripheral.Peripheral
import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.peripheral.toPeripheral
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Lifecycle-scoped peripheral management.
 *
 * [Peripheral] is created once from the [Advertisement]; all BLE operations
 * are scoped to [viewModelScope]. [onCleared] calls [Peripheral.close] which
 * cancels coroutines and disconnects — without this, GATT connections leak.
 */
class BleViewModel(advertisement: Advertisement) : ViewModel() {

    private val peripheral: Peripheral = advertisement.toPeripheral()

    val connectionState: StateFlow<State> = peripheral.state
    val bondState = peripheral.bondState
    val services = peripheral.services
    val maximumWriteValueLength = peripheral.maximumWriteValueLength

    private val _rssi = MutableStateFlow<Int?>(null)
    val rssi: StateFlow<Int?> = _rssi.asStateFlow()

    private val _mtu = MutableStateFlow(23)
    val mtu: StateFlow<Int> = _mtu.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val pairing = PairingCoordinator()

    private val _benchmarkResult = MutableStateFlow<String?>(null)
    val benchmarkResult: StateFlow<String?> = _benchmarkResult.asStateFlow()

    @OptIn(ExperimentalBleApi::class)
    fun benchmarkConnect(options: ConnectionOptions = ConnectionOptions()) {
        viewModelScope.launch {
            try {
                _benchmarkResult.value = "Benchmarking connect..."
                peripheral.disconnect()
                val result = bleStopwatch("connect") {
                    peripheral.connect(options.copy(pairingHandler = pairing.handler))
                }
                _benchmarkResult.value = "Connect: ${result.duration}"
            } catch (e: Exception) {
                _benchmarkResult.value = "Error: ${formatError(e)}"
            }
        }
    }

    @OptIn(ExperimentalBleApi::class)
    fun benchmarkReads(characteristic: Characteristic, count: Int = 10) {
        viewModelScope.launch {
            try {
                _benchmarkResult.value = "Reading $count times..."
                val meter = ThroughputMeter()
                val latency = LatencyTracker()
                meter.start()
                repeat(count) {
                    latency.measure {
                        val data = peripheral.read(characteristic)
                        meter.record(data.size)
                    }
                }
                val throughput = meter.stop("reads")
                val stats = latency.summarize("read latency")
                _benchmarkResult.value = "$throughput\n$stats"
            } catch (e: Exception) {
                _benchmarkResult.value = "Error: ${formatError(e)}"
            }
        }
    }

    fun connect(options: ConnectionOptions = ConnectionOptions()) {
        launchWithErrorHandling {
            _error.value = null
            peripheral.connect(options.copy(pairingHandler = pairing.handler))
        }
    }

    fun disconnect() {
        launchWithErrorHandling { peripheral.disconnect() }
    }

    fun readCharacteristic(characteristic: Characteristic, onResult: (Result<ByteArray>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { peripheral.read(characteristic) })
        }
    }

    fun writeCharacteristic(
        characteristic: Characteristic,
        data: ByteArray,
        writeType: WriteType = WriteType.WithResponse,
    ) {
        launchWithErrorHandling { peripheral.write(characteristic, data, writeType) }
    }

    fun observe(characteristic: Characteristic): Flow<Observation> =
        peripheral.observe(characteristic, BackpressureStrategy.Latest)

    fun readRssi() {
        launchWithErrorHandling { _rssi.value = peripheral.readRssi() }
    }

    fun requestMtu(mtu: Int) {
        launchWithErrorHandling { _mtu.value = peripheral.requestMtu(mtu) }
    }

    @OptIn(ExperimentalBleApi::class)
    fun removeBond() {
        val result = peripheral.removeBond()
        _error.value = when (result) {
            is BondRemovalResult.Success -> null
            is BondRemovalResult.NotSupported -> result.message
            is BondRemovalResult.Failed -> result.reason
        }
    }

    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        peripheral.close()
    }

    private fun launchWithErrorHandling(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _error.value = formatError(e)
            }
        }
    }

    private fun formatError(e: Exception): String = e.message ?: "Unknown error"
}
