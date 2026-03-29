package com.atruedev.kmpble.sample.peripheral.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GattServerScreen(viewModel: GattServerViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.padding(8.dp)) {
        ServerControls(
            serverState = state.state,
            onStart = viewModel::startServer,
            onStop = viewModel::stopServer,
        )

        val errorMessage = (state.state as? ServerState.Error)?.message ?: state.error
        if (errorMessage != null) {
            Text(
                errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        if (state.state is ServerState.Stopped && state.services.isEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Quick Start", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ServerPreset.entries.forEach { preset ->
                    OutlinedButton(onClick = { viewModel.loadPreset(preset) }) {
                        Text(preset.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (state.state is ServerState.Running) {
            Text(
                "Server is hosting services. Start advertising below so other devices can discover it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        state.services.forEachIndexed { serviceIndex, service ->
            ServiceConfigCard(
                service = service,
                serviceIndex = serviceIndex,
                onUuidChange = { viewModel.updateServiceUuid(serviceIndex, it) },
                onAddChar = { viewModel.addCharacteristic(serviceIndex) },
                onRemove = { viewModel.removeService(serviceIndex) },
                onCharUuidChange = { ci, uuid -> viewModel.updateCharUuid(serviceIndex, ci, uuid) },
                onToggleProperty = { ci, prop -> viewModel.toggleCharProperty(serviceIndex, ci, prop) },
                enabled = state.state is ServerState.Stopped,
            )
        }

        if (state.state is ServerState.Stopped) {
            TextButton(onClick = viewModel::addService) {
                Text("+ Add Service")
            }
        }
    }
}

@Composable
private fun ServerControls(
    serverState: ServerState,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("GATT Server", style = MaterialTheme.typography.titleSmall)
        when (serverState) {
            ServerState.Stopped -> Button(onClick = onStart) { Text("Start") }
            ServerState.Running -> OutlinedButton(onClick = onStop) { Text("Stop") }
            is ServerState.Error -> Button(onClick = onStart) { Text("Retry") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceConfigCard(
    service: ServerServiceConfig,
    serviceIndex: Int,
    onUuidChange: (String) -> Unit,
    onAddChar: () -> Unit,
    onRemove: () -> Unit,
    onCharUuidChange: (Int, String) -> Unit,
    onToggleProperty: (Int, CharProperty) -> Unit,
    enabled: Boolean,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Service ${serviceIndex + 1}", style = MaterialTheme.typography.labelMedium)
                if (enabled) {
                    TextButton(onClick = onRemove) { Text("Remove") }
                }
            }

            OutlinedTextField(
                value = service.uuid,
                onValueChange = onUuidChange,
                label = { Text("Service UUID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()

            service.characteristics.forEachIndexed { charIndex, char ->
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = char.uuid,
                    onValueChange = { onCharUuidChange(charIndex, it) },
                    label = { Text(char.label.ifEmpty { "Char ${charIndex + 1} UUID" }) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = enabled,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CharProperty.entries.forEach { prop ->
                        FilterChip(
                            selected = prop in char.properties,
                            onClick = { onToggleProperty(charIndex, prop) },
                            label = { Text(prop.name) },
                            enabled = enabled,
                        )
                    }
                }
            }

            if (enabled) {
                TextButton(onClick = onAddChar) { Text("+ Add Characteristic") }
            }
        }
    }
}
