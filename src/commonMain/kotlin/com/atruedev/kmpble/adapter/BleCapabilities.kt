package com.atruedev.kmpble.adapter

/**
 * Bluetooth 5.x feature capabilities of the current platform and hardware.
 *
 * Query via [BluetoothAdapter.capabilities] to gracefully degrade when the
 * platform does not support a requested feature, rather than relying on
 * trial-and-error with error recovery.
 *
 * On platforms where BLE is unsupported (JVM), all properties return `false`.
 *
 * ### Platform interpretation
 *
 * - **Android**: Delegates to [android.bluetooth.BluetoothAdapter] capability
 *   query methods (API 26+). Features that require a higher API level than
 *   the device reports return `false`.
 * - **iOS**: Inferred from OS version and device model. CoreBluetooth does
 *   not expose explicit PHY capability flags, so detection is best-effort
 *   based on hardware generation and iOS version.
 */
public class BleCapabilities internal constructor(
    /** Extended advertising support (BLE 5.0). Android API 26+. iOS 13+. */
    public val supportsExtendedAdvertising: Boolean,
    /** LE 2M PHY support (BLE 5.0). Android API 26+. iOS 13+ on iPhone 8/X+. */
    public val supportsLe2mPhy: Boolean,
    /** LE Coded PHY S=2/S=8 support (BLE 5.0). Android API 26+. iOS 13+. */
    public val supportsLeCodedPhy: Boolean,
    /** Periodic advertising support (BLE 5.0). Android API 26+. iOS 13+. */
    public val supportsPeriodicAdvertising: Boolean,
    /** LE Power Control support (BLE 5.0/5.1). Android API 34+. */
    public val supportsLePowerControl: Boolean,
    /** LE Audio / Isochronous Channels support (BLE 5.2). Android API 33+. */
    public val supportsLeAudio: Boolean,
    /** Connection Subrating support (BLE 5.3). Android API 35+. */
    public val supportsConnectionSubrating: Boolean,
    /** PAST -- Periodic Advertising Sync Transfer support (BLE 5.1). Android API 31+. */
    public val supportsPast: Boolean,
    /** Direction Finding (AoA/AoD) support (BLE 5.1). Android API 34+. */
    public val supportsDirectionFinding: Boolean,
) {
    override fun toString(): String =
        "BleCapabilities(" +
            "extAdv=$supportsExtendedAdvertising, " +
            "le2M=$supportsLe2mPhy, " +
            "leCoded=$supportsLeCodedPhy, " +
            "perAdv=$supportsPeriodicAdvertising, " +
            "pwrCtrl=$supportsLePowerControl, " +
            "leAudio=$supportsLeAudio, " +
            "subrating=$supportsConnectionSubrating, " +
            "past=$supportsPast, " +
            "directionFinding=$supportsDirectionFinding" +
            ")"

    /** Convenience constant: all capabilities unavailable (JVM / unsupported hardware). */
    public companion object {
        public val None: BleCapabilities =
            BleCapabilities(
                supportsExtendedAdvertising = false,
                supportsLe2mPhy = false,
                supportsLeCodedPhy = false,
                supportsPeriodicAdvertising = false,
                supportsLePowerControl = false,
                supportsLeAudio = false,
                supportsConnectionSubrating = false,
                supportsPast = false,
                supportsDirectionFinding = false,
            )
    }
}
