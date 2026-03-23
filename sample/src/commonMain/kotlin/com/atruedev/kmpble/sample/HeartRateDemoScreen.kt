package com.atruedev.kmpble.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.atruedev.kmpble.profiles.heartrate.BodySensorLocation
import com.atruedev.kmpble.profiles.heartrate.HeartRateMeasurement
import com.atruedev.kmpble.scanner.Advertisement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartRateDemoScreen(
    advertisement: Advertisement,
    onBack: () -> Unit,
) {
    val vm = viewModel(key = advertisement.identifier.value) { BleViewModel(advertisement) }
    val state by vm.connectionState.collectAsState()
    val isConnected = state is State.Connected

    var latestMeasurement by remember { mutableStateOf<HeartRateMeasurement?>(null) }
    var sensorLocation by remember { mutableStateOf<BodySensorLocation?>(null) }
    var recentValues by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(isConnected) {
        if (!isConnected) return@LaunchedEffect
        latestMeasurement = null
        sensorLocation = null
        recentValues = emptyList()
        sensorLocation = vm.readBodySensorLocation()
        vm.heartRateMeasurements().collect { measurement ->
            latestMeasurement = measurement
            val detail =
                buildString {
                    append("${measurement.heartRate} bpm")
                    if (measurement.rrIntervals.isNotEmpty()) {
                        append(" | RR: ${measurement.rrIntervals.joinToString()}ms")
                    }
                    measurement.energyExpended?.let { append(" | ${it}kJ") }
                }
            recentValues = (listOf(detail) + recentValues).take(20)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Heart Rate Monitor") },
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

            ConnectionStatusPill(state)

            Spacer(Modifier.height(32.dp))

            Text(
                text = latestMeasurement?.heartRate?.toString() ?: "—",
                fontSize = 96.sp,
                fontWeight = FontWeight.Thin,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "BPM",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                sensorLocation?.let {
                    Text(
                        text = "Sensor: ${it.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                latestMeasurement?.sensorContactDetected?.let { detected ->
                    Text(
                        text = if (detected) "Contact: Yes" else "Contact: No",
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (detected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Uses HeartRateProfile with transparent reconnection",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))

            if (recentValues.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Recent Values", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (value in recentValues) {
                                Text(value, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            if (latestMeasurement == null && isConnected) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "No Heart Rate service found on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatusPill(state: State) {
    val (label, isConnected) =
        when (state) {
            is State.Connected -> "Connected" to true
            is State.Connecting -> "Connecting..." to false
            is State.Disconnecting -> "Disconnecting..." to false
            is State.Disconnected -> "Disconnected" to false
        }
    FilterChip(
        selected = isConnected,
        onClick = {},
        label = { Text(label) },
    )
}
