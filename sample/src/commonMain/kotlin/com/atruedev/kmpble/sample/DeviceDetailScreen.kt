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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.bonding.BondState
import com.atruedev.kmpble.bonding.PairingEvent
import com.atruedev.kmpble.bonding.PairingResponse
import com.atruedev.kmpble.connection.BondingPreference
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.ConnectionRecipe
import com.atruedev.kmpble.connection.ReconnectionStrategy
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.scanner.Advertisement

@OptIn(ExperimentalMaterial3Api::class, ExperimentalBleApi::class)
@Composable
fun DeviceDetailScreen(
    advertisement: Advertisement,
    onBack: () -> Unit,
) {
    val vm = viewModel { BleViewModel(advertisement) }

    val state by vm.connectionState.collectAsState()
    val bond by vm.bondState.collectAsState()
    val services by vm.services.collectAsState()
    val rssi by vm.rssi.collectAsState()
    val mtu by vm.mtu.collectAsState()
    val maxWriteLen by vm.maximumWriteValueLength.collectAsState()
    val error by vm.error.collectAsState()
    val pairingEvent by vm.pairing.event.collectAsState()
    val benchmarkResult by vm.benchmarkResult.collectAsState()

    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    pairingEvent?.let { event ->
        PairingDialog(event = event, onRespond = { vm.pairing.respond(it) })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(advertisement.name ?: "Device") },
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
            item { ConnectionSection(state, bond, vm) }
            item { InfoSection(rssi, mtu, maxWriteLen, vm) }
            item { BenchmarkSection(state, services, benchmarkResult, vm) }

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

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Stable
@OptIn(ExperimentalBleApi::class)
private sealed interface RecipeOption {
    val label: String

    data object Custom : RecipeOption { override val label = "Custom" }
    data class Preset(override val label: String, val options: ConnectionOptions) : RecipeOption

    companion object {
        val entries = listOf(
            Custom,
            Preset("Medical", ConnectionRecipe.MEDICAL),
            Preset("Fitness", ConnectionRecipe.FITNESS),
            Preset("IoT", ConnectionRecipe.IOT),
            Preset("Consumer", ConnectionRecipe.CONSUMER),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalBleApi::class)
@Composable
private fun ConnectionSection(state: State, bond: BondState, vm: BleViewModel) {
    var autoConnect by remember { mutableStateOf(false) }
    var useReconnection by remember { mutableStateOf(false) }
    var bondingPref by remember { mutableStateOf(BondingPreference.IfRequired) }
    var selectedRecipe by remember { mutableStateOf<RecipeOption>(RecipeOption.Custom) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Connection", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            FilterChip(
                selected = state is State.Connected,
                onClick = {},
                label = { Text(stateLabel(state)) },
            )

            Spacer(Modifier.height(8.dp))

            Text("Connection Recipe", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (recipe in RecipeOption.entries) {
                    FilterChip(
                        selected = selectedRecipe == recipe,
                        onClick = { selectedRecipe = recipe },
                        label = { Text(recipe.label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            if (selectedRecipe is RecipeOption.Custom) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("autoConnect", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = autoConnect, onCheckedChange = { autoConnect = it })
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Reconnection", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = useReconnection, onCheckedChange = { useReconnection = it })
                }

                Text("Bonding", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (pref in BondingPreference.entries) {
                        FilterChip(
                            selected = bondingPref == pref,
                            onClick = { bondingPref = pref },
                            label = { Text(pref.name, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val baseOptions = when (val recipe = selectedRecipe) {
                            is RecipeOption.Custom -> ConnectionOptions(
                                autoConnect = autoConnect,
                                bondingPreference = bondingPref,
                                reconnectionStrategy = if (useReconnection) {
                                    ReconnectionStrategy.ExponentialBackoff()
                                } else {
                                    ReconnectionStrategy.None
                                },
                            )
                            is RecipeOption.Preset -> recipe.options
                        }
                        vm.connect(baseOptions)
                    },
                    enabled = state is State.Disconnected,
                ) { Text("Connect") }

                OutlinedButton(
                    onClick = { vm.disconnect() },
                    enabled = state is State.Connected || state is State.Connecting,
                ) { Text("Disconnect") }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Bond: ${bondLabel(bond)}", style = MaterialTheme.typography.bodySmall)
                if (bond is BondState.Bonded) {
                    OutlinedButton(onClick = { vm.removeBond() }) {
                        Text("Remove Bond", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoSection(rssi: Int?, mtu: Int, maxWriteLen: Int, vm: BleViewModel) {
    var mtuInput by remember { mutableStateOf("512") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Info", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            Text("RSSI: ${rssi?.let { "$it dBm" } ?: "—"}", style = MaterialTheme.typography.bodySmall)
            Text("MTU: $mtu", style = MaterialTheme.typography.bodySmall)
            Text("Max write length: $maxWriteLen", style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { vm.readRssi() }) { Text("Read RSSI") }

                OutlinedTextField(
                    value = mtuInput,
                    onValueChange = { mtuInput = it },
                    label = { Text("MTU") },
                    modifier = Modifier.width(80.dp),
                    singleLine = true,
                )
                OutlinedButton(
                    onClick = { mtuInput.toIntOrNull()?.let { vm.requestMtu(it) } },
                ) { Text("Set") }
            }
        }
    }
}

@Composable
private fun ServiceCard(service: DiscoveredService, vm: BleViewModel) {
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
private fun CharacteristicRow(characteristic: Characteristic, vm: BleViewModel) {
    val props = characteristic.properties
    var readValue by remember { mutableStateOf<String?>(null) }
    var writeInput by remember { mutableStateOf("") }
    var writeError by remember { mutableStateOf(false) }
    var observedValue by remember { mutableStateOf<String?>(null) }
    var isObserving by remember { mutableStateOf(false) }

    if (isObserving) {
        LaunchedEffect(characteristic) {
            vm.observe(characteristic).collect { observation ->
                observedValue = when (observation) {
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
                            readValue = result.fold(
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
                    onValueChange = { writeInput = it; writeError = false },
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
                Switch(
                    checked = isObserving,
                    onCheckedChange = { isObserving = it },
                )
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
private fun PairingDialog(event: PairingEvent, onRespond: (PairingResponse) -> Unit) {
    var pinInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Pairing Request", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            when (event) {
                is PairingEvent.NumericComparison -> {
                    Text(
                        "Confirm the number matches: ${event.numericValue}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onRespond(PairingResponse.Confirm(true)) }) {
                            Text("Confirm")
                        }
                        OutlinedButton(onClick = { onRespond(PairingResponse.Confirm(false)) }) {
                            Text("Reject")
                        }
                    }
                }

                is PairingEvent.PasskeyRequest -> {
                    Text("Enter the passkey:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it },
                        label = { Text("PIN") },
                        singleLine = true,
                        modifier = Modifier.width(120.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { pinInput.toIntOrNull()?.let { onRespond(PairingResponse.ProvidePin(it)) } },
                    ) { Text("Submit") }
                }

                is PairingEvent.PasskeyNotification -> {
                    Text(
                        "Passkey displayed on device: ${event.passkey}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onRespond(PairingResponse.Confirm(true)) }) {
                        Text("OK")
                    }
                }

                is PairingEvent.JustWorksConfirmation -> {
                    Text("Allow pairing with this device?", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onRespond(PairingResponse.Confirm(true)) }) {
                            Text("Allow")
                        }
                        OutlinedButton(onClick = { onRespond(PairingResponse.Confirm(false)) }) {
                            Text("Deny")
                        }
                    }
                }

                is PairingEvent.OutOfBandDataRequest -> {
                    Text("OOB pairing requested. Providing empty OOB data.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onRespond(PairingResponse.ProvideOobData(ByteArray(16))) }) {
                        Text("Provide OOB Data")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalBleApi::class)
@Composable
private fun BenchmarkSection(
    state: State,
    services: List<DiscoveredService>?,
    benchmarkResult: String?,
    vm: BleViewModel,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Benchmarks", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            Text(
                "Measure connection time and GATT read throughput/latency.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            val readableChar = remember(services) {
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

private fun stateLabel(state: State): String = when (state) {
    is State.Connecting.Transport -> "Connecting (transport)"
    is State.Connecting.Authenticating -> "Connecting (auth)"
    is State.Connecting.Discovering -> "Connecting (discovery)"
    is State.Connecting.Configuring -> "Connecting (config)"
    is State.Connected.Ready -> "Connected"
    is State.Connected.BondingChange -> "Connected (bonding change)"
    is State.Connected.ServiceChanged -> "Connected (service changed)"
    is State.Disconnecting.Requested -> "Disconnecting"
    is State.Disconnecting.Error -> "Disconnecting (error)"
    is State.Disconnected.ByRequest -> "Disconnected"
    is State.Disconnected.ByRemote -> "Disconnected (remote)"
    is State.Disconnected.ByError -> "Disconnected (error)"
    is State.Disconnected.ByTimeout -> "Disconnected (timeout)"
    is State.Disconnected.BySystemEvent -> "Disconnected (system)"
}

private fun bondLabel(state: BondState): String = when (state) {
    BondState.NotBonded -> "Not bonded"
    BondState.Bonding -> "Bonding..."
    BondState.Bonded -> "Bonded"
    BondState.Unknown -> "Unknown"
}
