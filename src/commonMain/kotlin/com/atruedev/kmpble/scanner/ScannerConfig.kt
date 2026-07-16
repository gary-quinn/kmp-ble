package com.atruedev.kmpble.scanner

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
     * Setting `true` (the default) tells the controller to drop extended-advertising
     * PDUs before `onScanResult` fires - silently, with no error and no `ScanEvent`.
     * A peripheral advertising a local name plus one or more 128-bit service UUIDs
     * commonly exceeds the legacy 31-byte payload and falls back to Extended
     * Advertising, so it can appear to simply not be advertising on Android with
     * default config. The Android scanner logs a `BleLogEvent.Warning` once per
     * scan when this is left at the default `true` (see #576) so the failure is at
     * least diagnosable - set `BleLogConfig.logger` to see it.
     *
     * On iOS, CoreBluetooth receives extended advertisements transparently
     * regardless of this setting, so this flag only ever restricts Android.
     */
    public var legacyOnly: Boolean = true

    /**
     * Scan strategy. Maps to Android `ScanSettings.Builder.setScanMode()`.
     *
     * | ------- | ------------------------------------------ |
     * | Android | `ScanSettings.SCAN_MODE_*` (API 26+).      |
     * | iOS     | No public API; CoreBluetooth chooses.      |
     *
     * Default: [ScanMode.Balanced].
     */
    public var scanMode: ScanMode = ScanMode.Balanced

    /**
     * PHY to scan on. Maps to the Android `ScanSettings.Builder.setPhy()` value.
     *
     * - [ScanPhy.Le1M]: 1 Mbps scanning on primary channels (always supported).
     * - [ScanPhy.LeCoded]: Long-range scanning with forward error correction (BLE 5.0+)
     *   via extended advertisements on secondary advertising channels.
     * - [ScanPhy.All]: Scan on all PHYs supported by the controller (default).
     *
     * | ------- | ------ |
     * | Android | `ScanSettings.Builder.setPhy()` with the corresponding flag (API 26+). |
     * | iOS     | No direct equivalent; CoreBluetooth scans on all PHYs transparently.   |
     */
    public var phy: ScanPhy = ScanPhy.All

    internal var filterGroups: List<List<ScanPredicate>> = emptyList()
        private set

    /** Define scan filters using OR-of-ANDs DSL. */
    public fun filters(block: FiltersScope.() -> Unit) {
        val scope = FiltersScope().apply(block)
        filterGroups = scope.matchGroups
    }

    public companion object {
        /**
         * Sensible default scanner configuration: all PHYs, extended advertisements,
         * 30s timeout, first-then-changes emission.
         *
         * ```kotlin
         * val scanner = AndroidScanner(context) { default(this) }
         * ```
         */
        public fun default(config: ScannerConfig) {
            config.phy = ScanPhy.All
            config.legacyOnly = false
            config.timeout = 30.seconds
            config.emission = EmissionPolicy.FirstThenChanges()
        }
    }
}
