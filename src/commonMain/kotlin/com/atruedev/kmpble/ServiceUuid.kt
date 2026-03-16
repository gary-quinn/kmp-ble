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
 * val scanner = AndroidScanner(context) {
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
    public val GENERIC_ACCESS: Uuid = uuidFrom("1800")
    public val GENERIC_ATTRIBUTE: Uuid = uuidFrom("1801")
    public val DEVICE_INFORMATION: Uuid = uuidFrom("180a")
    public val BATTERY: Uuid = uuidFrom("180f")
    public val HEART_RATE: Uuid = uuidFrom("180d")
    public val BLOOD_PRESSURE: Uuid = uuidFrom("1810")
    public val HEALTH_THERMOMETER: Uuid = uuidFrom("1809")
    public val GLUCOSE: Uuid = uuidFrom("1808")
    public val RUNNING_SPEED_AND_CADENCE: Uuid = uuidFrom("1814")
    public val CYCLING_SPEED_AND_CADENCE: Uuid = uuidFrom("1816")
    public val CYCLING_POWER: Uuid = uuidFrom("1818")
    public val ENVIRONMENTAL_SENSING: Uuid = uuidFrom("181a")
    public val BODY_COMPOSITION: Uuid = uuidFrom("181b")
    public val WEIGHT_SCALE: Uuid = uuidFrom("181d")
    public val BOND_MANAGEMENT: Uuid = uuidFrom("181e")
    public val CONTINUOUS_GLUCOSE_MONITORING: Uuid = uuidFrom("181f")
    public val PULSE_OXIMETER: Uuid = uuidFrom("1822")
    public val FITNESS_MACHINE: Uuid = uuidFrom("1826")
    public val CURRENT_TIME: Uuid = uuidFrom("1805")
    public val TX_POWER: Uuid = uuidFrom("1804")
    public val IMMEDIATE_ALERT: Uuid = uuidFrom("1802")
    public val LINK_LOSS: Uuid = uuidFrom("1803")
}
