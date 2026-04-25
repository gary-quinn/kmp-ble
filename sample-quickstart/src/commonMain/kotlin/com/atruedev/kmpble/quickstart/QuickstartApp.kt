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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.peripheral.toPeripheral
import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.scanner.Scanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Minimal BLE quickstart - scan, connect, read the first characteristic.
 *
 * This is intentionally simple. For production patterns (reconnection,
 * error handling, profiles, DFU), see the full `sample/` module.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickstartApp() {
    MaterialTheme {
        var selectedAd by remember { mutableStateOf<Advertisement?>(null) }

        if (selectedAd == null) {
            ScanScreen(onDeviceSelected = { selectedAd = it })
        } else {
            DeviceScreen(
                advertisement = selectedAd!!,
                onBack = { selectedAd = null },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanScreen(onDeviceSelected: (Advertisement) -> Unit) {
    val devices = remember { mutableStateListOf<Advertisement>() }
    var scanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("BLE Quickstart") }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        ) {
            // Guard relies on recomposition timing - a rapid double-tap could
            // theoretically create two scanners. Acceptable for quickstart simplicity.
            Button(
                onClick = {
                    if (scanning) return@Button
                    scanning = true
                    devices.clear()
                    scope.launch {
                        val scanner = Scanner { timeout = 10.seconds }
                        try {
                            scanner.advertisements.collect { ad ->
                                if (devices.none { it.identifier == ad.identifier }) {
                                    devices.add(ad)
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            println("Scan ended: ${e.message}")
                        } finally {
                            scanning = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (scanning) "Scanning..." else "Start Scan")
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(devices, key = { it.identifier.toString() }) { ad ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onDeviceSelected(ad) },
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = ad.name ?: "Unknown Device",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = ad.identifier.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                text = "RSSI: ${ad.rssi} dBm",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceScreen(
    advertisement: Advertisement,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Connecting...") }
    var readValue by remember { mutableStateOf<String?>(null) }
    val peripheral = remember { advertisement.toPeripheral() }

    DisposableEffect(peripheral) {
        onDispose { peripheral.close() }
    }

    LaunchedEffect(peripheral) {
        try {
            peripheral.connect()
            status = "Connected - discovering services..."

            val discoveredServices = peripheral.services.filterNotNull().first()
            val firstReadable: Characteristic? =
                discoveredServices
                    .flatMap { it.characteristics }
                    .firstOrNull { it.properties.read }

            if (firstReadable == null) {
                status = "No readable characteristics found"
                return@LaunchedEffect
            }

            status = "Reading ${firstReadable.uuid.toString().take(8)}..."
            val data = peripheral.read(firstReadable)
            readValue =
                data.joinToString(" ") { byte ->
                    byte
                        .toInt()
                        .and(0xFF)
                        .toString(16)
                        .padStart(2, '0')
                        .uppercase()
                }
            status = "Read from ${firstReadable.uuid.toString().take(8)}..."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            status = "Error: ${e.message}"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(advertisement.name ?: "Device") },
                navigationIcon = {
                    Button(onClick = onBack) { Text("Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Intentional simplification: string-based state check avoids a sealed class
            // for a quickstart sample. See sample/ for production-grade state management.
            if (readValue == null && !status.startsWith("Error") && !status.startsWith("No")) {
                CircularProgressIndicator()
            }

            Text(text = status, style = MaterialTheme.typography.bodyLarge)

            if (readValue != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Value (hex):", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = readValue!!,
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        try {
                            peripheral.disconnect()
                        } catch (_: Exception) {
                            // Best-effort disconnect
                        }
                        onBack()
                    }
                }) {
                    Text("Disconnect")
                }
            }
        }
    }
}
