package com.atruedev.kmpble.quickstart

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.peripheral.Peripheral
import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.scanner.ScanEvent
import com.atruedev.kmpble.scanner.uuidFrom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class ScannedDevice(
    val advertisement: Advertisement,
    val name: String,
    val rssi: Int,
)

private sealed interface SessionState {
    data object Idle : SessionState

    data class Connecting(
        val name: String,
    ) : SessionState

    data class Connected(
        val name: String,
        val characteristicLabel: String,
        val mode: String,
    ) : SessionState

    data class Error(
        val message: String,
    ) : SessionState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickstartScreen(ble: QuickstartBle = defaultQuickstartBle()) {
    val scope = rememberCoroutineScope()
    val devices = remember { mutableStateListOf<ScannedDevice>() }
    var session by remember { mutableStateOf<SessionState>(SessionState.Idle) }
    var latestValue by remember { mutableStateOf<String?>(null) }
    var activePeripheral by remember { mutableStateOf<Peripheral?>(null) }
    var observeJob by remember { mutableStateOf<Job?>(null) }

    val scanner = remember(ble) { ble.createScanner() }

    LaunchedEffect(scanner) {
        scanner.scanEvents.collect { event ->
            when (event) {
                is ScanEvent.Found -> {
                    val ad = event.advertisement
                    if (!ad.isConnectable) return@collect
                    val entry =
                        ScannedDevice(
                            advertisement = ad,
                            name = ad.name ?: "Unknown",
                            rssi = ad.rssi,
                        )
                    val index = devices.indexOfFirst { it.advertisement.identifier == ad.identifier }
                    if (index >= 0) {
                        devices[index] = entry
                    } else {
                        devices.add(entry)
                    }
                }
                is ScanEvent.Failed -> {
                    session = SessionState.Error(event.error.message ?: "Scan failed")
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            observeJob?.cancel()
            activePeripheral?.close()
            scanner.close()
        }
    }

    suspend fun cleanupSession() {
        observeJob?.cancel()
        observeJob = null
        runCatching { activePeripheral?.disconnect() }
        activePeripheral?.close()
        activePeripheral = null
        latestValue = null
    }

    fun disconnect() {
        scope.launch {
            cleanupSession()
            session = SessionState.Idle
        }
    }

    fun connect(device: ScannedDevice) {
        session = SessionState.Connecting(device.name)
        scope.launch {
            cleanupSession()
            val peripheral = ble.connect(device.advertisement)
            activePeripheral = peripheral
            try {
                peripheral.connect()
                peripheral.services.first { it != null }
                val services = peripheral.services.value.orEmpty()
                val target =
                    pickTargetCharacteristic(services)
                        ?: error("No readable or notifiable characteristic found")

                val label = "${target.serviceUuid}/${target.uuid} (${target.properties.displayName})"
                val mode =
                    if (target.properties.notify || target.properties.indicate) {
                        "observe"
                    } else {
                        "read"
                    }
                session =
                    SessionState.Connected(
                        name = device.name,
                        characteristicLabel = label,
                        mode = mode,
                    )

                if (mode == "observe") {
                    observeJob =
                        launch {
                            try {
                                peripheral
                                    .observeValues(target, BackpressureStrategy.Latest)
                                    .collect { data ->
                                        latestValue = formatValue(target, data)
                                    }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                session = SessionState.Error(e.message ?: "Observation failed")
                            }
                        }
                } else {
                    val data = peripheral.read(target)
                    latestValue = formatValue(target, data)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                session = SessionState.Error(e.message ?: "Connection failed")
                runCatching { peripheral.disconnect() }
                peripheral.close()
                activePeripheral = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("BLE Quickstart") })
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val current = session) {
                SessionState.Idle -> {
                    Text(
                        "Tap a device to connect, read, or observe its first useful characteristic.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag(QuickstartTestTags.SESSION),
                    )
                }
                is SessionState.Connecting -> {
                    Text(
                        "Connecting to ${current.name}...",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag(QuickstartTestTags.SESSION),
                    )
                }
                is SessionState.Connected -> {
                    Text(
                        "Connected: ${current.name}",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag(QuickstartTestTags.SESSION),
                    )
                    Text("Characteristic: ${current.characteristicLabel}")
                    Text("Mode: ${current.mode}")
                    latestValue?.let {
                        Text(
                            "Value: $it",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.testTag(QuickstartTestTags.VALUE),
                        )
                    }
                    Button(
                        onClick = ::disconnect,
                        modifier = Modifier.fillMaxWidth().testTag(QuickstartTestTags.DISCONNECT),
                    ) {
                        Text("Disconnect")
                    }
                }
                is SessionState.Error -> {
                    Text(
                        "Error: ${current.message}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(QuickstartTestTags.SESSION),
                    )
                    Button(
                        onClick = ::disconnect,
                        modifier = Modifier.fillMaxWidth().testTag(QuickstartTestTags.DISCONNECT),
                    ) {
                        Text("Back to scan")
                    }
                }
            }

            if (session is SessionState.Idle || session is SessionState.Error) {
                Text(
                    "Nearby devices",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag(QuickstartTestTags.NEARBY),
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(devices, key = { it.advertisement.identifier.value }) { device ->
                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag(QuickstartTestTags.SCAN_ROW)
                                    .clickable(enabled = session !is SessionState.Connecting) {
                                        connect(device)
                                    },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(device.name, fontWeight = FontWeight.Medium)
                                    Text(
                                        device.advertisement.identifier.value,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Text("${device.rssi} dBm")
                            }
                        }
                    }
                }
            }
        }
    }
}

private val heartRateServiceUuid = uuidFrom("180D")
private val heartRateMeasurementUuid = uuidFrom("2A37")

private fun pickTargetCharacteristic(services: List<DiscoveredService>): Characteristic? {
    services
        .firstOrNull { it.uuid == heartRateServiceUuid }
        ?.characteristics
        ?.firstOrNull { it.uuid == heartRateMeasurementUuid }
        ?.let { return it }

    services.forEach { service ->
        service.characteristics.firstOrNull { it.properties.notify || it.properties.indicate }?.let {
            return it
        }
    }
    return services.flatMap { it.characteristics }.firstOrNull { it.properties.read }
}

private fun formatValue(
    characteristic: Characteristic,
    data: ByteArray,
): String {
    val hex = data.toHexString()
    if (characteristic.serviceUuid == heartRateServiceUuid &&
        characteristic.uuid == heartRateMeasurementUuid
    ) {
        parseHeartRate(data)?.let { bpm -> return "$bpm BPM (hex: $hex)" }
    }
    return hex
}
