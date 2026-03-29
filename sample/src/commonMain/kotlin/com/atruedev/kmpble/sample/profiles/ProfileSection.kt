package com.atruedev.kmpble.sample.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ProfileSection(
    profileState: ProfileUiState,
    onStartHeartRate: () -> Unit,
    onStopProfile: (ProfileType) -> Unit,
    onReadBattery: () -> Unit,
    onStartBatteryNotifications: () -> Unit,
    onReadDeviceInfo: () -> Unit,
) {
    if (profileState.detectedProfiles.isEmpty()) return

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
            Text("Profiles", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            profileState.detectedProfiles.forEach { profile ->
                ProfileRow(
                    profile = profile,
                    reading = profileState.readings[profile.type],
                    isActive = profile.type in profileState.activeSubscriptions,
                    onStart = {
                        when (profile.type) {
                            ProfileType.HEART_RATE -> onStartHeartRate()
                            ProfileType.BATTERY -> onStartBatteryNotifications()
                            ProfileType.DEVICE_INFO -> onReadDeviceInfo()
                            else -> Unit
                        }
                    },
                    onStop = { onStopProfile(profile.type) },
                    onRead = {
                        when (profile.type) {
                            ProfileType.BATTERY -> onReadBattery()
                            ProfileType.DEVICE_INFO -> onReadDeviceInfo()
                            else -> Unit
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ProfileRow(
    profile: DetectedProfile,
    reading: ProfileData?,
    isActive: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRead: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(profileLabel(profile.type), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when (profile.type) {
                    ProfileType.HEART_RATE, ProfileType.BATTERY -> {
                        if (isActive) {
                            OutlinedButton(onClick = onStop) {
                                Text("Stop", style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            OutlinedButton(onClick = onStart) {
                                Text("Start", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    ProfileType.DEVICE_INFO -> {
                        OutlinedButton(onClick = onRead) {
                            Text("Read", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    else -> {
                        OutlinedButton(onClick = onStart) {
                            Text("Start", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        if (reading != null) {
            Text(
                formatReading(reading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun profileLabel(type: ProfileType): String =
    when (type) {
        ProfileType.HEART_RATE -> "Heart Rate"
        ProfileType.BATTERY -> "Battery"
        ProfileType.BLOOD_PRESSURE -> "Blood Pressure"
        ProfileType.GLUCOSE -> "Glucose"
        ProfileType.CSC -> "Cycling Speed & Cadence"
        ProfileType.DEVICE_INFO -> "Device Information"
    }

private fun formatReading(data: ProfileData): String =
    when (data) {
        is ProfileData.HeartRate ->
            "${data.bpm} BPM" +
                if (data.rrIntervals.isNotEmpty()) " (RR: ${data.rrIntervals.joinToString()})" else ""
        is ProfileData.Battery -> "${data.level}%"
        is ProfileData.DeviceInfo ->
            listOfNotNull(
                data.manufacturer?.let { "Mfg: $it" },
                data.model?.let { "Model: $it" },
                data.firmware?.let { "FW: $it" },
            ).joinToString(" | ").ifEmpty { "No info available" }
    }
