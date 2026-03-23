package com.atruedev.kmpble.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atruedev.kmpble.ServiceUuid
import com.atruedev.kmpble.codec.BleDecoder
import com.atruedev.kmpble.codec.map
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.scanner.uuidFrom
import kotlin.uuid.ExperimentalUuidApi

private val BatteryPercentDecoder =
    BleDecoder<Int> { data ->
        if (data.isEmpty()) 0 else data[0].toInt() and 0xFF
    }

private val Utf8StringDecoder = BleDecoder<String> { data -> data.decodeToString() }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
fun CodecDemoScreen(
    advertisement: Advertisement,
    onBack: () -> Unit,
) {
    val vm = viewModel(key = advertisement.identifier.value) { BleViewModel(advertisement) }
    val state by vm.connectionState.collectAsState()
    val isConnected = state is State.Connected

    var results by remember { mutableStateOf(emptyList<String>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Codec Examples") },
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
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("BleCodec / BleDecoder", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Read characteristics as typed values instead of raw bytes. " +
                                "Decoders compose via map/contramap/bimap.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Typed Read", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    vm.launchWithErrorHandling {
                                        val char =
                                            vm.findCharacteristic(
                                                ServiceUuid.BATTERY,
                                                uuidFrom("2A19"),
                                            )
                                        if (char != null) {
                                            val level = vm.readTyped(char, BatteryPercentDecoder)
                                            results = listOf("Battery (codec): $level%") + results
                                        } else {
                                            results = listOf("Battery service not found") + results
                                        }
                                    }
                                },
                                enabled = isConnected,
                            ) { Text("Read Battery") }

                            OutlinedButton(
                                onClick = {
                                    vm.launchWithErrorHandling {
                                        val char =
                                            vm.findCharacteristic(
                                                ServiceUuid.DEVICE_INFORMATION,
                                                uuidFrom("2A29"),
                                            )
                                        if (char != null) {
                                            val name = vm.readTyped(char, Utf8StringDecoder)
                                            results = listOf("Manufacturer (codec): $name") + results
                                        } else {
                                            results = listOf("DIS not found") + results
                                        }
                                    }
                                },
                                enabled = isConnected,
                            ) { Text("Read Manufacturer") }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Decoder Composition", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "BleDecoder<Int>.map { \"${'$'}it%\" } -> BleDecoder<String>",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))

                        val formattedDecoder = remember { BatteryPercentDecoder.map { "$it%" } }

                        OutlinedButton(
                            onClick = {
                                vm.launchWithErrorHandling {
                                    val char =
                                        vm.findCharacteristic(
                                            ServiceUuid.BATTERY,
                                            uuidFrom("2A19"),
                                        )
                                    if (char != null) {
                                        val formatted = vm.readTyped(char, formattedDecoder)
                                        results = listOf("Battery (mapped): $formatted") + results
                                    } else {
                                        results = listOf("Battery service not found") + results
                                    }
                                }
                            },
                            enabled = isConnected,
                        ) { Text("Read Mapped") }
                    }
                }
            }

            if (results.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Results", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            for (result in results.take(20)) {
                                Text(result, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            if (!isConnected && state !is State.Connecting) {
                item {
                    Text(
                        "Connect to try typed reads with BleCodec.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
