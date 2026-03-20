package com.atruedev.kmpble

import com.atruedev.kmpble.scanner.uuidFrom
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Standard BLE service UUIDs assigned by the Bluetooth SIG.
 *
 * Source: https://bitbucket.org/bluetooth-SIG/public/src/main/assigned_numbers/uuids/service_uuids.yaml
 *
 * Use these with the scanner filter DSL:
 * ```
 * val scanner = Scanner {
 *     filters {
 *         match { serviceUuid(ServiceUuid.HEART_RATE) }
 *     }
 * }
 * ```
 *
 * For custom/proprietary services, use [uuidFrom] with your device's UUID:
 * ```
 * match { serviceUuid("6e400001-b5a3-f393-e0a9-e50e24dcca9e") } // Nordic UART
 * ```
 */
@OptIn(ExperimentalUuidApi::class)
public object ServiceUuid {
    // GATT services
    public val GENERIC_ACCESS: Uuid = uuidFrom("1800")
    public val GENERIC_ATTRIBUTE: Uuid = uuidFrom("1801")

    // Alert & proximity
    public val IMMEDIATE_ALERT: Uuid = uuidFrom("1802")
    public val LINK_LOSS: Uuid = uuidFrom("1803")
    public val TX_POWER: Uuid = uuidFrom("1804")

    // Time
    public val CURRENT_TIME: Uuid = uuidFrom("1805")
    public val REFERENCE_TIME_UPDATE: Uuid = uuidFrom("1806")
    public val NEXT_DST_CHANGE: Uuid = uuidFrom("1807")

    // Health & medical
    public val GLUCOSE: Uuid = uuidFrom("1808")
    public val HEALTH_THERMOMETER: Uuid = uuidFrom("1809")
    public val DEVICE_INFORMATION: Uuid = uuidFrom("180a")
    public val HEART_RATE: Uuid = uuidFrom("180d")
    public val PHONE_ALERT_STATUS: Uuid = uuidFrom("180e")
    public val BATTERY: Uuid = uuidFrom("180f")
    public val BLOOD_PRESSURE: Uuid = uuidFrom("1810")
    public val ALERT_NOTIFICATION: Uuid = uuidFrom("1811")
    public val HUMAN_INTERFACE_DEVICE: Uuid = uuidFrom("1812")

    // Scanning & network
    public val SCAN_PARAMETERS: Uuid = uuidFrom("1813")
    public val RUNNING_SPEED_AND_CADENCE: Uuid = uuidFrom("1814")
    public val AUTOMATION_IO: Uuid = uuidFrom("1815")
    public val CYCLING_SPEED_AND_CADENCE: Uuid = uuidFrom("1816")
    public val CYCLING_POWER: Uuid = uuidFrom("1818")
    public val LOCATION_AND_NAVIGATION: Uuid = uuidFrom("1819")

    // Environmental & body
    public val ENVIRONMENTAL_SENSING: Uuid = uuidFrom("181a")
    public val BODY_COMPOSITION: Uuid = uuidFrom("181b")
    public val USER_DATA: Uuid = uuidFrom("181c")
    public val WEIGHT_SCALE: Uuid = uuidFrom("181d")
    public val BOND_MANAGEMENT: Uuid = uuidFrom("181e")
    public val CONTINUOUS_GLUCOSE_MONITORING: Uuid = uuidFrom("181f")

    // Internet & transport
    public val INTERNET_PROTOCOL_SUPPORT: Uuid = uuidFrom("1820")
    public val INDOOR_POSITIONING: Uuid = uuidFrom("1821")
    public val PULSE_OXIMETER: Uuid = uuidFrom("1822")
    public val HTTP_PROXY: Uuid = uuidFrom("1823")
    public val TRANSPORT_DISCOVERY: Uuid = uuidFrom("1824")
    public val OBJECT_TRANSFER: Uuid = uuidFrom("1825")
    public val FITNESS_MACHINE: Uuid = uuidFrom("1826")
    public val MESH_PROVISIONING: Uuid = uuidFrom("1827")
    public val MESH_PROXY: Uuid = uuidFrom("1828")
    public val RECONNECTION_CONFIGURATION: Uuid = uuidFrom("1829")

    // Insulin, emergency, physical activity
    public val INSULIN_DELIVERY: Uuid = uuidFrom("183a")
    public val BINARY_SENSOR: Uuid = uuidFrom("183b")
    public val EMERGENCY_CONFIGURATION: Uuid = uuidFrom("183c")
    public val PHYSICAL_ACTIVITY_MONITOR: Uuid = uuidFrom("183e")

    // Audio & telephony
    public val AUDIO_INPUT_CONTROL: Uuid = uuidFrom("1843")
    public val VOLUME_CONTROL: Uuid = uuidFrom("1844")
    public val VOLUME_OFFSET_CONTROL: Uuid = uuidFrom("1845")
    public val COORDINATED_SET_IDENTIFICATION: Uuid = uuidFrom("1846")
    public val MEDIA_CONTROL: Uuid = uuidFrom("1848")
    public val GENERIC_MEDIA_CONTROL: Uuid = uuidFrom("1849")
    public val TELEPHONE_BEARER: Uuid = uuidFrom("184b")
    public val GENERIC_TELEPHONE_BEARER: Uuid = uuidFrom("184c")
    public val MICROPHONE_CONTROL: Uuid = uuidFrom("184d")

    // Common vendor services (not SIG-assigned, but widely used)

    /** Nordic UART Service — defacto standard for serial-over-BLE. */
    public val NORDIC_UART: Uuid = uuidFrom("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
}
