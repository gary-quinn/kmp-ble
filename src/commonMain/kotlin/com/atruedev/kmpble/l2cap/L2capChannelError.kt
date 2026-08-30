package com.atruedev.kmpble.l2cap

/**
 * Structured L2CAP channel failure surfaced on [L2capChannel.errors].
 *
 * Covers application-visible edge cases for L2CAP Connection-Oriented Channels (CoC) on Android
 * and iOS. LE Credit-Based Flow Control (ATT CID, credits, PID) is handled inside the OS stack
 * and is not exposed through this API.
 */
public sealed interface L2capChannelError {
    /** PSM of the affected channel. */
    public val psm: Int

    /** Channel state when the error was observed. */
    public val state: L2capChannelState

    /** Whether [L2capChannel.recover] may reopen this channel after it reaches [L2capChannelState.Closed]. */
    public val recoverable: Boolean

    /**
     * Remote device disconnected or closed the channel while it was active.
     */
    public data class RemoteDisconnected(
        override val psm: Int,
        override val state: L2capChannelState,
    ) : L2capChannelError {
        override val recoverable: Boolean = true
    }

    /**
     * The platform returned a channel that could not be opened (missing streams, invalid handle).
     */
    public data class ChannelOpenFailed(
        override val psm: Int,
        override val state: L2capChannelState,
        val reason: String,
    ) : L2capChannelError {
        override val recoverable: Boolean = false
    }

    /**
     * Data arrived on a channel that is not in [L2capChannelState.Open].
     */
    public data class UnexpectedPacket(
        override val psm: Int,
        override val state: L2capChannelState,
    ) : L2capChannelError {
        override val recoverable: Boolean = false
    }
}
