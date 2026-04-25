package com.atruedev.kmpble.scanner

/**
 * Controls how scan results are deduplicated before emission.
 *
 * Default: [FirstThenChanges] - emits the first advertisement per device, then
 * re-emits only when data or RSSI changes significantly. This prevents flooding
 * the collector with 10+ identical advertisements per second per device.
 */
public sealed class EmissionPolicy {
    /** Emit every advertisement callback. Use for RSSI tracking / indoor positioning. */
    public data object All : EmissionPolicy()

    /**
     * Emit first advertisement per identifier, then re-emit only when advertisement
     * data changes or RSSI changes by more than [rssiThreshold] dBm.
     */
    public data class FirstThenChanges(
        val rssiThreshold: Int = 5,
    ) : EmissionPolicy()
}
