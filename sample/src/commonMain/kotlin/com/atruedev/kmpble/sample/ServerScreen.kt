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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
    val l2capOpen by vm.l2capOpen.collectAsState()
    val l2capPsm by vm.l2capPsm.collectAsState()
    val l2capLog by vm.l2capLog.collectAsState()
    val l2capStreamedCount by vm.l2capStreamedCount.collectAsState()
    val blobOpen by vm.blobOpen.collectAsState()
    val blobPsm by vm.blobPsm.collectAsState()
    val blobLog by vm.blobLog.collectAsState()
    val blobTotal by vm.blobTotalBytes.collectAsState()
    val blobFrame by vm.blobFrameBytes.collectAsState()
    val blobSendStats by vm.blobSendStats.collectAsState()

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
            item { L2capServerCard(l2capOpen, l2capPsm, l2capLog, l2capStreamedCount, vm) }
            item {
                L2capBlobServerCard(
                    open = blobOpen,
                    psm = blobPsm,
                    totalBytes = blobTotal,
                    frameBytes = blobFrame,
                    stats = blobSendStats,
                    log = blobLog,
                    vm = vm,
                )
            }
            item { LegacyAdvertiserCard(isAdvertising, vm) }
            item { ExtendedAdvertiserCard(activeSets, vm) }
            item { ConnectionLogCard(connectionLog) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun GattServerCard(
    serverOpen: Boolean,
    heartRate: Int,
    vm: ServerViewModel,
) {
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
private fun L2capServerCard(
    open: Boolean,
    psm: Int,
    log: List<String>,
    streamedCount: Int,
    vm: ServerViewModel,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("L2CAP Sensor Stream", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Publishes an L2CAP CoC listener. Each accepted channel receives a " +
                    "SensorReading every 100ms, CBOR-encoded and length-prefix framed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            FilterChip(
                selected = open,
                onClick = {},
                label = {
                    Text(if (open) "Open (PSM=$psm)" else "Closed")
                },
            )

            if (open) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Frames streamed: $streamedCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.openL2capServer(secure = true) },
                    enabled = !open,
                ) { Text("Open Secure") }

                OutlinedButton(
                    onClick = { vm.closeL2capServer() },
                    enabled = open,
                ) { Text("Close") }
            }

            if (log.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    log.take(8).forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun L2capBlobServerCard(
    open: Boolean,
    psm: Int,
    totalBytes: Long,
    frameBytes: Int,
    stats: BlobSendStats?,
    log: List<String>,
    vm: ServerViewModel,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("L2CAP Blob Stream", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Generic large-payload demo. On each accepted L2CAP channel, the server pushes " +
                    "a configurable total (default 5 MiB) as length-prefix-framed BlobChunk " +
                    "messages of the chosen frame size. Receiver shows MTU, OS read chunks, and " +
                    "app frames side by side.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            FilterChip(
                selected = open,
                onClick = {},
                label = { Text(if (open) "Open (PSM=$psm)" else "Closed") },
            )

            Spacer(Modifier.height(8.dp))

            Text("Total payload", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (bytes in BLOB_TOTAL_OPTIONS) {
                    FilterChip(
                        selected = totalBytes == bytes,
                        onClick = { vm.setBlobTotalBytes(bytes) },
                        enabled = !open,
                        label = { Text(humanBytes(bytes)) },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Text("Frame size (app)", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (size in BLOB_FRAME_OPTIONS) {
                    FilterChip(
                        selected = frameBytes == size,
                        onClick = { vm.setBlobFrameBytes(size) },
                        enabled = !open,
                        label = { Text(humanBytes(size.toLong())) },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "Default LengthPrefixFramer caps frames at 64 KiB; CBOR overhead leaves ~60 KiB " +
                    "usable. Larger frames need a custom LengthPrefixFramer(maxFrameSize) on both ends.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.openBlobServer(secure = true) },
                    enabled = !open,
                ) { Text("Open Secure") }

                OutlinedButton(
                    onClick = { vm.closeBlobServer() },
                    enabled = open,
                ) { Text("Close") }
            }

            if (stats != null) {
                Spacer(Modifier.height(8.dp))
                BlobSendStatsBlock(stats)
            }

            if (log.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    log.take(8).forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BlobSendStatsBlock(stats: BlobSendStats) {
    val progress =
        if (stats.totalBytes > 0) {
            (stats.bytesSent.toFloat() / stats.totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    val throughputKbps =
        if (stats.elapsedMs > 0) (stats.bytesSent * 1000.0 / stats.elapsedMs / 1024.0) else 0.0
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "Sent: ${humanBytes(stats.bytesSent)} / ${humanBytes(stats.totalBytes)} " +
                "(${stats.framesSent} frames)",
            style = MaterialTheme.typography.bodySmall,
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Throughput: ${humanThroughput(throughputKbps)}  |  MTU: ${stats.mtu} B  |  " +
                "Elapsed: ${stats.elapsedMs} ms  |  ${if (stats.done) "DONE" else "..."}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ClientPreviewCard(
    heartRate: Int,
    connectedClients: Int,
) {
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
                text =
                    "To see this live on another device: start advertising below, " +
                        "scan from the other device, connect, and open Heart Rate Monitor.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegacyAdvertiserCard(
    isAdvertising: Boolean,
    vm: ServerViewModel,
) {
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
private fun ExtendedAdvertiserCard(
    activeSets: Set<Int>,
    vm: ServerViewModel,
) {
    var periodic by remember { mutableStateOf(false) }

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

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Periodic Advertising", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(8.dp))
                Switch(checked = periodic, onCheckedChange = { periodic = it })
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    vm.startExtendedSet(
                        Phy.Le1M,
                        Phy.Le2M,
                        "$SAMPLE_NAME_PREFIX Ext",
                        AdvertiseInterval.Balanced,
                        periodic,
                    )
                }) { Text("Add Set (1M/2M)") }

                Button(onClick = {
                    vm.startExtendedSet(
                        Phy.LeCoded,
                        Phy.LeCoded,
                        "$SAMPLE_NAME_PREFIX LR",
                        AdvertiseInterval.LowPower,
                        periodic,
                    )
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
