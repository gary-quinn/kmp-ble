package com.atruedev.kmpble.sample.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.atruedev.kmpble.bonding.BondState
import com.atruedev.kmpble.connection.State

@Composable
internal fun ConnectionStateIndicator(state: State) {
    val color =
        when (state) {
            is State.Connected -> Color(0xFF4CAF50)
            is State.Connecting -> Color(0xFFFFC107)
            is State.Disconnecting -> Color(0xFFFFC107)
            is State.Disconnected -> Color(0xFFF44336)
        }
    val label =
        when (state) {
            is State.Connected.Ready -> "Connected"
            is State.Connected -> "Connected"
            is State.Connecting -> "Connecting"
            is State.Disconnecting -> "Disconnecting"
            is State.Disconnected -> "Disconnected"
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun ConnectionControlBar(
    connectionState: State,
    rssi: Int?,
    mtu: Int?,
    bondState: BondState?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRequestMtu: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (connectionState) {
                    is State.Connected -> OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
                    is State.Disconnected -> Button(onClick = onConnect) { Text("Connect") }
                    else -> OutlinedButton(onClick = {}, enabled = false) { Text("Connecting...") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (rssi != null) InfoLabel(label = "RSSI", value = "$rssi dBm")
                    if (mtu != null) InfoLabel(label = "MTU", value = "$mtu")
                    if (bondState != null) {
                        InfoLabel(
                            label = "Bond",
                            value =
                                when (bondState) {
                                    is BondState.Bonded -> "Bonded"
                                    is BondState.Bonding -> "Bonding"
                                    is BondState.NotBonded -> "None"
                                    else -> "?"
                                },
                        )
                    }
                }
            }

            if (connectionState is State.Connected) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onRequestMtu) { Text("Request MTU") }
                }
            }
        }
    }
}

@Composable
private fun InfoLabel(
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
    }
}

@Composable
internal fun DisconnectedContent(onConnect: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Disconnected", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onConnect) { Text("Reconnect") }
    }
}

@Composable
internal fun ConnectingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Connecting...", style = MaterialTheme.typography.bodyLarge)
    }
}
