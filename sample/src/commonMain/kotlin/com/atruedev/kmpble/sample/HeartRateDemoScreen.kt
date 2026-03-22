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
import com.atruedev.kmpble.ServiceUuid
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.scanner.uuidFrom
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
private val HR_MEASUREMENT = uuidFrom("2A37")

private fun parseHeartRate(data: ByteArray): Int? {
    if (data.isEmpty()) return null
    val flags = data[0].toInt() and 0xFF
    return if (flags and 0x01 == 0) {
        if (data.size > 1) data[1].toInt() and 0xFF else null
    } else {
        if (data.size > 2) ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF) else null
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
fun HeartRateDemoScreen(
    advertisement: Advertisement,
    onBack: () -> Unit,
) {
    val vm = viewModel { BleViewModel(advertisement) }
    val state by vm.connectionState.collectAsState()
    val services by vm.services.collectAsState()

    var bpm by remember { mutableStateOf<Int?>(null) }
    var recentValues by remember { mutableStateOf(emptyList<String>()) }

    val hrChar =
        remember(services) {
            services?.firstOrNull { it.uuid == ServiceUuid.HEART_RATE }
                ?.characteristics
                ?.firstOrNull { it.uuid == HR_MEASUREMENT }
        }

    if (hrChar != null) {
        LaunchedEffect(hrChar) {
            vm.observeValues(hrChar, BackpressureStrategy.Latest).collect { data ->
                parseHeartRate(data)?.let {
                    bpm = it
                    recentValues = (listOf("$it bpm") + recentValues).take(20)
                }
            }
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
                text = bpm?.toString() ?: "—",
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

            Text(
                text = "Uses observeValues() for transparent reconnection",
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

            if (hrChar == null && state is State.Connected) {
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
