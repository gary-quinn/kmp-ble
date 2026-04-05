package com.atruedev.kmpble.scanner

import kotlin.time.Duration

/**
 * Configuration for a [Scanner], built via DSL.
 *
 * ```kotlin
 * val scanner = AndroidScanner(context) {
 *     timeout = 30.seconds
 *     emission = EmissionPolicy.FirstThenChanges(rssiThreshold = 10)
 *     filters {
 *         match { serviceUuid("180d") }
 *     }
 * }
 * ```
 */
public class ScannerConfig internal constructor() {
    /** Auto-stop scan after this duration. `null` = unbounded (default). */
    public var timeout: Duration? = null

    /** Advertisement deduplication policy. Default: [EmissionPolicy.FirstThenChanges]. */
    public var emission: EmissionPolicy = EmissionPolicy.FirstThenChanges()

    /**
     * When `true` (default), only legacy advertisements (≤ 31 bytes) are reported.
     * Set to `false` to also receive BLE 5.0 extended advertisements.
     *
     * On Android, this maps to `android.bluetooth.le.ScanSettings.Builder.setLegacy`.
     * On iOS, CoreBluetooth receives extended advertisements transparently
     * regardless of this setting.
     */
    public var legacyOnly: Boolean = true

    internal var filterGroups: List<List<ScanPredicate>> = emptyList()
        private set

    /** Define scan filters using OR-of-ANDs DSL. */
    public fun filters(block: FiltersScope.() -> Unit) {
        val scope = FiltersScope().apply(block)
        filterGroups = scope.matchGroups
    }
}
