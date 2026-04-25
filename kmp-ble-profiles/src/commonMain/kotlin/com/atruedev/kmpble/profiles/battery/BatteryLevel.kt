package com.atruedev.kmpble.profiles.battery

/**
 * Parses a Battery Level characteristic value (0x2A19).
 *
 * @return Battery percentage (0-100), or `null` if [data] is empty or out of range.
 */
public fun parseBatteryLevel(data: ByteArray): Int? {
    if (data.isEmpty()) return null
    val level = data[0].toInt() and 0xFF
    return if (level in 0..100) level else null
}
