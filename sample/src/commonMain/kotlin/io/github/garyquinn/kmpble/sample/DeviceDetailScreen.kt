package io.github.garyquinn.kmpble.sample

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
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.garyquinn.kmpble.bonding.BondState
import io.github.garyquinn.kmpble.connection.BondingPreference
import io.github.garyquinn.kmpble.connection.ConnectionOptions
import io.github.garyquinn.kmpble.connection.ReconnectionStrategy
import io.github.garyquinn.kmpble.connection.State
import io.github.garyquinn.kmpble.gatt.Characteristic
import io.github.garyquinn.kmpble.gatt.DiscoveredService
import io.github.garyquinn.kmpble.gatt.Observation
import io.github.garyquinn.kmpble.gatt.WriteType
import io.github.garyquinn.kmpble.scanner.Advertisement
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    advertisement: Advertisement,
    onBack: () -> Unit,
) {
    // ViewModel is scoped to this composable's lifecycle.
    // When navigating back, onCleared() fires → peripheral.close().
    val vm = viewModel { BleViewModel(advertisement) }

    val state by vm.connectionState.collectAsState()
    val bond by vm.bondState.collectAsState()
    val services by vm.services.collectAsState()
    val rssi by vm.rssi.collectAsState()
    val mtu by vm.mtu.collectAsState()
    val maxWriteLen by vm.maximumWriteValueLength.collectAsState()
    val error by vm.error.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
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
            // Connection section
            item { ConnectionSection(state, bond, vm) }

            // Info section
            item { InfoSection(rssi, mtu, maxWriteLen, vm) }

            // Services section
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionSection(state: State, bond: BondState, vm: BleViewModel) {
    var autoConnect by remember { mutableStateOf(false) }
    var useReconnection by remember { mutableStateOf(false) }
    var bondingPref by remember { mutableStateOf(BondingPreference.IfRequired) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Connection", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            // State chip
            FilterChip(
                selected = state is State.Connected,
                onClick = {},
                label = { Text(stateLabel(state)) },
            )

            Spacer(Modifier.height(8.dp))

            // Connection options
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

            // Bonding preference chips
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

            Spacer(Modifier.height(8.dp))

            // Connect/Disconnect buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        vm.connect(
                            ConnectionOptions(
                                autoConnect = autoConnect,
                                bondingPreference = bondingPref,
                                reconnectionStrategy = if (useReconnection) {
                                    ReconnectionStrategy.ExponentialBackoff()
                                } else {
                                    ReconnectionStrategy.None
                                },
                            )
                        )
                    },
                    enabled = state is State.Disconnected,
                ) { Text("Connect") }

                OutlinedButton(
                    onClick = { vm.disconnect() },
                    enabled = state is State.Connected || state is State.Connecting,
                ) { Text("Disconnect") }
            }

            // Bond state
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
    var observedValue by remember { mutableStateOf<String?>(null) }
    var isObserving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column {
        Text(
            text = characteristic.uuid.toString().take(8) + "...",
            style = MaterialTheme.typography.bodyMedium,
        )

        // Property badges
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (props.read) PropertyBadge("R")
            if (props.write) PropertyBadge("W")
            if (props.writeWithoutResponse) PropertyBadge("WnR")
            if (props.notify) PropertyBadge("N")
            if (props.indicate) PropertyBadge("I")
        }

        Spacer(Modifier.height(4.dp))

        // Read
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

        // Write
        if (props.write || props.writeWithoutResponse) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = writeInput,
                    onValueChange = { writeInput = it },
                    label = { Text("Hex") },
                    modifier = Modifier.width(120.dp),
                    singleLine = true,
                )
                OutlinedButton(
                    onClick = {
                        try {
                            val bytes = writeInput.hexToByteArray()
                            val type = if (props.write) WriteType.WithResponse else WriteType.WithoutResponse
                            vm.writeCharacteristic(characteristic, bytes, type)
                        } catch (_: Exception) { }
                    },
                ) { Text("Write") }
            }
        }

        // Observe
        if (props.notify || props.indicate) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Observe", style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = isObserving,
                    onCheckedChange = { enabled ->
                        isObserving = enabled
                        if (enabled) {
                            scope.launch {
                                vm.observe(characteristic).collect { observation ->
                                    observedValue = when (observation) {
                                        is Observation.Value -> observation.data.toHexString()
                                        is Observation.Disconnected -> "Disconnected"
                                    }
                                }
                            }
                        }
                    },
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
