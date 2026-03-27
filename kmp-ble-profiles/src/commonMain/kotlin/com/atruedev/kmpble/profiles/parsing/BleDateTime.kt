package com.atruedev.kmpble.profiles.parsing

/**
 * Date/time as defined in the Bluetooth GATT specification (org.bluetooth.characteristic.date_time).
 *
 * Used by profile parsers for timestamped measurements (Blood Pressure, Glucose, etc.).
 */
public data class BleDateTime(
    val year: Int,
    val month: Int,
    val day: Int,
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
)
