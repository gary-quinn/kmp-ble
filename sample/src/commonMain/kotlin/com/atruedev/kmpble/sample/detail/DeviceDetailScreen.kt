package com.atruedev.kmpble.sample.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.sample.detail.bonding.BondingSection
import com.atruedev.kmpble.sample.dfu.DfuSection
import com.atruedev.kmpble.sample.l2cap.L2capSection
import com.atruedev.kmpble.sample.profiles.ProfileSection
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
fun DeviceDetailScreen(
    viewModel: DeviceDetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.connect()
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    if (state.writeDialogTarget != null) {
        WriteDialog(
            characteristicName = state.writeDialogTarget!!.displayName,
            onDismiss = viewModel::dismissWriteDialog,
            onWrite = { data, writeType ->
                val target = state.writeDialogTarget!!
                val serviceIndex =
                    state.services.indexOfFirst { svc ->
                        svc.characteristics.any { it.uuid == target.uuid }
                    }
                if (serviceIndex >= 0) {
                    val charIndex =
                        state.services[serviceIndex]
                            .characteristics
                            .indexOfFirst { it.uuid == target.uuid }
                    if (charIndex >= 0) {
                        viewModel.writeCharacteristic(serviceIndex, charIndex, data, writeType)
                    }
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.deviceName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            state.identifier,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.disconnect()
                        onBack()
                    }) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = { ConnectionStateIndicator(state.connectionState) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            ConnectionControlBar(
                connectionState = state.connectionState,
                rssi = state.rssi,
                mtu = state.mtu,
                bondState = state.bondState,
                onConnect = viewModel::connect,
                onDisconnect = viewModel::disconnect,
                onRequestMtu = { viewModel.requestMtu(512) },
            )

            when (state.connectionState) {
                is State.Connected -> ConnectedContent(state = state, viewModel = viewModel)
                is State.Disconnected -> DisconnectedContent(onConnect = viewModel::connect)
                else -> ConnectingContent()
            }
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun ConnectedContent(
    state: DeviceDetailUiState,
    viewModel: DeviceDetailViewModel,
) {
    BondingSection(
        bondState = state.bondState,
        selectedRecipe = state.selectedRecipe,
        onPair = viewModel::pair,
        onRemoveBond = viewModel::removeBond,
        onSelectRecipe = viewModel::selectRecipe,
        onConnectWithRecipe = viewModel::connectWithRecipe,
    )

    DfuSection(
        dfuState = state.dfuState,
        onFileSelected = viewModel::selectFirmware,
        onStartDfu = viewModel::startDfu,
        onCancelDfu = viewModel::cancelDfu,
        onReset = viewModel::resetDfu,
    )

    ProfileSection(
        profileState = state.profileState,
        onStartHeartRate = viewModel::startHeartRate,
        onStopProfile = viewModel::stopProfile,
        onReadBattery = viewModel::readBattery,
        onStartBatteryNotifications = viewModel::startBatteryNotifications,
        onReadDeviceInfo = viewModel::readDeviceInfo,
    )

    L2capSection(
        l2capState = state.l2capState,
        messages = state.l2capMessages,
        onOpenChannel = viewModel::openL2capChannel,
        onSend = viewModel::sendL2capData,
        onCloseChannel = viewModel::closeL2capChannel,
    )

    ServiceList(
        services = state.services,
        onToggleService = viewModel::toggleService,
        onToggleCharacteristic = viewModel::toggleCharacteristic,
        onRead = viewModel::readCharacteristic,
        onWrite = viewModel::showWriteDialog,
        onToggleNotify = viewModel::toggleNotifications,
        onFormatChange = viewModel::setDisplayFormat,
        onDismissError = viewModel::dismissCharacteristicError,
    )
}
