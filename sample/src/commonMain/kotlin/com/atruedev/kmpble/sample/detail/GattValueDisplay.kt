package com.atruedev.kmpble.sample.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi

@Composable
internal fun ValueDisplay(
    value: ByteArray,
    format: DisplayFormat,
) {
    Text(
        text = "Value: ${ValueFormatter.format(value, format)}",
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp),
    )
}

@Composable
internal fun NotificationLog(
    values: List<TimestampedValue>,
    format: DisplayFormat,
) {
    Text("Notification Log:", style = MaterialTheme.typography.labelSmall)
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
        values.forEach { tv ->
            val elapsed = Clock.System.now() - tv.timestamp
            val timeStr =
                when {
                    elapsed.inWholeSeconds < 60 -> "${elapsed.inWholeSeconds}s"
                    else -> "${elapsed.inWholeMinutes}m"
                }
            Text(
                "[$timeStr] ${ValueFormatter.format(tv.value, format)}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}

@Composable
internal fun InlineError(
    message: String,
    onDismiss: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) { Text("Dismiss") }
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
internal fun DescriptorList(descriptors: List<DescriptorUiModel>) {
    Text("Descriptors:", style = MaterialTheme.typography.labelSmall)
    descriptors.forEach { desc ->
        Text(
            "${desc.displayName} (${desc.uuid.toString().take(8)})",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PropertyChips(properties: com.atruedev.kmpble.gatt.Characteristic.Properties) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (properties.read) PropertyChip("R", MaterialTheme.colorScheme.primary)
        if (properties.write) PropertyChip("W", Color(0xFF4CAF50))
        if (properties.writeWithoutResponse) PropertyChip("WNR", Color(0xFF8BC34A))
        if (properties.notify) PropertyChip("N", Color(0xFFFF9800))
        if (properties.indicate) PropertyChip("I", Color(0xFF9C27B0))
    }
}

@Composable
private fun PropertyChip(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier =
            Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(color)
                .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FormatSelector(
    currentFormat: DisplayFormat,
    onFormatChange: (DisplayFormat) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        DisplayFormat.entries.forEachIndexed { index, format ->
            SegmentedButton(
                selected = format == currentFormat,
                onClick = { onFormatChange(format) },
                shape = SegmentedButtonDefaults.itemShape(index, DisplayFormat.entries.size),
            ) {
                Text(format.name, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
