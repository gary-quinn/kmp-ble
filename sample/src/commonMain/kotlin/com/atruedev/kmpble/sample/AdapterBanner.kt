package com.atruedev.kmpble.sample

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.atruedev.kmpble.adapter.BluetoothAdapter
import com.atruedev.kmpble.adapter.BluetoothAdapterState

@Composable
fun AdapterBanner(adapter: BluetoothAdapter) {
    val state by adapter.state.collectAsState()

    val (message, color) = when (state) {
        BluetoothAdapterState.On -> null to Color.Transparent
        BluetoothAdapterState.Off -> "Bluetooth is off" to MaterialTheme.colorScheme.error
        BluetoothAdapterState.Unavailable -> "Bluetooth unavailable" to MaterialTheme.colorScheme.error
        BluetoothAdapterState.Unauthorized -> "Bluetooth unauthorized" to Color(0xFFFF9800)
        BluetoothAdapterState.Unsupported -> "BLE not supported" to MaterialTheme.colorScheme.error
    }

    AnimatedVisibility(visible = message != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(color)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = message ?: "",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
