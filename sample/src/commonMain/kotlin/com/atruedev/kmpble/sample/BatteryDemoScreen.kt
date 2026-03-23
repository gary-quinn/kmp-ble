package com.atruedev.kmpble.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.scanner.Advertisement

private enum class ServicePresence { Unknown, Found, Absent }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryDemoScreen(
    advertisement: Advertisement,
    onBack: () -> Unit,
) {
    val vm = viewModel { BleViewModel(advertisement) }
    val state by vm.connectionState.collectAsState()
    val isConnected = state is State.Connected

    var batteryLevel by remember { mutableStateOf<Int?>(null) }
    var subscribedLevel by remember { mutableStateOf<Int?>(null) }
    var servicePresence by remember { mutableStateOf(ServicePresence.Unknown) }

    LaunchedEffect(isConnected) {
        if (!isConnected) return@LaunchedEffect
        val level = vm.readBatteryLevel()
        if (level != null) {
            batteryLevel = level
            servicePresence = ServicePresence.Found
            vm.batteryLevelNotifications().collect { subscribedLevel = it }
        } else {
            servicePresence = ServicePresence.Absent
        }
    }

    val displayLevel = subscribedLevel ?: batteryLevel

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Battery Level") },
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
            Spacer(Modifier.height(32.dp))

            Text(
                text = displayLevel?.let { "$it%" } ?: "—",
                fontSize = 72.sp,
                fontWeight = FontWeight.Thin,
                color = batteryColor(displayLevel),
            )

            Spacer(Modifier.height(16.dp))

            displayLevel?.let { level ->
                LinearProgressIndicator(
                    progress = { level / 100f },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    color = batteryColor(level),
                )
            }

            Spacer(Modifier.height(24.dp))

            OutlinedButton(
                onClick = {
                    vm.launchWithErrorHandling {
                        vm.readBatteryLevel()?.let { batteryLevel = it }
                    }
                },
                enabled = isConnected,
            ) {
                Text("Read Battery")
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Uses BatteryProfile for read and notifications",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (servicePresence == ServicePresence.Absent && isConnected) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "No Battery service found on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun batteryColor(level: Int?) =
    when {
        level == null -> MaterialTheme.colorScheme.onSurfaceVariant
        level > 50 -> MaterialTheme.colorScheme.primary
        level > 20 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
