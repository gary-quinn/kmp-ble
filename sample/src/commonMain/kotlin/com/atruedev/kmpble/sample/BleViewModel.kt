package com.atruedev.kmpble.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.benchmark.LatencyTracker
import com.atruedev.kmpble.benchmark.ThroughputMeter
import com.atruedev.kmpble.benchmark.bleStopwatch
import com.atruedev.kmpble.bonding.BondRemovalResult
import com.atruedev.kmpble.codec.BleDecoder
import com.atruedev.kmpble.codec.read
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.dfu.DfuController
import com.atruedev.kmpble.dfu.DfuOptions
import com.atruedev.kmpble.dfu.DfuProgress
import com.atruedev.kmpble.dfu.firmware.FirmwarePackage
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.peripheral.Peripheral
import com.atruedev.kmpble.peripheral.dump
import com.atruedev.kmpble.peripheral.toPeripheral
import com.atruedev.kmpble.profiles.battery.batteryLevelNotifications
import com.atruedev.kmpble.profiles.battery.readBatteryLevel
import com.atruedev.kmpble.profiles.deviceinfo.DeviceInformation
import com.atruedev.kmpble.profiles.deviceinfo.readDeviceInformation
import com.atruedev.kmpble.profiles.heartrate.HeartRateMeasurement
import com.atruedev.kmpble.profiles.heartrate.heartRateMeasurements
import com.atruedev.kmpble.profiles.heartrate.readBodySensorLocation
import com.atruedev.kmpble.scanner.Advertisement
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

/**
 * Lifecycle-scoped peripheral management.
 *
 * [Peripheral] is created once from the [Advertisement]; all BLE operations
 * are scoped to [viewModelScope]. [onCleared] calls [Peripheral.close] which
 * cancels coroutines and disconnects — without this, GATT connections leak.
 *
 * NOTE: This is a sample-app convenience — a single ViewModel for all screens
 * sharing one Peripheral. In production, split by concern (e.g., separate DFU
 * orchestration, profile readers) to avoid accumulating responsibilities.
 */
class BleViewModel(
    advertisement: Advertisement,
) : ViewModel() {
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
    val l2cap = L2capController(peripheral, viewModelScope)

    private val _benchmarkResult = MutableStateFlow<String?>(null)
    val benchmarkResult: StateFlow<String?> = _benchmarkResult.asStateFlow()

    // -- Profile operations (scoped to viewModelScope, no Peripheral leakage) --

    fun heartRateMeasurements(): Flow<HeartRateMeasurement> = peripheral.heartRateMeasurements()

    suspend fun readBodySensorLocation() = peripheral.readBodySensorLocation()

    suspend fun readBatteryLevel() = peripheral.readBatteryLevel()

    fun batteryLevelNotifications(): Flow<Int> = peripheral.batteryLevelNotifications()

    suspend fun readDeviceInformation(): DeviceInformation = peripheral.readDeviceInformation()

    // -- Codec operations --

    fun findCharacteristic(
        serviceUuid: Uuid,
        charUuid: Uuid,
    ): Characteristic? = peripheral.findCharacteristic(serviceUuid, charUuid)

    suspend fun <T> readTyped(
        characteristic: Characteristic,
        decoder: BleDecoder<T>,
    ): T = peripheral.read(characteristic, decoder)

    // -- DFU (lifecycle-scoped) --

    private val _dfuProgress = MutableStateFlow<DfuProgress?>(null)
    val dfuProgress: StateFlow<DfuProgress?> = _dfuProgress.asStateFlow()

    private var dfuController: DfuController? = null
    private var dfuJob: Job? = null

    fun startDfu(firmwareZip: ByteArray) {
        dfuJob?.cancel()
        dfuJob =
            viewModelScope.launch {
                try {
                    val controller = DfuController(peripheral)
                    dfuController = controller
                    val firmware = FirmwarePackage.Nordic.fromZipBytes(firmwareZip)
                    controller.performDfu(firmware, DfuOptions()).collect { progress ->
                        _dfuProgress.value = progress
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _error.value = formatError(e)
                } finally {
                    dfuController = null
                }
            }
    }

    fun abortDfu() {
        dfuController?.abort()
    }

    // -- Core GATT operations --

    fun dump(): String = peripheral.dump()

    fun observeValues(
        characteristic: Characteristic,
        backpressure: BackpressureStrategy = BackpressureStrategy.Latest,
    ): Flow<ByteArray> = peripheral.observeValues(characteristic, backpressure)

    @OptIn(ExperimentalBleApi::class)
    fun benchmarkConnect(options: ConnectionOptions = ConnectionOptions()) {
        viewModelScope.launch {
            try {
                _benchmarkResult.value = "Benchmarking connect..."
                peripheral.disconnect()
                val result =
                    bleStopwatch("connect") {
                        peripheral.connect(options.copy(pairingHandler = pairing.handler))
                    }
                _benchmarkResult.value = "Connect: ${result.duration}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _benchmarkResult.value = "Error: ${formatError(e)}"
            }
        }
    }

    @OptIn(ExperimentalBleApi::class)
    fun benchmarkReads(
        characteristic: Characteristic,
        count: Int = 10,
    ) {
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _benchmarkResult.value = "Error: ${formatError(e)}"
            }
        }
    }

    fun connect(options: ConnectionOptions = ConnectionOptions()) {
        launchWithErrorHandling {
            _error.value = null
            if (peripheral.state.value is State.Disconnecting) {
                peripheral.disconnect()
            }
            peripheral.connect(options.copy(pairingHandler = pairing.handler))
        }
    }

    fun disconnect() {
        launchWithErrorHandling { peripheral.disconnect() }
    }

    fun readCharacteristic(
        characteristic: Characteristic,
        onResult: (Result<ByteArray>) -> Unit,
    ) {
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
        _error.value =
            when (result) {
                is BondRemovalResult.Success -> null
                is BondRemovalResult.NotSupported -> result.message
                is BondRemovalResult.Failed -> result.reason
            }
    }

    fun clearError() {
        _error.value = null
    }

    fun releaseConnection() {
        dfuJob?.cancel()
        dfuController = null
        l2cap.close()
        peripheral.close()
    }

    override fun onCleared() {
        releaseConnection()
    }

    // Sample convenience — in production, prefer named ViewModel methods over exposing
    // a generic launcher, which bypasses operation serialization.
    fun launchWithErrorHandling(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = formatError(e)
            }
        }
    }

    private fun formatError(e: Exception): String = e.message ?: "Unknown error"
}
