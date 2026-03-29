package com.atruedev.kmpble.sample.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun CharacteristicItem(
    characteristic: CharacteristicUiModel,
    onToggle: () -> Unit,
    onRead: () -> Unit,
    onWrite: () -> Unit,
    onToggleNotify: () -> Unit,
    onFormatChange: (DisplayFormat) -> Unit,
    onDismissError: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 8.dp, top = 1.dp, bottom = 1.dp)
                .clickable(onClick = onToggle),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            CharacteristicHeader(characteristic)

            AnimatedVisibility(
                visible = characteristic.isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                CharacteristicExpandedContent(
                    characteristic = characteristic,
                    onRead = onRead,
                    onWrite = onWrite,
                    onToggleNotify = onToggleNotify,
                    onFormatChange = onFormatChange,
                    onDismissError = onDismissError,
                )
            }
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun CharacteristicHeader(characteristic: CharacteristicUiModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                characteristic.displayName,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                characteristic.uuid.toString(),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PropertyChips(properties = characteristic.properties)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharacteristicExpandedContent(
    characteristic: CharacteristicUiModel,
    onRead: () -> Unit,
    onWrite: () -> Unit,
    onToggleNotify: () -> Unit,
    onFormatChange: (DisplayFormat) -> Unit,
    onDismissError: () -> Unit,
) {
    Column {
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        OperationButtons(
            properties = characteristic.properties,
            isNotifying = characteristic.isNotifying,
            onRead = onRead,
            onWrite = onWrite,
            onToggleNotify = onToggleNotify,
        )

        if (characteristic.lastReadValue != null || characteristic.notificationValues.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            FormatSelector(currentFormat = characteristic.displayFormat, onFormatChange = onFormatChange)
        }

        if (characteristic.lastReadValue != null) {
            Spacer(modifier = Modifier.height(4.dp))
            ValueDisplay(value = characteristic.lastReadValue, format = characteristic.displayFormat)
        }

        if (characteristic.notificationValues.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            NotificationLog(values = characteristic.notificationValues, format = characteristic.displayFormat)
        }

        if (characteristic.error != null) {
            Spacer(modifier = Modifier.height(4.dp))
            InlineError(message = characteristic.error, onDismiss = onDismissError)
        }

        if (characteristic.descriptors.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            DescriptorList(descriptors = characteristic.descriptors)
        }
    }
}

@Composable
private fun OperationButtons(
    properties: com.atruedev.kmpble.gatt.Characteristic.Properties,
    isNotifying: Boolean,
    onRead: () -> Unit,
    onWrite: () -> Unit,
    onToggleNotify: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (properties.read) {
            OutlinedButton(onClick = onRead) { Text("Read", style = MaterialTheme.typography.labelSmall) }
        }
        if (properties.write || properties.writeWithoutResponse) {
            OutlinedButton(onClick = onWrite) { Text("Write", style = MaterialTheme.typography.labelSmall) }
        }
        if (properties.notify || properties.indicate) {
            OutlinedButton(onClick = onToggleNotify) {
                Text(
                    if (isNotifying) "Unsubscribe" else "Subscribe",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
