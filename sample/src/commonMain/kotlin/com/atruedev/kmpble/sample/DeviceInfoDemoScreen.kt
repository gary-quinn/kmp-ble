package com.atruedev.kmpble.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.profiles.deviceinfo.DeviceInformation
import com.atruedev.kmpble.scanner.Advertisement
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoDemoScreen(
    advertisement: Advertisement,
    onBack: () -> Unit,
) {
    val vm = viewModel { BleViewModel(advertisement) }
    val state by vm.connectionState.collectAsState()
    val isConnected = state is State.Connected

    var deviceInfo by remember { mutableStateOf<DeviceInformation?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isConnected) {
        if (!isConnected || deviceInfo != null) return@LaunchedEffect
        try {
            deviceInfo = vm.readDeviceInformation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Failed to read device information"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Information") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("<", style = MaterialTheme.typography.titleLarge)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            deviceInfo?.let { info ->
                DeviceInfoCard(info)
            }

            if (deviceInfo == null && isConnected && error == null) {
                Text(
                    "Reading device information...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    vm.launchWithErrorHandling {
                        deviceInfo = vm.readDeviceInformation()
                        error = null
                    }
                },
                enabled = isConnected,
            ) {
                Text("Refresh")
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Uses DeviceInformationProfile to read DIS characteristics",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state !is State.Connected && state !is State.Connecting) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Connect to read device information.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeviceInfoCard(info: DeviceInformation) {
    val fields =
        buildList {
            info.manufacturerName?.let { add("Manufacturer" to it) }
            info.modelNumber?.let { add("Model" to it) }
            info.serialNumber?.let { add("Serial Number" to it) }
            info.hardwareRevision?.let { add("Hardware Rev" to it) }
            info.firmwareRevision?.let { add("Firmware Rev" to it) }
            info.softwareRevision?.let { add("Software Rev" to it) }
            info.systemId?.let {
                val mfg = it.manufacturerIdentifier.toString(16)
                val oui = it.organizationallyUniqueIdentifier.toString(16)
                add("System ID" to "MFG: 0x$mfg, OUI: 0x$oui")
            }
            info.pnpId?.let {
                val vendor = it.vendorId.toString(16)
                val product = it.productId.toString(16)
                val ver = it.productVersion.toString(16)
                add("PnP ID" to "Vendor: 0x$vendor, Product: 0x$product, Ver: 0x$ver")
            }
        }

    if (fields.isEmpty()) {
        Text(
            "Device Information service present but no characteristics readable.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Device Information", style = MaterialTheme.typography.titleSmall)
            for ((label, value) in fields) {
                Column {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
