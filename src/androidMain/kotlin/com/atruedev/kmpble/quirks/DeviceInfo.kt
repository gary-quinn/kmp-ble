package com.atruedev.kmpble.quirks

import android.os.Build

/**
 * Identity of the Android device running the app.
 * All values are lowercased for case-insensitive matching.
 *
 * Inspect on device: `adb shell getprop | grep -E "ro.product.(manufacturer|model|display)"`
 */
public data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val display: String,
) {
    /** Cached match keys from most specific to least specific. Avoids allocation on every resolve. */
    @PublishedApi
    internal val matchKeys: List<String> = listOf(
        "$manufacturer:$model:$display",
        "$manufacturer:$model",
        "$manufacturer:${model.take(DeviceMatch.MODEL_PREFIX_LENGTH)}",
        manufacturer,
    )

    public companion object {
        public fun current(): DeviceInfo = DeviceInfo(
            manufacturer = Build.MANUFACTURER.lowercase(),
            model = Build.MODEL.lowercase(),
            display = Build.DISPLAY.lowercase(),
        )
    }
}
