package com.atruedev.kmpble.error

import kotlin.time.Duration

/**
 * A BLE operation timed out before completion.
 *
 * The platform did not respond within the configured [OperationTimeouts] for
 * the given operation. Common causes include the peripheral being out of
 * range, the peripheral's BLE stack being busy, or RF congestion.
 */
public data class PeripheralTimeout(
    /** The operation that timed out (e.g. "connect", "read", "write"). */
    val operation: String,
    /** The timeout duration that was exceeded. */
    val timeout: Duration,
    val recoveryHint: String =
        "The operation timed out. Verify the peripheral is in range and responsive. " +
            "Consider increasing the timeout via ConnectionOptions.timeouts.",
) : BleError
