package com.atruedev.kmpble.sample

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.atruedev.kmpble.ServiceUuid
import com.atruedev.kmpble.bonding.BondState
import com.atruedev.kmpble.bonding.PairingEvent
import com.atruedev.kmpble.bonding.PairingResponse
import com.atruedev.kmpble.connection.BondingPreference
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.ConnectionRecipe
import com.atruedev.kmpble.connection.ReconnectionStrategy
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.scanner.Advertisement
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalBleApi::class, ExperimentalUuidApi::class)
@Composable
fun DeviceDetailScreen(
    advertisement: Advertisement,
    onBack: () -> Unit,
    onExploreServices: () -> Unit,
    onHeartRateDemo: () -> Unit,
    onBatteryDemo: () -> Unit,
    onDeviceInfoDemo: () -> Unit,
    onDfuDemo: () -> Unit,
    onCodecDemo: () -> Unit,
) {
    val vm = viewModel(key = advertisement.identifier.value) { BleViewModel(advertisement) }

    val state by vm.connectionState.collectAsState()
    val bond by vm.bondState.collectAsState()
    val services by vm.services.collectAsState()
    val rssi by vm.rssi.collectAsState()
    val mtu by vm.mtu.collectAsState()
    val maxWriteLen by vm.maximumWriteValueLength.collectAsState()
    val error by vm.error.collectAsState()
    val pairingEvent by vm.pairing.event.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    val isConnected = state is State.Connected
    val hasHeartRate = services?.any { it.uuid == ServiceUuid.HEART_RATE } == true
    val hasBattery = services?.any { it.uuid == ServiceUuid.BATTERY } == true
    val hasDeviceInfo = services?.any { it.uuid == ServiceUuid.DEVICE_INFORMATION } == true

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
                    IconButton(onClick = {
                        vm.releaseConnection()
                        onBack()
                    }) {
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

            if (isConnected) {
                item { InfoSection(rssi, mtu, maxWriteLen, vm) }
            }

            item {
                AnimatedVisibility(isConnected) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        NavigationCard(
                            title = "Explore Services",
                            description = "Browse GATT tree, read/write characteristics, benchmarks, L2CAP",
                            onClick = onExploreServices,
                        )

                        if (hasHeartRate) {
                            NavigationCard(
                                title = "Heart Rate Monitor",
                                description = "Live BPM with transparent reconnection via HeartRateProfile",
                                onClick = onHeartRateDemo,
                                highlight = true,
                            )
                        }

                        if (hasBattery) {
                            NavigationCard(
                                title = "Battery Level",
                                description = "Read and subscribe to battery level via BatteryProfile",
                                onClick = onBatteryDemo,
                            )
                        }

                        if (hasDeviceInfo) {
                            NavigationCard(
                                title = "Device Information",
                                description = "Read DIS characteristics via DeviceInformationProfile",
                                onClick = onDeviceInfoDemo,
                            )
                        }

                        NavigationCard(
                            title = "Firmware Update (DFU)",
                            description = "Nordic Secure DFU v2 with progress tracking",
                            onClick = onDfuDemo,
                        )

                        NavigationCard(
                            title = "Codec Examples",
                            description = "Typed read/write with BleCodec instead of raw bytes",
                            onClick = onCodecDemo,
                        )
                    }
                }
            }

            if (!isConnected) {
                item {
                    Text(
                        "Connect to explore this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun NavigationCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    highlight: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors =
            if (highlight) {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            } else {
                CardDefaults.cardColors()
            },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (highlight) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

@Stable
@OptIn(ExperimentalBleApi::class)
private sealed interface RecipeOption {
    val label: String

    data object Custom : RecipeOption {
        override val label = "Custom"
    }

    data class Preset(override val label: String, val options: ConnectionOptions) : RecipeOption

    companion object {
        val entries =
            listOf(
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
private fun ConnectionSection(
    state: State,
    bond: BondState,
    vm: BleViewModel,
) {
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
                        val baseOptions =
                            when (val recipe = selectedRecipe) {
                                is RecipeOption.Custom ->
                                    ConnectionOptions(
                                        autoConnect = autoConnect,
                                        bondingPreference = bondingPref,
                                        reconnectionStrategy =
                                            if (useReconnection) {
                                                ReconnectionStrategy.ExponentialBackoff()
                                            } else {
                                                ReconnectionStrategy.None
                                            },
                                    )
                                is RecipeOption.Preset -> recipe.options
                            }
                        vm.connect(baseOptions)
                    },
                    enabled = state is State.Disconnected || state is State.Disconnecting.Error,
                ) { Text("Connect") }

                OutlinedButton(
                    onClick = { vm.disconnect() },
                    enabled =
                        state is State.Connected ||
                            state is State.Connecting ||
                            state is State.Disconnecting.Error,
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
private fun InfoSection(
    rssi: Int?,
    mtu: Int,
    maxWriteLen: Int,
    vm: BleViewModel,
) {
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

@OptIn(ExperimentalBleApi::class)
@Composable
private fun PairingDialog(
    event: PairingEvent,
    onRespond: (PairingResponse) -> Unit,
) {
    var pinInput by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Pairing Request", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            when (event) {
                is PairingEvent.NumericComparison -> {
                    Text("Confirm the number matches: ${event.numericValue}")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onRespond(PairingResponse.Confirm(true)) }) { Text("Confirm") }
                        OutlinedButton(onClick = { onRespond(PairingResponse.Confirm(false)) }) { Text("Reject") }
                    }
                }
                is PairingEvent.PasskeyRequest -> {
                    Text("Enter the passkey:")
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it },
                        label = { Text("PIN") },
                        singleLine = true,
                        modifier = Modifier.width(120.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { pinInput.toIntOrNull()?.let { onRespond(PairingResponse.ProvidePin(it)) } }) {
                        Text("Submit")
                    }
                }
                is PairingEvent.PasskeyNotification -> {
                    Text("Passkey displayed on device: ${event.passkey}")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onRespond(PairingResponse.Confirm(true)) }) { Text("OK") }
                }
                is PairingEvent.JustWorksConfirmation -> {
                    Text("Allow pairing with this device?")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onRespond(PairingResponse.Confirm(true)) }) { Text("Allow") }
                        OutlinedButton(onClick = { onRespond(PairingResponse.Confirm(false)) }) { Text("Deny") }
                    }
                }
                is PairingEvent.OutOfBandDataRequest -> {
                    Text("OOB pairing requested.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onRespond(PairingResponse.ProvideOobData(ByteArray(16))) }) {
                        Text("Provide OOB Data")
                    }
                }
            }
        }
    }
}

private fun stateLabel(state: State): String =
    when (state) {
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

private fun bondLabel(state: BondState): String =
    when (state) {
        BondState.NotBonded -> "Not bonded"
        BondState.Bonding -> "Bonding..."
        BondState.Bonded -> "Bonded"
        BondState.Unknown -> "Unknown"
    }
