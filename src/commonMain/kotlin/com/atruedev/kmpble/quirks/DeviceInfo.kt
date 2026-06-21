package com.atruedev.kmpble.quirks

/**
 * Identity of the device running the app.
 * All values are lowercased for case-insensitive matching.
 */
public data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val display: String,
) {
    /** Cached match keys from most specific to least specific. Avoids allocation on every resolve. */
    @PublishedApi
    internal val matchKeys: List<String> =
        listOf(
            "$manufacturer:$model:$display",
            "$manufacturer:$model",
            "$manufacturer:${model.take(DeviceMatch.MODEL_PREFIX_LENGTH)}",
            manufacturer,
        )

    public companion object {
        /** Returns the device running this process. Platform-specific. */
        public fun current(): DeviceInfo = platformCurrentDevice()
    }
}

/** Platform-specific: returns the current device identity. */
internal expect fun platformCurrentDevice(): DeviceInfo
