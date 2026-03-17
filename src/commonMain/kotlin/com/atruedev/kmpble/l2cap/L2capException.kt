package com.atruedev.kmpble.l2cap

/**
 * Exception thrown when L2CAP operations fail.
 */
public sealed class L2capException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /**
     * Failed to open L2CAP channel.
     */
    public class OpenFailed(
        public val psm: Int,
        message: String,
        cause: Throwable? = null,
    ) : L2capException("Failed to open L2CAP channel on PSM $psm: $message", cause)

    /**
     * Write operation failed.
     */
    public class WriteFailed(
        message: String,
        cause: Throwable? = null,
    ) : L2capException("L2CAP write failed: $message", cause)

    /**
     * Channel is not open.
     */
    public class ChannelClosed(
        message: String = "L2CAP channel is closed",
    ) : L2capException(message)

    /**
     * Peripheral is not connected.
     */
    public class NotConnected(
        message: String = "Peripheral is not connected",
    ) : L2capException(message)

    /**
     * L2CAP is not supported on this device/OS version.
     */
    public class NotSupported(
        message: String = "L2CAP channels are not supported",
    ) : L2capException(message)
}
