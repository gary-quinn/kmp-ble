package com.atruedev.kmpble.sample

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.atruedev.kmpble.ServiceUuid
import com.atruedev.kmpble.adapter.BleCapabilities
import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.scanner.EmissionPolicy
import com.atruedev.kmpble.scanner.ScanEvent
import com.atruedev.kmpble.scanner.Scanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Immutable
private data class ScannedDevice(
    val identifier: String,
    val name: String?,
    val rssi: Int,
    val serviceUuids: List<String>,
    val isLegacy: Boolean,
    val isConnectable: Boolean,
    val phyInfo: String?,
    val isSample: Boolean,
)

private fun Advertisement.isSampleServer(): Boolean = name?.startsWith(SAMPLE_NAME_PREFIX) == true

@OptIn(ExperimentalUuidApi::class)
private fun Advertisement.toScannedDevice() =
    ScannedDevice(
        identifier = identifier.value,
        name = name,
        rssi = rssi,
        serviceUuids = serviceUuids.map { uuid -> wellKnownServiceName(uuid) ?: uuid.toString().take(8) },
        isLegacy = isLegacy,
        isConnectable = isConnectable,
        phyInfo =
            if (!isLegacy) {
                "Extended | PHY: $primaryPhy" +
                    (secondaryPhy?.let { " / $it" } ?: "") +
                    (advertisingSid?.let { " | SID: $it" } ?: "")
            } else {
                null
            },
        isSample = isSampleServer(),
    )

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScannerScreen(
    capabilities: BleCapabilities,
    onDeviceSelected: (Advertisement) -> Unit,
    onServerTapped: () -> Unit = {},
    onBeaconTapped: () -> Unit = {},
) {
    var devices by remember { mutableStateOf(emptyList<ScannedDevice>()) }
    var scanError by remember { mutableStateOf<String?>(null) }
    // Safe without synchronization: both writer (snapshot coroutine) and reader (click handler)
    // run on Main dispatcher via rememberCoroutineScope / Compose click callback.
    val advertisementLookup = remember { HashMap<String, Advertisement>() }
    val scope = rememberCoroutineScope()

    var legacyOnly by remember { mutableStateOf(true) }
    var filterQuery by remember { mutableStateOf("") }
    var serviceFilter by remember { mutableStateOf<String?>(null) }
    val scanContext = remember { Dispatchers.Default.limitedParallelism(1) }

    DisposableEffect(legacyOnly) {
        val deviceMap = HashMap<Identifier, Pair<Advertisement, TimeSource.Monotonic.ValueTimeMark>>()
        val scanner =
            Scanner {
                emission = EmissionPolicy.FirstThenChanges(rssiThreshold = 5)
                this.legacyOnly = legacyOnly
            }
        val job =
            scope.launch {
                launch(scanContext) {
                    scanner.scanEvents.collect { event ->
                        when (event) {
                            is ScanEvent.Found -> {
                                deviceMap[event.advertisement.identifier] =
                                    event.advertisement to TimeSource.Monotonic.markNow()
                            }
                            is ScanEvent.Failed -> {
                                scanError = "Scan failed (code ${event.error.errorCode})"
                            }
                        }
                    }
                }
                while (isActive) {
                    delay(250)
                    val snapshot =
                        withContext(scanContext) {
                            deviceMap.entries.removeAll { it.value.second.elapsedNow() > 10.seconds }
                            deviceMap.values
                                .map { it.first }
                                .sortedWith(
                                    compareByDescending<Advertisement> { it.isSampleServer() }
                                        .thenByDescending { it.rssi },
                                )
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
                title = { Text("BLE Explorer") },
                actions = {
                    TextButton(onClick = onBeaconTapped) {
                        Text("Beacons")
                    }
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
            OutlinedTextField(
                value = filterQuery,
                onValueChange = { filterQuery = it },
                placeholder = { Text("Filter by name or ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )

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

            // BLE 5.x Capabilities
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (capabilities.supportsLe2mPhy) {
                    CapabilityChip("2M PHY")
                }
                if (capabilities.supportsLeCodedPhy) {
                    CapabilityChip("Coded PHY")
                }
                if (capabilities.supportsExtendedAdvertising) {
                    CapabilityChip("Ext Adv")
                }
                if (capabilities.supportsPeriodicAdvertising) {
                    CapabilityChip("Per Adv")
                }
                if (capabilities.supportsLeAudio) {
                    CapabilityChip("LE Audio")
                }
                if (capabilities.supportsLePowerControl) {
                    CapabilityChip("Pwr Ctrl")
                }
                if (capabilities.supportsDirectionFinding) {
                    CapabilityChip("AoA/AoD")
                }
                if (capabilities.supportsConnectionSubrating) {
                    CapabilityChip("Subrating")
                }
                if (capabilities.supportsPast) {
                    CapabilityChip("PAST")
                }
            }

            Spacer(Modifier.height(4.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(
                    selected = serviceFilter == null,
                    onClick = { serviceFilter = null },
                    label = { Text("All", style = MaterialTheme.typography.labelSmall) },
                )
                for (name in WELL_KNOWN_SERVICES.values) {
                    FilterChip(
                        selected = serviceFilter == name,
                        onClick = { serviceFilter = if (serviceFilter == name) null else name },
                        label = { Text(name, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            scanError?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            val filteredDevices =
                devices.filter { device ->
                    val matchesQuery =
                        filterQuery.isBlank() ||
                            device.name?.lowercase()?.contains(filterQuery.lowercase()) == true ||
                            device.identifier.lowercase().contains(filterQuery.lowercase())
                    val matchesService =
                        serviceFilter == null ||
                            device.serviceUuids.contains(serviceFilter)
                    matchesQuery && matchesService
                }

            if (filteredDevices.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (filterQuery.isBlank()) "Scanning for BLE devices..." else "No devices match filter",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = filteredDevices,
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
private fun DeviceCard(
    device: ScannedDevice,
    onClick: () -> Unit,
) {
    val cardColors =
        if (device.isSample) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    val cardBorder =
        if (device.isSample) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = cardColors,
        border = cardBorder,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (device.isSample) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = "$SAMPLE_NAME_PREFIX sample",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
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
private fun rssiColor(rssi: Int) =
    when {
        rssi >= -50 -> MaterialTheme.colorScheme.primary
        rssi >= -70 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

@OptIn(ExperimentalUuidApi::class)
private val WELL_KNOWN_SERVICES: Map<Uuid, String> =
    mapOf(
        ServiceUuid.HEART_RATE to "Heart Rate",
        ServiceUuid.BATTERY to "Battery",
        ServiceUuid.DEVICE_INFORMATION to "Device Info",
        ServiceUuid.HEALTH_THERMOMETER to "Thermometer",
        ServiceUuid.GLUCOSE to "Glucose",
        ServiceUuid.CURRENT_TIME to "Current Time",
        ServiceUuid.GENERIC_ACCESS to "Generic Access",
    )

@OptIn(ExperimentalUuidApi::class)
private fun wellKnownServiceName(uuid: Uuid): String? = WELL_KNOWN_SERVICES[uuid]

@Composable
private fun CapabilityChip(label: String) {
    FilterChip(
        selected = true,
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
    )
}
