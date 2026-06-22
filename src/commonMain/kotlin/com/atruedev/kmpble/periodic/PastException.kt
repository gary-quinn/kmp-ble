package com.atruedev.kmpble.periodic

/**
 * Exception thrown when periodic advertising sync or PAST operations fail.
 *
 * Periodic Advertising Sync Transfer (PAST, BLE 5.1) allows a device to
 * transfer an established periodic advertising sync to a connected peer.
 */
public sealed class PastException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /**
     * PAST is not supported on this platform or OS version.
     *
     * - Android: Requires API 31+ for PAST transfer.
     * - iOS: CoreBluetooth does not expose periodic advertising sync APIs.
     * - JVM: No Bluetooth hardware.
     */
    public class NotSupported(
        message: String = "Periodic Advertising Sync Transfer is not supported on this platform",
    ) : PastException(message)

    /**
     * Sync to periodic advertising failed (e.g., sync lost, timeout).
     */
    public class SyncFailed(
        message: String,
        cause: Throwable? = null,
    ) : PastException("Periodic advertising sync lost: $message", cause)

    /**
     * Transfer of the sync to a connected peripheral failed.
     */
    public class TransferFailed(
        message: String,
        cause: Throwable? = null,
    ) : PastException("PAST transfer failed: $message", cause)

    /**
     * Peripheral is not connected -- PAST requires an active connection.
     */
    public class NotConnected(
        message: String = "Peripheral is not connected -- PAST requires an active connection",
    ) : PastException(message)

    /**
     * Sync is no longer active -- the advertiser stopped or moved out of range.
     */
    public class SyncInactive(
        message: String = "Periodic advertising sync is no longer active",
    ) : PastException(message)
}
