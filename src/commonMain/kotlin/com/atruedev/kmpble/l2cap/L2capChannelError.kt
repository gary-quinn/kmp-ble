package com.atruedev.kmpble.l2cap

/**
 * Structured, recoverable L2CAP channel failure surfaced on [L2capChannel.errors].
 *
 * These model application-visible edge cases for L2CAP Connection-Oriented Channels (CoC).
 * LE Credit-Based Flow Control details (ATT CID, PID, credits) are handled inside the OS
 * stack on Android and iOS; [CreditExhausted] is defined for parity with embedded stacks
 * and fakes but is not emitted by mobile CoC implementations.
 */
public sealed interface L2capChannelError {
    /** PSM of the affected channel. */
    public val psm: Int

    /** Channel state when the error was observed. */
    public val state: L2capChannelState

    /** Whether [L2capChannel.recover] may reopen this channel. */
    public val recoverable: Boolean

    /**
     * Peer or OS rejected the channel open request.
     */
    public data class ConnectionRejected(
        override val psm: Int,
        override val state: L2capChannelState,
        val reason: RejectionReason,
    ) : L2capChannelError {
        override val recoverable: Boolean = true
    }

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
     * Credit pool exhausted on a credit-based channel.
     *
     * Not emitted by Android/iOS CoC channels (credits are managed by the OS). Available for
     * fakes and future embedded-style integrations.
     */
    public data class CreditExhausted(
        override val psm: Int,
        override val state: L2capChannelState,
    ) : L2capChannelError {
        override val recoverable: Boolean = true
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

    /**
     * Channel open did not complete within the configured timeout.
     */
    public data class OpenTimeout(
        override val psm: Int,
        override val state: L2capChannelState,
    ) : L2capChannelError {
        override val recoverable: Boolean = true
    }

    /**
     * Incoming buffer overflow; oldest packets were dropped to make room.
     */
    public data class BackpressureOverflow(
        override val psm: Int,
        override val state: L2capChannelState,
        val dropped: Int,
    ) : L2capChannelError {
        override val recoverable: Boolean = false
    }
}

/**
 * Reason a channel open was rejected.
 */
public enum class RejectionReason {
    NotConnected,
    PermissionDenied,
    UnsupportedParameters,
    PeerRejected,
    Unknown,
}
