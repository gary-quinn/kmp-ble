package com.atruedev.kmpble.sample.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
@Composable
internal fun ServiceList(
    services: List<ServiceUiModel>,
    onToggleService: (Int) -> Unit,
    onToggleCharacteristic: (Int, Int) -> Unit,
    onRead: (Int, Int) -> Unit,
    onWrite: (Int, Int) -> Unit,
    onToggleNotify: (Int, Int) -> Unit,
    onFormatChange: (Int, Int, DisplayFormat) -> Unit,
    onDismissError: (Int, Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        services.forEachIndexed { serviceIndex, service ->
            item(key = "service_${service.uuid}") {
                ServiceHeader(
                    service = service,
                    onClick = { onToggleService(serviceIndex) },
                )
            }

            if (service.isExpanded) {
                service.characteristics.forEachIndexed { charIndex, char ->
                    item(key = "char_${service.uuid}_${char.uuid}") {
                        CharacteristicItem(
                            characteristic = char,
                            onToggle = { onToggleCharacteristic(serviceIndex, charIndex) },
                            onRead = { onRead(serviceIndex, charIndex) },
                            onWrite = { onWrite(serviceIndex, charIndex) },
                            onToggleNotify = { onToggleNotify(serviceIndex, charIndex) },
                            onFormatChange = { onFormatChange(serviceIndex, charIndex, it) },
                            onDismissError = { onDismissError(serviceIndex, charIndex) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun ServiceHeader(
    service: ServiceUiModel,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clickable(onClick = onClick),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (service.isExpanded) "▾" else "▸",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(16.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(service.displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    service.uuid.toString(),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${service.characteristics.size} chars",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
