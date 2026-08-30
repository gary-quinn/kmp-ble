package com.atruedev.kmpble.l2cap

/**
 * Lifecycle state of an [L2capChannel].
 *
 * Typical transitions:
 * - Client open: [Opening] -> [Open]
 * - Graceful close: [Open] -> [Closing] -> [Closed]
 * - Remote disconnect or I/O failure: [Open] -> [Error] -> [Closed] (after [L2capChannel.recover] or [close])
 */
public enum class L2capChannelState {
    /** Channel is being established (socket connect / CoreBluetooth callback pending). */
    Opening,

    /** Channel is open and ready for [L2capChannel.write] and [L2capChannel.incoming]. */
    Open,

    /** Local [L2capChannel.close] in progress; streams are being flushed and torn down. */
    Closing,

    /** Channel is fully closed. No further operations except [L2capChannel.recover] when eligible. */
    Closed,

    /**
     * Channel encountered a recoverable failure (remote disconnect, open timeout, etc.).
     * Call [L2capChannel.recover] to reopen, or [L2capChannel.close] to abandon.
     */
    Error,
}
