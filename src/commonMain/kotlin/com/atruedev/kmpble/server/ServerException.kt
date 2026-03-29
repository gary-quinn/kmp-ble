package com.atruedev.kmpble.server

public sealed class ServerException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    public class OpenFailed(
        message: String,
        cause: Throwable? = null,
    ) : ServerException("Failed to open GATT server: $message", cause)

    public class NotOpen(
        message: String = "GATT server is not open",
    ) : ServerException(message)

    public class NotSupported(
        message: String = "GATT server is not supported on this device",
    ) : ServerException(message)

    public class DeviceNotConnected(
        message: String = "Device is not connected to the server",
    ) : ServerException(message)

    public class NotifyFailed(
        message: String,
        cause: Throwable? = null,
    ) : ServerException("Notification failed: $message", cause)
}

public sealed class AdvertiserException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    public class StartFailed(
        message: String,
        cause: Throwable? = null,
    ) : AdvertiserException("Failed to start advertising: $message", cause)

    public class NotSupported(
        message: String = "BLE advertising is not supported on this device",
    ) : AdvertiserException(message)

    public class AlreadyAdvertising(
        message: String = "Already advertising",
    ) : AdvertiserException(message)
}
