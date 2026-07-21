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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atruedev.kmpble.peripheral.state.State
import com.atruedev.kmpble.scanner.Advertisement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    advertisement: Advertisement,
    onBack: () -> Unit,
) {
    val vm = viewModel(key = advertisement.identifier.value) { BleViewModel(advertisement) }
    val state by vm.connectionState.collectAsState()
    val quality by vm.connectionQuality.collectAsState()
    val pathLoss by vm.pathLoss.collectAsState()
    val isConnected = state is State.Connected

    DisposableEffect(isConnected) {
        if (isConnected) {
            vm.startMonitoring()
        }
        onDispose { vm.stopMonitoring() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connection Monitor") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Connection health card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Connection Health", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))

                    val connected = quality.isConnected || isConnected
                    FilterChip(
                        selected = connected,
                        onClick = {},
                        label = { Text(if (connected) "Connected" else "Disconnected") },
                    )

                    Spacer(Modifier.height(8.dp))

                    MetricRow("Total connections", quality.totalConnections.toString())
                    MetricRow("Total disconnections", quality.totalDisconnections.toString())
                    MetricRow("Reconnections", quality.reconnectionCount.toString())
                    MetricRow(
                        "Last RSSI",
                        quality.lastRssi?.let { "$it dBm" } ?: "--",
                    )
                }
            }

            // Path loss card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Path Loss (LE Power Monitor)", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))

                    pathLoss?.let { reading ->
                        MetricRow("Path loss", "${reading.pathLoss} dB")
                        MetricRow("RSSI", "${reading.rssi} dBm")
                        MetricRow("TX power (configured)", "${reading.txPower} dBm")

                        Spacer(Modifier.height(8.dp))
                        val assessment =
                            when {
                                reading.pathLoss < 40 -> "Good signal"
                                reading.pathLoss < 60 -> "Moderate -- consider moving closer"
                                reading.pathLoss < 80 -> "Weak -- high path loss"
                                else -> "Very weak -- device may be out of range"
                            }
                        Text(
                            assessment,
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                when {
                                    reading.pathLoss < 40 -> MaterialTheme.colorScheme.primary
                                    reading.pathLoss < 60 -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.error
                                },
                        )
                    } ?: Text(
                        "No path loss readings yet. RSSI auto-polls every 2s while connected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Manual RSSI trigger
            OutlinedButton(
                onClick = { vm.readRssi() },
                enabled = isConnected,
            ) {
                Text("Read RSSI Now")
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Uses ConnectionQualityMonitor + PowerMonitor from kmp-ble",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!isConnected && state !is State.Connecting) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Connect to view monitoring metrics.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
