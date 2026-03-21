package com.atruedev.kmpble.bonding

import com.atruedev.kmpble.ExperimentalBleApi

/**
 * BLE pairing methods as defined by the Bluetooth specification.
 *
 * The pairing method is selected by the Bluetooth stack based on the
 * I/O capabilities of both devices.
 */
@ExperimentalBleApi
public enum class PairingMethod {
    /** No user interaction required. Lowest security. */
    JustWorks,

    /** Remote device displays a passkey; local device must enter it. */
    PasskeyEntry,

    /**
     * Both devices display a 6-digit number; user confirms they match.
     * Provides MITM protection — preferred over [JustWorks] and [PasskeyEntry]
     * when both devices have a display and confirmation capability.
     */
    NumericComparison,

    /**
     * Out-of-band data exchange (e.g., NFC tap) for key agreement.
     * Provides MITM protection via a separate secure channel.
     */
    OutOfBand,
}
