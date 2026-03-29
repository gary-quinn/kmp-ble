package com.atruedev.kmpble.sample.scanner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.atruedev.kmpble.adapter.BluetoothAdapterState
import com.atruedev.kmpble.sample.permission.PermissionResult
import com.atruedev.kmpble.sample.permission.rememberBlePermissionLauncher
import com.atruedev.kmpble.scanner.Advertisement
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    onDeviceSelected: (Advertisement) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    val requestPermission =
        rememberBlePermissionLauncher { result ->
            if (result is PermissionResult.Granted) {
                viewModel.onPermissionGranted()
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BLE Toolkit") },
                actions = {
                    SortModeSelector(
                        currentMode = state.sortMode,
                        onModeSelected = viewModel::setSortMode,
                    )
                    IconButton(onClick = viewModel::toggleFilterBar) {
                        Text(
                            "⊞",
                            style = MaterialTheme.typography.titleMedium,
                            color =
                                if (state.isFilterBarVisible) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                    ScanToggleButton(
                        scanState = state.scanState,
                        adapterState = state.adapterState,
                        onToggle = viewModel::toggleScan,
                    )
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            if (state.scanState == ScanState.Scanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            AnimatedVisibility(
                visible = state.isFilterBarVisible,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                FilterBar(
                    filters = state.filters,
                    onFiltersChanged = viewModel::updateFilters,
                )
            }

            when {
                state.adapterState == BluetoothAdapterState.Unavailable -> InitializingContent()
                state.adapterState == BluetoothAdapterState.Off -> BluetoothOffContent()
                state.adapterState == BluetoothAdapterState.Unauthorized ->
                    PermissionDeniedContent(
                        onRequestPermission = requestPermission,
                    )
                state.adapterState == BluetoothAdapterState.Unsupported -> UnsupportedContent()
                state.scanState is ScanState.Error ->
                    ErrorContent(
                        message = (state.scanState as ScanState.Error).message,
                        onRetry = viewModel::startScan,
                    )
                state.advertisements.isEmpty() && state.scanState == ScanState.Scanning -> EmptyScanningContent()
                state.advertisements.isEmpty() && state.scanState == ScanState.Idle ->
                    EmptyIdleContent(
                        onStartScan = viewModel::startScan,
                    )
                else ->
                    DeviceList(
                        devices = state.advertisements,
                        onDeviceClick = { onDeviceSelected(it.advertisement) },
                    )
            }
        }
    }
}
