package com.atruedev.kmpble.sample.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atruedev.kmpble.adapter.BluetoothAdapter
import com.atruedev.kmpble.adapter.BluetoothAdapterState
import com.atruedev.kmpble.sample.registry.BluetoothUuidNames
import com.atruedev.kmpble.scanner.EmissionPolicy
import com.atruedev.kmpble.scanner.Scanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class ScannerViewModel : ViewModel() {
    private val serialScope =
        CoroutineScope(
            viewModelScope.coroutineContext +
                Dispatchers.Default.limitedParallelism(1) +
                SupervisorJob(viewModelScope.coroutineContext[Job]),
        )

    private var adapter = BluetoothAdapter()
    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private var scanner: Scanner? = null
    private var adapterObserveJob: Job? = null
    private val discoveredDevices = mutableMapOf<String, DiscoveredDevice>()

    init {
        observeAdapterState()
    }

    private fun observeAdapterState() {
        adapterObserveJob?.cancel()
        adapterObserveJob =
            serialScope.launch {
                adapter.state.collect { state ->
                    _uiState.update { it.copy(adapterState = state) }
                    if (state == BluetoothAdapterState.On && _uiState.value.scanState == ScanState.Idle) {
                        startScan()
                    }
                    if (state != BluetoothAdapterState.On) {
                        stopScan()
                    }
                }
            }
    }

    fun onPermissionGranted() {
        serialScope.launch {
            adapter.close()
            adapter = BluetoothAdapter()
            observeAdapterState()
        }
    }

    fun startScan() {
        if (scanJob?.isActive == true) return
        if (_uiState.value.adapterState != BluetoothAdapterState.On) return

        discoveredDevices.clear()
        _uiState.update { it.copy(scanState = ScanState.Scanning, advertisements = emptyList()) }

        scanner?.close()
        val newScanner =
            Scanner {
                timeout = 30.seconds
                emission = EmissionPolicy.FirstThenChanges(rssiThreshold = 5)
            }
        scanner = newScanner

        scanJob =
            serialScope.launch {
                try {
                    newScanner.advertisements.collect { advertisement ->
                        processAdvertisement(advertisement)
                    }
                    _uiState.update { it.copy(scanState = ScanState.Idle) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(scanState = ScanState.Error(e.message ?: "Scan failed")) }
                }
            }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        scanner?.close()
        scanner = null
        if (_uiState.value.scanState == ScanState.Scanning) {
            _uiState.update { it.copy(scanState = ScanState.Idle) }
        }
    }

    fun toggleScan() {
        if (_uiState.value.scanState == ScanState.Scanning) stopScan() else startScan()
    }

    private fun processAdvertisement(advertisement: com.atruedev.kmpble.scanner.Advertisement) {
        val id = advertisement.identifier.value
        val manufacturerName =
            advertisement.manufacturerData.keys.firstOrNull()?.let { companyId ->
                BluetoothUuidNames.companyName(companyId)
            }
        val category = DeviceCategoryClassifier.classify(advertisement.serviceUuids)

        val device =
            DiscoveredDevice(
                advertisement = advertisement,
                displayName = advertisement.name ?: "Unknown",
                rssi = advertisement.rssi,
                lastSeen = Clock.System.now(),
                serviceUuids = advertisement.serviceUuids,
                manufacturerName = manufacturerName,
                deviceCategory = category,
                identifier = id,
            )

        discoveredDevices[id] = device
        updateFilteredAndSortedList()
    }

    private fun updateFilteredAndSortedList() {
        val filters = _uiState.value.filters
        val sortMode = _uiState.value.sortMode

        val filtered =
            discoveredDevices.values
                .filter { device -> matchesFilters(device, filters) }

        val sorted =
            when (sortMode) {
                SortMode.RSSI -> filtered.sortedByDescending { it.rssi }
                SortMode.NAME -> filtered.sortedBy { it.displayName.lowercase() }
                SortMode.LAST_SEEN -> filtered.sortedByDescending { it.lastSeen }
            }

        _uiState.update { it.copy(advertisements = sorted) }
    }

    private fun matchesFilters(
        device: DiscoveredDevice,
        filters: ScanFilters,
    ): Boolean {
        if (filters.hideUnnamed && device.advertisement.name == null) return false
        if (device.rssi < filters.minRssi) return false
        if (filters.nameQuery.isNotBlank()) {
            val query = filters.nameQuery.lowercase()
            val nameMatch = device.displayName.lowercase().contains(query)
            val idMatch = device.identifier.lowercase().contains(query)
            if (!nameMatch && !idMatch) return false
        }
        if (filters.serviceUuidFilter != null && filters.serviceUuidFilter !in device.serviceUuids) return false
        return true
    }

    fun updateFilters(newFilters: ScanFilters) {
        _uiState.update { it.copy(filters = newFilters) }
        serialScope.launch { updateFilteredAndSortedList() }
    }

    fun setSortMode(mode: SortMode) {
        _uiState.update { it.copy(sortMode = mode) }
        serialScope.launch { updateFilteredAndSortedList() }
    }

    fun toggleFilterBar() {
        _uiState.update { it.copy(isFilterBarVisible = !it.isFilterBarVisible) }
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
        adapter.close()
    }
}
