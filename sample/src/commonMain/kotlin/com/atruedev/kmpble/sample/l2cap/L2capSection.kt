package com.atruedev.kmpble.sample.l2cap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.atruedev.kmpble.sample.detail.ValueFormatter

@Composable
internal fun L2capSection(
    l2capState: L2capState,
    messages: List<L2capMessage>,
    onOpenChannel: (Int) -> Unit,
    onSend: (ByteArray) -> Unit,
    onCloseChannel: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("L2CAP Channel", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            when (l2capState) {
                L2capState.Closed -> ClosedContent(onOpenChannel = onOpenChannel)
                L2capState.Opening -> Text("Opening channel...", style = MaterialTheme.typography.bodySmall)
                is L2capState.Open ->
                    OpenContent(
                        psm = l2capState.psm,
                        messages = messages,
                        onSend = onSend,
                        onClose = onCloseChannel,
                    )
                is L2capState.Error -> {
                    Text(
                        l2capState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ClosedContent(onOpenChannel = onOpenChannel)
                }
            }
        }
    }
}

private enum class PsmPreset(
    val label: String,
    val psm: Int,
) {
    CoC(label = "CoC (128)", psm = 128),
    DFU(label = "DFU (192)", psm = 192),
    Custom(label = "Custom", psm = 0),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClosedContent(onOpenChannel: (Int) -> Unit) {
    var psmText by remember { mutableStateOf("128") }

    Text("Common PSMs", style = MaterialTheme.typography.labelSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PsmPreset.entries.filter { it != PsmPreset.Custom }.forEach { preset ->
            FilterChip(
                selected = psmText == preset.psm.toString(),
                onClick = { psmText = preset.psm.toString() },
                label = { Text(preset.label) },
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = psmText,
            onValueChange = { psmText = it.filter { c -> c.isDigit() } },
            label = { Text("PSM") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        OutlinedButton(
            onClick = { psmText.toIntOrNull()?.let(onOpenChannel) },
            enabled = psmText.toIntOrNull() != null,
        ) {
            Text("Open")
        }
    }
}

@Composable
private fun OpenContent(
    psm: Int,
    messages: List<L2capMessage>,
    onSend: (ByteArray) -> Unit,
    onClose: () -> Unit,
) {
    var sendText by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("PSM: $psm", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onClose) {
            Text("Close", style = MaterialTheme.typography.labelSmall)
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = sendText,
            onValueChange = { sendText = it },
            label = { Text("Hex data") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        OutlinedButton(
            onClick = {
                ValueFormatter.parseHex(sendText)?.let { bytes ->
                    onSend(bytes)
                    sendText = ""
                }
            },
            enabled = ValueFormatter.parseHex(sendText) != null,
        ) {
            Text("Send")
        }
    }

    if (messages.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text("Messages:", style = MaterialTheme.typography.labelSmall)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            messages.forEach { msg ->
                val prefix = if (msg.direction == L2capMessage.Direction.SENT) "TX" else "RX"
                Text(
                    "[$prefix] ${ValueFormatter.formatHex(msg.data)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }
    }
}
