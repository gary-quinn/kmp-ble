package com.atruedev.kmpble.scanner

/**
 * PHY for BLE scan operations.
 *
 * Scanning only uses primary and Coded PHYs; LE 2M is not applicable to
 * scanning because advertisements are transmitted on the primary advertising
 * channels (37/38/39) which always use LE 1M, while extended advertisements
 * on secondary channels support LE Coded (S=2/S=8) for long range.
 *
 * | ---------- | ---------- |
 * | Android    | `ScanSettings.Builder.setPhy()` (API 26+).                 |
 * | iOS        | CoreBluetooth handles PHY automatically; no public API.     |
 */
public enum class ScanPhy {
    /** Primary PHY (1 Mbps). Always supported. */
    Le1M,

    /**
     * LE Coded PHY (S=2, S=8) for long-range scanning via extended
     * advertisements on secondary advertising channels (BLE 5.0+).
     */
    LeCoded,

    /** All PHYs supported by the controller (default). */
    All,
}
