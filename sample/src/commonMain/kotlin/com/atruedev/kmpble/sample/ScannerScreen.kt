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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@Immutable
private data class ScannedDevice(
    val identifier: String,
    val name: String?,
    val rssi: Int,
    val serviceUuids: List<String>,
    val isLegacy: Boolean,
    val isConnectable: Boolean,
    val phyInfo: String?,
)

private fun Advertisement.toScannedDevice() = ScannedDevice(
    identifier = identifier.value,
    name = name,
    rssi = rssi,
    serviceUuids = serviceUuids.map { it.toString().take(8) },
    isLegacy = isLegacy,
    isConnectable = isConnectable,
    phyInfo = if (!isLegacy) {
        "Extended | PHY: $primaryPhy" +
            (secondaryPhy?.let { " / $it" } ?: "") +
            (advertisingSid?.let { " | SID: $it" } ?: "")
    } else {
        null
    },
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScannerScreen(
    onDeviceSelected: (Advertisement) -> Unit,
    onServerTapped: () -> Unit = {},
) {
    var devices by remember { mutableStateOf(emptyList<ScannedDevice>()) }
    // Safe without synchronization: both writer (snapshot coroutine) and reader (click handler)
    // run on Main dispatcher via rememberCoroutineScope / Compose click callback.
    val advertisementLookup = remember { HashMap<String, Advertisement>() }
    val scope = rememberCoroutineScope()

    var legacyOnly by remember { mutableStateOf(true) }
    val scanContext = remember { Dispatchers.Default.limitedParallelism(1) }

    DisposableEffect(legacyOnly) {
        val deviceMap = HashMap<Identifier, Pair<Advertisement, TimeSource.Monotonic.ValueTimeMark>>()
        val scanner = Scanner {
            emission = EmissionPolicy.FirstThenChanges(rssiThreshold = 5)
            this.legacyOnly = legacyOnly
        }
        val job = scope.launch {
            launch(scanContext) {
                scanner.advertisements.conflate().collect {
                    deviceMap[it.identifier] = it to TimeSource.Monotonic.markNow()
                }
            }
            while (isActive) {
                delay(250)
                val snapshot = withContext(scanContext) {
                    deviceMap.entries.removeAll { it.value.second.elapsedNow() > 10.seconds }
                    deviceMap.values.map { it.first }.sortedByDescending { it.rssi }
                }
                advertisementLookup.clear()
                snapshot.forEach { advertisementLookup[it.identifier.value] = it }
                devices = snapshot.map { it.toScannedDevice() }
            }
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
                        key = { it.identifier },
                    ) { device ->
                        DeviceCard(
                            device = device,
                            onClick = {
                                advertisementLookup[device.identifier]?.let(onDeviceSelected)
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceCard(device: ScannedDevice, onClick: () -> Unit) {
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
                    text = device.name ?: "Unknown Device",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${device.rssi} dBm",
                    style = MaterialTheme.typography.labelMedium,
                    color = rssiColor(device.rssi),
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = device.identifier,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (device.serviceUuids.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (uuid in device.serviceUuids) {
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = { Text(uuid, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }

            device.phyInfo?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            if (!device.isConnectable) {
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
