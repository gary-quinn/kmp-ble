package com.atruedev.kmpble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class ServiceUuidTest {

    @Test
    fun heartRateUuidMatchesSigSpec() {
        assertEquals("0000180d-0000-1000-8000-00805f9b34fb", ServiceUuid.HEART_RATE.toString())
    }

    @Test
    fun batteryUuidMatchesSigSpec() {
        assertEquals("0000180f-0000-1000-8000-00805f9b34fb", ServiceUuid.BATTERY.toString())
    }

    @Test
    fun deviceInfoUuidMatchesSigSpec() {
        assertEquals("0000180a-0000-1000-8000-00805f9b34fb", ServiceUuid.DEVICE_INFORMATION.toString())
    }

    @Test
    fun nordicUartIsNonSigUuid() {
        // Nordic UART is a full 128-bit UUID, not a 16-bit SIG UUID
        assertNotEquals("0000", ServiceUuid.NORDIC_UART.toString().substring(0, 4))
    }

    @Test
    fun allUuidsAreUnique() {
        val uuids = listOf(
            ServiceUuid.GENERIC_ACCESS, ServiceUuid.GENERIC_ATTRIBUTE,
            ServiceUuid.IMMEDIATE_ALERT, ServiceUuid.LINK_LOSS, ServiceUuid.TX_POWER,
            ServiceUuid.CURRENT_TIME, ServiceUuid.REFERENCE_TIME_UPDATE, ServiceUuid.NEXT_DST_CHANGE,
            ServiceUuid.GLUCOSE, ServiceUuid.HEALTH_THERMOMETER, ServiceUuid.DEVICE_INFORMATION,
            ServiceUuid.HEART_RATE, ServiceUuid.PHONE_ALERT_STATUS, ServiceUuid.BATTERY,
            ServiceUuid.BLOOD_PRESSURE, ServiceUuid.ALERT_NOTIFICATION, ServiceUuid.HUMAN_INTERFACE_DEVICE,
            ServiceUuid.SCAN_PARAMETERS, ServiceUuid.RUNNING_SPEED_AND_CADENCE, ServiceUuid.AUTOMATION_IO,
            ServiceUuid.CYCLING_SPEED_AND_CADENCE, ServiceUuid.CYCLING_POWER,
            ServiceUuid.LOCATION_AND_NAVIGATION, ServiceUuid.ENVIRONMENTAL_SENSING,
            ServiceUuid.BODY_COMPOSITION, ServiceUuid.USER_DATA, ServiceUuid.WEIGHT_SCALE,
            ServiceUuid.BOND_MANAGEMENT, ServiceUuid.CONTINUOUS_GLUCOSE_MONITORING,
            ServiceUuid.INTERNET_PROTOCOL_SUPPORT, ServiceUuid.INDOOR_POSITIONING,
            ServiceUuid.PULSE_OXIMETER, ServiceUuid.HTTP_PROXY, ServiceUuid.TRANSPORT_DISCOVERY,
            ServiceUuid.OBJECT_TRANSFER, ServiceUuid.FITNESS_MACHINE,
            ServiceUuid.MESH_PROVISIONING, ServiceUuid.MESH_PROXY, ServiceUuid.RECONNECTION_CONFIGURATION,
            ServiceUuid.INSULIN_DELIVERY, ServiceUuid.BINARY_SENSOR, ServiceUuid.EMERGENCY_CONFIGURATION,
            ServiceUuid.PHYSICAL_ACTIVITY_MONITOR, ServiceUuid.AUDIO_INPUT_CONTROL,
            ServiceUuid.VOLUME_CONTROL, ServiceUuid.VOLUME_OFFSET_CONTROL,
            ServiceUuid.COORDINATED_SET_IDENTIFICATION, ServiceUuid.MEDIA_CONTROL,
            ServiceUuid.GENERIC_MEDIA_CONTROL, ServiceUuid.TELEPHONE_BEARER,
            ServiceUuid.GENERIC_TELEPHONE_BEARER, ServiceUuid.MICROPHONE_CONTROL,
            ServiceUuid.NORDIC_UART,
        )
        assertEquals(uuids.size, uuids.toSet().size, "Duplicate UUIDs found in ServiceUuid")
    }
}
