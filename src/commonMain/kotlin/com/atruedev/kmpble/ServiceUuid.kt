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

    private val _all = mutableListOf<Uuid>()
    private var frozen = false
    private fun register(uuid: Uuid): Uuid { check(!frozen) { "Add new UUIDs above ALL" }; _all += uuid; return uuid }
    private fun sig(shortCode: String): Uuid = register(uuidFrom(shortCode))
    private fun vendor(fullUuid: String): Uuid = register(uuidFrom(fullUuid))

    public val GENERIC_ACCESS: Uuid = sig("1800")
    public val GENERIC_ATTRIBUTE: Uuid = sig("1801")

    public val IMMEDIATE_ALERT: Uuid = sig("1802")
    public val LINK_LOSS: Uuid = sig("1803")
    public val TX_POWER: Uuid = sig("1804")

    public val CURRENT_TIME: Uuid = sig("1805")
    public val REFERENCE_TIME_UPDATE: Uuid = sig("1806")
    public val NEXT_DST_CHANGE: Uuid = sig("1807")

    public val GLUCOSE: Uuid = sig("1808")
    public val HEALTH_THERMOMETER: Uuid = sig("1809")
    public val DEVICE_INFORMATION: Uuid = sig("180a")
    public val HEART_RATE: Uuid = sig("180d")
    public val PHONE_ALERT_STATUS: Uuid = sig("180e")
    public val BATTERY: Uuid = sig("180f")
    public val BLOOD_PRESSURE: Uuid = sig("1810")
    public val ALERT_NOTIFICATION: Uuid = sig("1811")
    public val HUMAN_INTERFACE_DEVICE: Uuid = sig("1812")

    public val SCAN_PARAMETERS: Uuid = sig("1813")
    public val RUNNING_SPEED_AND_CADENCE: Uuid = sig("1814")
    public val AUTOMATION_IO: Uuid = sig("1815")
    public val CYCLING_SPEED_AND_CADENCE: Uuid = sig("1816")
    public val CYCLING_POWER: Uuid = sig("1818")
    public val LOCATION_AND_NAVIGATION: Uuid = sig("1819")

    public val ENVIRONMENTAL_SENSING: Uuid = sig("181a")
    public val BODY_COMPOSITION: Uuid = sig("181b")
    public val USER_DATA: Uuid = sig("181c")
    public val WEIGHT_SCALE: Uuid = sig("181d")
    public val BOND_MANAGEMENT: Uuid = sig("181e")
    public val CONTINUOUS_GLUCOSE_MONITORING: Uuid = sig("181f")

    public val INTERNET_PROTOCOL_SUPPORT: Uuid = sig("1820")
    public val INDOOR_POSITIONING: Uuid = sig("1821")
    public val PULSE_OXIMETER: Uuid = sig("1822")
    public val HTTP_PROXY: Uuid = sig("1823")
    public val TRANSPORT_DISCOVERY: Uuid = sig("1824")
    public val OBJECT_TRANSFER: Uuid = sig("1825")
    public val FITNESS_MACHINE: Uuid = sig("1826")
    public val MESH_PROVISIONING: Uuid = sig("1827")
    public val MESH_PROXY: Uuid = sig("1828")
    public val RECONNECTION_CONFIGURATION: Uuid = sig("1829")

    public val INSULIN_DELIVERY: Uuid = sig("183a")
    public val BINARY_SENSOR: Uuid = sig("183b")
    public val EMERGENCY_CONFIGURATION: Uuid = sig("183c")
    public val PHYSICAL_ACTIVITY_MONITOR: Uuid = sig("183e")

    public val AUDIO_INPUT_CONTROL: Uuid = sig("1843")
    public val VOLUME_CONTROL: Uuid = sig("1844")
    public val VOLUME_OFFSET_CONTROL: Uuid = sig("1845")
    public val COORDINATED_SET_IDENTIFICATION: Uuid = sig("1846")
    public val MEDIA_CONTROL: Uuid = sig("1848")
    public val GENERIC_MEDIA_CONTROL: Uuid = sig("1849")
    public val TELEPHONE_BEARER: Uuid = sig("184b")
    public val GENERIC_TELEPHONE_BEARER: Uuid = sig("184c")
    public val MICROPHONE_CONTROL: Uuid = sig("184d")

    /** Nordic UART Service — defacto standard for serial-over-BLE. */
    public val NORDIC_UART: Uuid = vendor("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

    /** All UUIDs defined in this object, auto-populated via [sig] and [vendor]. */
    public val ALL: List<Uuid> = _all.toList().also { frozen = true }
}
