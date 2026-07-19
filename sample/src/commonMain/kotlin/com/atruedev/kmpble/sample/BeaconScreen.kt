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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.atruedev.kmpble.beacon.Beacon
import com.atruedev.kmpble.beacon.BeaconEvent
import com.atruedev.kmpble.beacon.BeaconScanner
import com.atruedev.kmpble.scanner.Scanner
import com.atruedev.kmpble.scanner.uuidFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
private val EDDYSTONE_SERVICE = uuidFrom("FEAA")

@Immutable
private data class BeaconEntry(
    val id: String,
    val type: String,
    val rssi: Int,
    val firstLine: String,
    val secondLine: String?,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BeaconScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf(emptyList<BeaconEntry>()) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf<String?>(null) }
    val seenBeacons = remember { LinkedHashSet<String>() }

    DisposableEffect(Unit) {
        val scanner =
            Scanner {
                legacyOnly = false
            }
        val beaconScanner = BeaconScanner(scanner, scope)

        val job =
            scope.launch {
                launch(Dispatchers.Default.limitedParallelism(1)) {
                    beaconScanner.beaconEvents.collect { event ->
                        when (event) {
                            is BeaconEvent.Found -> {
                                val key = beaconKey(event.beacon)
                                if (seenBeacons.add(key)) {
                                    entries = entries + event.beacon.toEntry()
                                } else {
                                    // Update RSSI for existing entry
                                    entries =
                                        entries.map { e ->
                                            if (e.id == key) e.copy(rssi = event.beacon.source.rssi) else e
                                        }
                                }
                            }
                            is BeaconEvent.Failed -> {
                                scanError = "Beacon scan error: ${event.error.message}"
                            }
                        }
                    }
                }

                beaconScanner.start()

                // Evict beacons not seen for 30 seconds
                while (isActive) {
                    delay(5000)
                }
            }

        onDispose {
            job.cancel()
            beaconScanner.close()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Beacon Scanner") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("<", style = MaterialTheme.typography.titleLarge)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(
                    selected = selectedType == null,
                    onClick = { selectedType = null },
                    label = { Text("All", style = MaterialTheme.typography.labelSmall) },
                )
                for (type in listOf("iBeacon", "Eddystone-UID", "Eddystone-URL", "Eddystone-TLM")) {
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = if (selectedType == type) null else type },
                        label = { Text(type, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            scanError?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            val filtered =
                if (selectedType == null) {
                    entries
                } else {
                    entries.filter { it.type == selectedType }
                }

            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Scanning for beacons...",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "iBeacon and Eddystone beacons near you will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { it.id }) { entry ->
                        BeaconCard(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun BeaconCard(entry: BeaconEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = { Text(entry.type, style = MaterialTheme.typography.labelSmall) },
                )
                Text(
                    text = "${entry.rssi} dBm",
                    style = MaterialTheme.typography.labelMedium,
                    color = rssiColor(entry.rssi),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(entry.firstLine, style = MaterialTheme.typography.bodyMedium)
            entry.secondLine?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun beaconKey(beacon: Beacon): String =
    when (beacon) {
        is Beacon.IBeacon -> "ibeacon:${beacon.proximityUuid}:${beacon.major}:${beacon.minor}"
        is Beacon.EddystoneUID -> "eddyuid:${beacon.namespace.toHex()}:${beacon.instance.toHex()}"
        is Beacon.EddystoneURL -> "eddyurl:${beacon.url}"
        is Beacon.EddystoneTLM -> "eddytlm:${beacon.source.identifier.value}"
    }

private fun Beacon.toEntry(): BeaconEntry =
    when (this) {
        is Beacon.IBeacon ->
            BeaconEntry(
                id = beaconKey(this),
                type = "iBeacon",
                rssi = source.rssi,
                firstLine = "UUID: ${this.proximityUuid}",
                secondLine = "Major: ${this.major}  |  Minor: ${this.minor}  |  1m RSSI: ${this.measuredPower}",
            )
        is Beacon.EddystoneUID ->
            BeaconEntry(
                id = beaconKey(this),
                type = "Eddystone-UID",
                rssi = source.rssi,
                firstLine = "Namespace: ${this.namespace.toHex()}",
                secondLine = "Instance: ${this.instance.toHex()}  |  TxPower: ${this.rangingData}",
            )
        is Beacon.EddystoneURL ->
            BeaconEntry(
                id = beaconKey(this),
                type = "Eddystone-URL",
                rssi = source.rssi,
                firstLine = this.url,
                secondLine = "TxPower: ${this.txPower}",
            )
        is Beacon.EddystoneTLM ->
            BeaconEntry(
                id = beaconKey(this),
                type = "Eddystone-TLM",
                rssi = source.rssi,
                firstLine = buildString {
                    this.batteryVoltageMv?.let { append("Battery: ${it}mV  ") }
                    this.temperatureCelsius?.let { append("Temp: ${"%.1f".format(it)}C  ") }
                }.trimEnd(),
                secondLine = "Adv count: ${this.advertisementCount}  |  Uptime: ${"%.0f".format(this.uptimeSeconds)}s",
            )
    }

@Composable
private fun rssiColor(rssi: Int) =
    when {
        rssi >= -50 -> MaterialTheme.colorScheme.primary
        rssi >= -70 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
