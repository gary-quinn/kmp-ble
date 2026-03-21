package com.atruedev.kmpble.sample

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.scanner.EmissionPolicy
import com.atruedev.kmpble.scanner.Scanner
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, FlowPreview::class)
@Composable
fun ScannerScreen(
    onDeviceSelected: (Advertisement) -> Unit,
    onServerTapped: () -> Unit = {},
) {
    var devices by remember { mutableStateOf(emptyList<Advertisement>()) }
    val scope = rememberCoroutineScope()

    var legacyOnly by remember { mutableStateOf(true) }

    DisposableEffect(legacyOnly) {
        val deviceMap = HashMap<Identifier, Advertisement>()
        val scanner = Scanner {
            emission = EmissionPolicy.FirstThenChanges(rssiThreshold = 5)
            this.legacyOnly = legacyOnly
        }
        val job = scope.launch {
            scanner.advertisements
                .onEach { deviceMap[it.identifier] = it }
                .sample(250)
                .collect { devices = deviceMap.values.sortedByDescending { adv -> adv.rssi } }
        }
        onDispose {
            job.cancel()
            scanner.close()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("kmp-ble Scanner") },
                actions = {
                    TextButton(onClick = onServerTapped) {
                        Text("Server")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            // BLE 5.0 extended advertising toggle
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Extended Ads (BLE 5.0)", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = !legacyOnly,
                    onCheckedChange = { legacyOnly = !it },
                )
            }

            if (devices.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Scanning for BLE devices...", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = devices,
                        key = { it.identifier.value },
                    ) { advertisement ->
                        DeviceCard(advertisement, onClick = { onDeviceSelected(advertisement) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceCard(advertisement: Advertisement, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = advertisement.name ?: "Unknown Device",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${advertisement.rssi} dBm",
                    style = MaterialTheme.typography.labelMedium,
                    color = rssiColor(advertisement.rssi),
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = advertisement.identifier.value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (advertisement.serviceUuids.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (uuid in advertisement.serviceUuids) {
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = uuid.toString().take(8),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
            }

            // BLE 5.0 extended advertisement fields
            if (!advertisement.isLegacy) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Extended | PHY: ${advertisement.primaryPhy}" +
                        (advertisement.secondaryPhy?.let { " / $it" } ?: "") +
                        (advertisement.advertisingSid?.let { " | SID: $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            if (!advertisement.isConnectable) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Not connectable",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun rssiColor(rssi: Int) = when {
    rssi >= -50 -> MaterialTheme.colorScheme.primary
    rssi >= -70 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}
