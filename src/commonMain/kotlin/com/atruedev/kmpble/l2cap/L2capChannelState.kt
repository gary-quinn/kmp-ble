package com.atruedev.kmpble.l2cap

/**
 * Lifecycle state of an [L2capChannel].
 *
 * Typical transitions:
 * - Client open: [Opening] -> [Open]
 * - Graceful close: [Open] -> [Closing] -> [Closed]
 * - Remote disconnect: [Open] -> [Closing] -> [Closed] (recoverable [L2capChannelError] on [L2capChannel.errors])
 */
public enum class L2capChannelState {
    /** Channel is being established (socket connect / CoreBluetooth streams opening). */
    Opening,

    /** Channel is open and ready for [L2capChannel.write] and [L2capChannel.incoming]. */
    Open,

    /** Local or remote close in progress; streams are being torn down. */
    Closing,

    /**
     * Channel is fully closed. [L2capChannel.recover] may reopen when the close was due to a
     * recoverable error (see [L2capChannel.errors]).
     */
    Closed,
}
