package com.atruedev.kmpble.sample.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atruedev.kmpble.adapter.BluetoothAdapterState
import kotlin.uuid.ExperimentalUuidApi

@Composable
internal fun ScanToggleButton(
    scanState: ScanState,
    adapterState: BluetoothAdapterState,
    onToggle: () -> Unit,
) {
    TextButton(
        onClick = onToggle,
        enabled = adapterState == BluetoothAdapterState.On,
    ) {
        Text(
            when (scanState) {
                ScanState.Scanning -> "Stop"
                else -> "Scan"
            },
        )
    }
}

@Composable
internal fun SortModeSelector(
    currentMode: SortMode,
    onModeSelected: (SortMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text(
                when (currentMode) {
                    SortMode.RSSI -> "RSSI"
                    SortMode.NAME -> "Name"
                    SortMode.LAST_SEEN -> "Recent"
                },
                style = MaterialTheme.typography.labelMedium,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (mode) {
                                SortMode.RSSI -> "Sort by RSSI"
                                SortMode.NAME -> "Sort by Name"
                                SortMode.LAST_SEEN -> "Sort by Recent"
                            },
                        )
                    },
                    onClick = {
                        onModeSelected(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
internal fun FilterBar(
    filters: ScanFilters,
    onFiltersChanged: (ScanFilters) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = filters.nameQuery,
            onValueChange = { onFiltersChanged(filters.copy(nameQuery = it)) },
            label = { Text("Filter by name or ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Min RSSI: ${filters.minRssi} dBm",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(130.dp),
            )
            Slider(
                value = filters.minRssi.toFloat(),
                onValueChange = { onFiltersChanged(filters.copy(minRssi = it.toInt())) },
                valueRange = -100f..-30f,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Hide unnamed", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = filters.hideUnnamed,
                onCheckedChange = { onFiltersChanged(filters.copy(hideUnnamed = it)) },
            )
        }
    }
}
