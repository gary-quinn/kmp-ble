package com.atruedev.kmpble.internal

import android.os.Build

internal data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val display: String,
) {
    companion object {
        fun current(): DeviceInfo = DeviceInfo(
            manufacturer = Build.MANUFACTURER.lowercase(),
            model = Build.MODEL.lowercase(),
            display = Build.DISPLAY.lowercase(),
        )
    }
}
