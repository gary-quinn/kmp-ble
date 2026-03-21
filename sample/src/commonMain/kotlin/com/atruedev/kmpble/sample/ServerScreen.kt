package com.atruedev.kmpble.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.server.AdvertiseInterval

@OptIn(ExperimentalMaterial3Api::class, ExperimentalBleApi::class, ExperimentalLayoutApi::class)
@Composable
fun ServerScreen(onBack: () -> Unit) {
    val vm = viewModel { ServerViewModel() }
    val snackbar = remember { SnackbarHostState() }

    val serverOpen by vm.serverOpen.collectAsState()
    val heartRate by vm.heartRate.collectAsState()
    val isAdvertising by vm.isAdvertising.collectAsState()
    val activeSets by vm.activeSets.collectAsState()
    val connectionLog by vm.connectionLog.collectAsState()
    val error by vm.error.collectAsState()

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GATT Server & Advertiser") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("<", style = MaterialTheme.typography.titleLarge)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { GattServerCard(serverOpen, heartRate, vm) }
            if (serverOpen) {
                item { ClientPreviewCard(heartRate, connectionLog.size) }
            }
            item { LegacyAdvertiserCard(isAdvertising, vm) }
            item { ExtendedAdvertiserCard(activeSets, vm) }
            item { ConnectionLogCard(connectionLog) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun GattServerCard(serverOpen: Boolean, heartRate: Int, vm: ServerViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("GATT Server", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            Text(
                "Hosts a Heart Rate service (0x180D) with a readable/notifiable measurement characteristic.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            FilterChip(
                selected = serverOpen,
                onClick = {},
                label = { Text(if (serverOpen) "Open" else "Closed") },
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.openServer() },
                    enabled = !serverOpen,
                ) { Text("Open") }

                OutlinedButton(
                    onClick = { vm.closeServer() },
                    enabled = serverOpen,
                ) { Text("Close") }
            }

            if (serverOpen) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("HR: $heartRate bpm", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { vm.randomizeHeartRate() }) {
                        Text("Randomize")
                    }
                    OutlinedButton(onClick = { vm.notifyClients() }) {
                        Text("Notify All")
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientPreviewCard(heartRate: Int, connectedClients: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Client Preview", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "What connected clients see when you tap Notify All.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = heartRate.toString(),
                fontSize = 72.sp,
                fontWeight = FontWeight.Thin,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "BPM",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "To see this live on another device: start advertising below, " +
                    "scan from the other device, connect, and open Heart Rate Monitor.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegacyAdvertiserCard(isAdvertising: Boolean, vm: ServerViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Legacy Advertiser", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            FilterChip(
                selected = isAdvertising,
                onClick = {},
                label = { Text(if (isAdvertising) "Advertising" else "Stopped") },
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.startLegacyAdvertising() },
                    enabled = !isAdvertising,
                ) { Text("Start") }

                OutlinedButton(
                    onClick = { vm.stopLegacyAdvertising() },
                    enabled = isAdvertising,
                ) { Text("Stop") }
            }
        }
    }
}

@OptIn(ExperimentalBleApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ExtendedAdvertiserCard(activeSets: Set<Int>, vm: ServerViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Extended Advertiser (BLE 5.0)", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            Text(
                "Supports larger payloads, PHY selection, and multiple concurrent advertising sets.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            Text("Active sets: ${activeSets.size}", style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    vm.startExtendedSet(Phy.Le1M, Phy.Le2M, "kmp-ble Ext", AdvertiseInterval.Balanced)
                }) { Text("Add Set (1M/2M)") }

                Button(onClick = {
                    vm.startExtendedSet(Phy.LeCoded, Phy.LeCoded, "kmp-ble LR", AdvertiseInterval.LowPower)
                }) { Text("Add Set (Coded)") }
            }

            if (activeSets.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (setId in activeSets) {
                        FilterChip(
                            selected = true,
                            onClick = { vm.stopExtendedSet(setId) },
                            label = { Text("Set $setId (tap to stop)") },
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = { vm.stopAllExtendedSets() },
                enabled = activeSets.isNotEmpty(),
            ) { Text("Stop All") }
        }
    }
}

@Composable
private fun ConnectionLogCard(connectionLog: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Connection Events", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            if (connectionLog.isEmpty()) {
                Text(
                    "No connection events yet. Open the server and connect a client.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                for (entry in connectionLog) {
                    Text(entry, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
