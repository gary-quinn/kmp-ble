package com.atruedev.kmpble.quirks

import android.os.Build

internal actual fun platformCurrentDevice(): DeviceInfo =
    DeviceInfo(
        manufacturer = Build.MANUFACTURER.lowercase(),
        model = Build.MODEL.lowercase(),
        display = Build.DISPLAY.lowercase(),
    )
