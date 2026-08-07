package com.atruedev.kmpble.direction

/**
 * Result of a [com.atruedev.kmpble.peripheral.Peripheral.requestDirectionFinding] call.
 */
public sealed interface DirectionFindingResult {
    /**
     * Direction finding was enabled and the platform returned an angle estimate.
     *
     * @property azimuth Horizontal angle in degrees (0..360), where 0 is north
     *   and angles increase clockwise.
     * @property elevation Vertical angle in degrees (-90..90), where 0 is the
     *   horizontal plane and positive values point upward.
     * @property signalQuality Optional RSSI or signal quality metric in dBm.
     *   Higher values (less negative) indicate stronger signals.
     */
    public data class Angle(
        val azimuth: Float,
        val elevation: Float,
        val signalQuality: Float? = null,
    ) : DirectionFindingResult {
        init {
            require(azimuth in 0.0f..360.0f) {
                "azimuth must be 0..360 degrees, was $azimuth"
            }
            require(elevation in -90.0f..90.0f) {
                "elevation must be -90..90 degrees, was $elevation"
            }
        }
    }

    /**
     * Direction finding is not supported on this platform, OS version,
     * or hardware.
     */
    public data object NotSupported : DirectionFindingResult

    /**
     * Direction finding was requested but the platform rejected it or the
     * operation failed.
     *
     * @property reason Human-readable explanation of the failure.
     */
    public data class Failed(
        val reason: String? = null,
    ) : DirectionFindingResult
}
