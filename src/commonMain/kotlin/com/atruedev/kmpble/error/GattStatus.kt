package com.atruedev.kmpble.error

/**
 * Cross-platform representation of a GATT operation result code.
 *
 * Maps platform-specific status codes (Android `BluetoothGatt.GATT_*`, iOS `CBATTError.*`)
 * to a common sealed hierarchy. Use [Unknown] for codes that don't map to a known constant.
 */
public sealed interface GattStatus {
    /** The operation completed successfully. */
    public data object Success : GattStatus

    /** The peripheral requires authentication (bonding) before this operation. */
    public data object InsufficientAuthentication : GattStatus

    /** The peripheral requires an encrypted link before this operation. */
    public data object InsufficientEncryption : GattStatus

    /** The peripheral rejected the operation due to authorization requirements. */
    public data object InsufficientAuthorization : GattStatus

    /** The read/write offset is invalid for this characteristic or descriptor. */
    public data object InvalidOffset : GattStatus

    /** The value length exceeds the attribute's maximum allowed size. */
    public data object InvalidAttributeLength : GattStatus

    /** The characteristic or descriptor does not permit reads. */
    public data object ReadNotPermitted : GattStatus

    /** The characteristic or descriptor does not permit writes. */
    public data object WriteNotPermitted : GattStatus

    /** The peripheral does not support the requested ATT operation. */
    public data object RequestNotSupported : GattStatus

    /** The connection is congested — retry the operation after a delay. */
    public data object ConnectionCongested : GattStatus

    /** A generic GATT failure with no specific cause. */
    public data object Failure : GattStatus

    /**
     * A platform-specific status code not mapped to a known constant.
     *
     * @property platformCode The raw numeric status code from the platform.
     * @property platformName Human-readable name from the platform SDK, if available.
     */
    public data class Unknown(val platformCode: Int, val platformName: String) : GattStatus
}
