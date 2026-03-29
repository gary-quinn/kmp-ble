package com.atruedev.kmpble.sample.scanner

import com.atruedev.kmpble.adapter.BluetoothAdapterState
import com.atruedev.kmpble.scanner.Advertisement
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

sealed interface ScanState {
    data object Idle : ScanState

    data object Scanning : ScanState

    data class Error(
        val message: String,
    ) : ScanState
}

enum class SortMode { RSSI, NAME, LAST_SEEN }

enum class DeviceCategory {
    HEART_RATE,
    BATTERY,
    BLOOD_PRESSURE,
    GLUCOSE,
    CYCLING,
    DEVICE_INFO,
    NORDIC_DK,
    UNKNOWN,
}

@OptIn(ExperimentalUuidApi::class)
data class ScanFilters(
    val nameQuery: String = "",
    val minRssi: Int = -100,
    val serviceUuidFilter: Uuid? = null,
    val hideUnnamed: Boolean = false,
)

@OptIn(ExperimentalUuidApi::class)
data class DiscoveredDevice(
    val advertisement: Advertisement,
    val displayName: String,
    val rssi: Int,
    val lastSeen: Instant,
    val serviceUuids: List<Uuid>,
    val manufacturerName: String?,
    val deviceCategory: DeviceCategory,
    val identifier: String,
)

@OptIn(ExperimentalUuidApi::class)
data class ScannerUiState(
    val adapterState: BluetoothAdapterState = BluetoothAdapterState.Unavailable,
    val scanState: ScanState = ScanState.Idle,
    val advertisements: List<DiscoveredDevice> = emptyList(),
    val filters: ScanFilters = ScanFilters(),
    val sortMode: SortMode = SortMode.RSSI,
    val isFilterBarVisible: Boolean = false,
)
