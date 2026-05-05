package com.atruedev.kmpble.sample

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.scanner.Advertisement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceExplorerScreen(
    advertisement: Advertisement,
    onBack: () -> Unit,
) {
    val vm = viewModel(key = advertisement.identifier.value) { BleViewModel(advertisement) }
    val state by vm.connectionState.collectAsState()
    val services by vm.services.collectAsState()
    val benchmarkResult by vm.benchmarkResult.collectAsState()
    val l2capChannel by vm.l2cap.channel.collectAsState()
    val l2capLog by vm.l2cap.log.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Services") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("<", style = MaterialTheme.typography.titleLarge)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { GattDumpSection(vm) }

            val serviceList = services
            if (serviceList != null) {
                items(serviceList) { service ->
                    ServiceCard(service, vm)
                }
            } else if (state is State.Connected) {
                item {
                    Text(
                        "Discovering services...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { BenchmarkSection(state, services, benchmarkResult, vm) }
            item { L2capSection(state, l2capChannel != null, l2capLog, vm) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun CollapsibleCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(if (expanded) "▲" else "▼", style = MaterialTheme.typography.titleSmall)
            }
            AnimatedVisibility(expanded) { content() }
        }
    }
}

@Composable
private fun GattDumpSection(vm: BleViewModel) {
    var dumpText by remember { mutableStateOf<String?>(null) }

    CollapsibleCard("GATT Dump") {
        Column {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { dumpText = vm.dump() }) { Text("Refresh") }
            dumpText?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }
        }
    }
}

@Composable
private fun ServiceCard(
    service: DiscoveredService,
    vm: BleViewModel,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Service: ${service.uuid.toString().take(8)}...",
                style = MaterialTheme.typography.titleSmall,
            )
            for ((index, char) in service.characteristics.withIndex()) {
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                CharacteristicRow(char, vm)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CharacteristicRow(
    characteristic: Characteristic,
    vm: BleViewModel,
) {
    val props = characteristic.properties
    var readValue by remember { mutableStateOf<String?>(null) }
    var writeInput by remember { mutableStateOf("") }
    var writeError by remember { mutableStateOf(false) }
    var observedValue by remember { mutableStateOf<String?>(null) }
    var isObserving by remember { mutableStateOf(false) }

    if (isObserving) {
        LaunchedEffect(characteristic) {
            vm.observe(characteristic).collect { observation ->
                observedValue =
                    when (observation) {
                        is Observation.Value -> observation.data.toHexString()
                        is Observation.Disconnected -> "Disconnected"
                    }
            }
        }
    }

    Column {
        Text(
            text = characteristic.uuid.toString().take(8) + "...",
            style = MaterialTheme.typography.bodyMedium,
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (props.read) PropertyBadge("R")
            if (props.write) PropertyBadge("W")
            if (props.writeWithoutResponse) PropertyBadge("WnR")
            if (props.notify) PropertyBadge("N")
            if (props.indicate) PropertyBadge("I")
        }

        Spacer(Modifier.height(4.dp))

        if (props.read) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        vm.readCharacteristic(characteristic) { result ->
                            readValue =
                                result.fold(
                                    onSuccess = { it.toHexString() },
                                    onFailure = { "Error: ${it.message}" },
                                )
                        }
                    },
                ) { Text("Read") }
                readValue?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (props.write || props.writeWithoutResponse) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = writeInput,
                    onValueChange = {
                        writeInput = it
                        writeError = false
                    },
                    label = { Text("Hex") },
                    modifier = Modifier.width(120.dp),
                    singleLine = true,
                    isError = writeError,
                )
                OutlinedButton(
                    onClick = {
                        try {
                            val bytes = writeInput.hexToByteArray()
                            val type = if (props.write) WriteType.WithResponse else WriteType.WithoutResponse
                            vm.writeCharacteristic(characteristic, bytes, type)
                        } catch (_: IllegalArgumentException) {
                            writeError = true
                        }
                    },
                ) { Text("Write") }
            }
        }

        if (props.notify || props.indicate) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Observe", style = MaterialTheme.typography.bodySmall)
                Switch(checked = isObserving, onCheckedChange = { isObserving = it })
                observedValue?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun PropertyBadge(label: String) {
    FilterChip(
        selected = true,
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
    )
}

@OptIn(ExperimentalBleApi::class)
@Composable
private fun BenchmarkSection(
    state: State,
    services: List<DiscoveredService>?,
    benchmarkResult: String?,
    vm: BleViewModel,
) {
    CollapsibleCard("Benchmarks") {
        Column {
            Spacer(Modifier.height(8.dp))

            Text(
                "Measure connection time and GATT read throughput/latency.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            val readableChar =
                remember(services) {
                    services?.flatMap { it.characteristics }?.firstOrNull { it.properties.read }
                }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.benchmarkConnect() },
                    enabled = state is State.Connected || state is State.Disconnected,
                ) { Text("Bench Connect") }

                OutlinedButton(
                    onClick = { readableChar?.let { vm.benchmarkReads(it) } },
                    enabled = state is State.Connected && readableChar != null,
                ) { Text("Bench Reads") }
            }

            benchmarkResult?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun L2capSection(
    state: State,
    isOpen: Boolean,
    log: List<String>,
    vm: BleViewModel,
) {
    var psmInput by remember { mutableStateOf("") }
    var sendInput by remember { mutableStateOf("") }
    var sendError by remember { mutableStateOf(false) }

    CollapsibleCard("L2CAP Channel") {
        Column {
            Spacer(Modifier.height(8.dp))

            Text(
                "High-throughput streaming bypassing GATT. Requires a device with an open PSM endpoint.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            FilterChip(
                selected = isOpen,
                onClick = {},
                label = { Text(if (isOpen) "Open" else "Closed") },
            )

            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = psmInput,
                    onValueChange = { psmInput = it },
                    label = { Text("PSM") },
                    modifier = Modifier.width(80.dp),
                    singleLine = true,
                )
                Button(
                    onClick = { psmInput.toIntOrNull()?.let { vm.l2cap.open(it) } },
                    enabled = state is State.Connected && !isOpen,
                ) { Text("Open") }
                OutlinedButton(
                    onClick = { vm.l2cap.close() },
                    enabled = isOpen,
                ) { Text("Close") }
            }

            if (isOpen) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = sendInput,
                        onValueChange = {
                            sendInput = it
                            sendError = false
                        },
                        label = { Text("Hex") },
                        modifier = Modifier.width(120.dp),
                        singleLine = true,
                        isError = sendError,
                    )
                    OutlinedButton(
                        onClick = {
                            try {
                                vm.l2cap.write(sendInput.hexToByteArray())
                            } catch (_: IllegalArgumentException) {
                                sendError = true
                            }
                        },
                    ) { Text("Send") }
                }
            }

            if (log.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                for (entry in log.take(10)) {
                    Text(entry, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
