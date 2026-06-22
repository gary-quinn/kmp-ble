package com.atruedev.kmpble.isochronous

/**
 * Exception thrown when LE Audio isochronous channel operations fail.
 *
 * Isochronous channels (CIS/BIS, Bluetooth 5.2+) provide time-bounded
 * streaming for LE Audio use cases such as hearing aids, broadcast audio,
 * and low-latency audio devices.
 */
public sealed class IsochronousException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /**
     * Failed to open an isochronous channel.
     */
    public class OpenFailed(
        message: String,
        cause: Throwable? = null,
    ) : IsochronousException("Failed to open isochronous channel: $message", cause)

    /**
     * Write operation failed.
     */
    public class WriteFailed(
        message: String,
        cause: Throwable? = null,
    ) : IsochronousException("Isochronous write failed: $message", cause)

    /**
     * Channel is not open.
     */
    public class ChannelClosed(
        message: String = "Isochronous channel is closed",
    ) : IsochronousException(message)

    /**
     * Peripheral is not connected.
     */
    public class NotConnected(
        message: String = "Peripheral is not connected",
    ) : IsochronousException(message)

    /**
     * Isochronous channels are not supported on this device/OS version.
     */
    public class NotSupported(
        message: String = "LE Audio isochronous channels are not supported",
    ) : IsochronousException(message)

    /**
     * Stream configuration is invalid.
     */
    public class InvalidConfiguration(
        message: String,
    ) : IsochronousException(message)
}
