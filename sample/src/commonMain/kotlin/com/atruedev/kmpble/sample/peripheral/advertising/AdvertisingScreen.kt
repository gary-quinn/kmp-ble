package com.atruedev.kmpble.sample.peripheral.advertising

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atruedev.kmpble.server.AdvertiseMode
import com.atruedev.kmpble.server.AdvertiseTxPower

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdvertisingScreen(viewModel: AdvertisingViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Advertising", style = MaterialTheme.typography.titleSmall)
            when (state.state) {
                AdvertisingState.Stopped -> Button(onClick = viewModel::startAdvertising) { Text("Start") }
                AdvertisingState.Advertising -> OutlinedButton(onClick = viewModel::stopAdvertising) { Text("Stop") }
                is AdvertisingState.Error -> Button(onClick = viewModel::startAdvertising) { Text("Retry") }
            }
        }

        if (state.state is AdvertisingState.Error) {
            Text(
                (state.state as AdvertisingState.Error).message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = state.localName,
            onValueChange = viewModel::setLocalName,
            label = { Text("Local Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = state.state !is AdvertisingState.Advertising,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Connectable", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Switch(
                checked = state.connectable,
                onCheckedChange = { viewModel.toggleConnectable() },
                enabled = state.state !is AdvertisingState.Advertising,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("Mode", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdvertiseMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.mode == mode,
                    onClick = { viewModel.setMode(mode) },
                    label = { Text(mode.name) },
                    enabled = state.state !is AdvertisingState.Advertising,
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text("TX Power", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdvertiseTxPower.entries.forEach { power ->
                FilterChip(
                    selected = state.txPower == power,
                    onClick = { viewModel.setTxPower(power) },
                    label = { Text(power.name) },
                    enabled = state.state !is AdvertisingState.Advertising,
                )
            }
        }
    }
}
