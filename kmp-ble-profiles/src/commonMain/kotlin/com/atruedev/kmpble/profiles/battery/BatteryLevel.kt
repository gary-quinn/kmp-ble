package com.atruedev.kmpble.profiles.battery

public fun parseBatteryLevel(data: ByteArray): Int? {
    if (data.isEmpty()) return null
    val level = data[0].toInt() and 0xFF
    return if (level in 0..100) level else null
}
