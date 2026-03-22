package com.atruedev.kmpble.error

public sealed interface GattStatus {
    public data object Success : GattStatus

    public data object InsufficientAuthentication : GattStatus

    public data object InsufficientEncryption : GattStatus

    public data object InsufficientAuthorization : GattStatus

    public data object InvalidOffset : GattStatus

    public data object InvalidAttributeLength : GattStatus

    public data object ReadNotPermitted : GattStatus

    public data object WriteNotPermitted : GattStatus

    public data object RequestNotSupported : GattStatus

    public data object ConnectionCongested : GattStatus

    public data object Failure : GattStatus

    public data class Unknown(val platformCode: Int, val platformName: String) : GattStatus
}
