package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.connection.Phy
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

    /**
     * PHYs to scan on. Maps to the Android ScanSettings.setPhy() value.
     *
     * - [Phy.Le1M]: 1 Mbps scanning (always supported, BLE 4.0+)
     * - [Phy.Le2M]: 2 Mbps scanning (BLE 5.0+, requires hardware support)
     * - [Phy.LeCoded]: Long-range scanning with forward error correction (BLE 5.0+)
     *
     * Default: scan on all PHYs supported by the hardware.
     *
     * | Android | `ScanSettings.Builder.setPhy()` with the corresponding flag. |
     * |---------|----------|
     * | iOS     | No direct equivalent; CoreBluetooth scans on all PHYs transparently. |
     *
     * When scanning on a single PHY, only advertisements received on that PHY
     * are reported. When scanning on multiple PHYs (or the default "all"), the
     * platform reports advertisements from all supported PHYs.
     */
    public var scanPhy: Set<Phy> = setOf(Phy.Le1M, Phy.Le2M, Phy.LeCoded)

    internal var filterGroups: List<List<ScanPredicate>> = emptyList()
        private set

    /** Define scan filters using OR-of-ANDs DSL. */
    public fun filters(block: FiltersScope.() -> Unit) {
        val scope = FiltersScope().apply(block)
        filterGroups = scope.matchGroups
    }
}
