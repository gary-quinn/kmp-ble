package com.atruedev.kmpble.sample.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
@Composable
internal fun DeviceList(
    devices: List<DiscoveredDevice>,
    onDeviceClick: (DiscoveredDevice) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(
            items = devices,
            key = { _, device -> device.identifier },
        ) { _, device ->
            DeviceCard(device = device, onClick = { onDeviceClick(device) })
        }
    }
}

@OptIn(ExperimentalUuidApi::class, ExperimentalLayoutApi::class)
@Composable
private fun DeviceCard(
    device: DiscoveredDevice,
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
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SignalStrengthIndicator(rssi = device.rssi)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color =
                        if (device.advertisement.name != null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        },
                )
                Text(
                    text = device.identifier,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (device.deviceCategory != DeviceCategory.UNKNOWN) {
                        CategoryChip(category = device.deviceCategory)
                    }
                    if (device.manufacturerName != null) {
                        SmallChip(text = device.manufacturerName)
                    }
                    if (device.serviceUuids.isNotEmpty()) {
                        SmallChip(text = "${device.serviceUuids.size} services")
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${device.rssi} dBm",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = rssiColor(device.rssi),
                )
                Text(
                    text = relativeTime(device.lastSeen),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SignalStrengthIndicator(rssi: Int) {
    val bars =
        when {
            rssi >= -50 -> 4
            rssi >= -65 -> 3
            rssi >= -80 -> 2
            rssi >= -90 -> 1
            else -> 0
        }
    Row(
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.size(20.dp, 16.dp),
    ) {
        for (i in 0 until 4) {
            val barHeight = (4 + i * 3).dp
            val isActive = i < bars
            Box(
                modifier =
                    Modifier
                        .width(3.dp)
                        .height(barHeight)
                        .align(Alignment.Bottom)
                        .clip(RoundedCornerShape(1.dp))
                        .background(
                            if (isActive) {
                                rssiColor(rssi)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                            },
                        ),
            )
        }
    }
}

@Composable
internal fun rssiColor(rssi: Int): Color =
    when {
        rssi >= -50 -> MaterialTheme.colorScheme.primary
        rssi >= -65 -> MaterialTheme.colorScheme.tertiary
        rssi >= -80 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }

@Composable
private fun CategoryChip(category: DeviceCategory) {
    val label =
        when (category) {
            DeviceCategory.HEART_RATE -> "Heart Rate"
            DeviceCategory.BATTERY -> "Battery"
            DeviceCategory.BLOOD_PRESSURE -> "Blood Pressure"
            DeviceCategory.GLUCOSE -> "Glucose"
            DeviceCategory.CYCLING -> "Cycling"
            DeviceCategory.DEVICE_INFO -> "Device Info"
            DeviceCategory.NORDIC_DK -> "Nordic DK"
            DeviceCategory.UNKNOWN -> return
        }
    SmallChip(text = label)
}

@Composable
internal fun SmallChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier =
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

internal fun relativeTime(instant: kotlin.time.Instant): String {
    val elapsed =
        kotlin.time.Clock.System
            .now() - instant
    return when {
        elapsed.inWholeSeconds < 5 -> "now"
        elapsed.inWholeSeconds < 60 -> "${elapsed.inWholeSeconds}s ago"
        elapsed.inWholeMinutes < 60 -> "${elapsed.inWholeMinutes}m ago"
        else -> "${elapsed.inWholeHours}h ago"
    }
}
