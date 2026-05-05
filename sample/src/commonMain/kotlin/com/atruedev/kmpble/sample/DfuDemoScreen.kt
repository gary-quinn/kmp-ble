package com.atruedev.kmpble.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.dfu.DfuError
import com.atruedev.kmpble.dfu.DfuProgress
import com.atruedev.kmpble.scanner.Advertisement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DfuDemoScreen(
    advertisement: Advertisement,
    onBack: () -> Unit,
) {
    val vm = viewModel(key = advertisement.identifier.value) { BleViewModel(advertisement) }
    val state by vm.connectionState.collectAsState()
    val dfuProgress by vm.dfuProgress.collectAsState()

    var selectedFirmware by remember { mutableStateOf<ByteArray?>(null) }
    var firmwareName by remember { mutableStateOf<String?>(null) }
    val filePicker =
        rememberFilePickerLauncher { name, bytes ->
            firmwareName = name
            selectedFirmware = bytes
        }

    val isRunning =
        dfuProgress.let {
            it is DfuProgress.Starting ||
                it is DfuProgress.Transferring ||
                it is DfuProgress.Verifying ||
                it is DfuProgress.Completing
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Firmware Update (DFU)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("<", style = MaterialTheme.typography.titleLarge)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Nordic Secure DFU v2", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Select a DFU .zip package to flash firmware over BLE. " +
                            "The device must expose the Nordic DFU service.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { filePicker() },
                        enabled = !isRunning,
                    ) {
                        Text(firmwareName ?: "Select Firmware .zip")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            DfuProgressSection(dfuProgress)

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { selectedFirmware?.let { vm.startDfu(it) } },
                    enabled = state is State.Connected && !isRunning && selectedFirmware != null,
                ) {
                    Text("Start DFU")
                }

                OutlinedButton(
                    onClick = { vm.abortDfu() },
                    enabled = isRunning,
                ) {
                    Text("Abort")
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Uses DfuController from kmp-ble-dfu module",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state !is State.Connected && state !is State.Connecting) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Connect to a Nordic DFU-capable device to perform firmware updates.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DfuProgressSection(progress: DfuProgress?) {
    if (progress == null) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Progress", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            when (progress) {
                is DfuProgress.Starting -> {
                    Text("Starting DFU...", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is DfuProgress.Transferring -> {
                    Text(
                        "Object ${progress.currentObject}/${progress.totalObjects} -- " +
                            "${formatBytes(progress.bytesSent)}/${formatBytes(progress.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${formatBytes(progress.bytesPerSecond)}/s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is DfuProgress.Verifying -> {
                    Text(
                        "Verifying object ${progress.objectIndex}...",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is DfuProgress.Completing -> {
                    Text("Completing...", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is DfuProgress.Completed -> {
                    Text(
                        "DFU completed successfully.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                is DfuProgress.Failed -> {
                    Text(
                        "DFU failed: ${dfuErrorMessage(progress.error)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                is DfuProgress.Aborted -> {
                    Text(
                        "DFU aborted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private fun dfuErrorMessage(error: DfuError): String =
    when (error) {
        is DfuError.NotConnected -> error.message ?: "Not connected"
        is DfuError.ServiceNotFound -> error.message ?: "Service not found"
        is DfuError.CharacteristicNotFound -> error.message ?: "Characteristic not found"
        is DfuError.ProtocolError -> error.message ?: "Protocol error"
        is DfuError.ChecksumMismatch -> error.message ?: "Checksum mismatch"
        is DfuError.TransferFailed -> error.message ?: "Transfer failed"
        is DfuError.FirmwareParseError -> error.message ?: "Firmware parse error"
        is DfuError.Timeout -> error.message ?: "Timeout"
        is DfuError.Aborted -> error.message ?: "Aborted"
        is DfuError.HashMismatch -> error.message ?: "Hash mismatch"
        is DfuError.ImageSlotError -> error.message ?: "Image slot error"
    }

private fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1_048_576 -> "${bytes * 10 / 1_048_576 / 10.0} MB"
        bytes >= 1_024 -> "${bytes * 10 / 1_024 / 10.0} KB"
        else -> "$bytes B"
    }
