package com.atruedev.kmpble.sample.profiles

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

enum class ProfileType {
    HEART_RATE,
    BATTERY,
    BLOOD_PRESSURE,
    GLUCOSE,
    CSC,
    DEVICE_INFO,
}

sealed interface ProfileData {
    data class HeartRate(
        val bpm: Int,
        val sensorContact: Boolean?,
        val rrIntervals: List<Int>,
    ) : ProfileData

    data class Battery(
        val level: Int,
    ) : ProfileData

    data class DeviceInfo(
        val manufacturer: String?,
        val model: String?,
        val serial: String?,
        val firmware: String?,
        val hardware: String?,
        val software: String?,
    ) : ProfileData
}

@OptIn(ExperimentalUuidApi::class)
data class DetectedProfile(
    val type: ProfileType,
    val serviceUuid: Uuid,
)

data class ProfileUiState(
    val detectedProfiles: List<DetectedProfile> = emptyList(),
    val readings: Map<ProfileType, ProfileData> = emptyMap(),
    val activeSubscriptions: Set<ProfileType> = emptySet(),
    val error: String? = null,
)
