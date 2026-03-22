package com.atruedev.kmpble.dfu

public sealed interface DfuError {

    public data class NotConnected(
        override val message: String = "Peripheral is not connected",
    ) : Exception(message), DfuError

    public data class ServiceNotFound(
        override val message: String = "DFU service not found on peripheral",
    ) : Exception(message), DfuError

    public data class CharacteristicNotFound(
        val name: String,
        override val message: String = "DFU characteristic not found: $name",
    ) : Exception(message), DfuError

    public data class ProtocolError(
        val opcode: Int,
        val resultCode: Int,
        override val message: String,
    ) : Exception(message), DfuError

    public data class ChecksumMismatch(
        val expected: UInt,
        val actual: UInt,
    ) : Exception("CRC32 mismatch: expected 0x${expected.toString(16)}, actual 0x${actual.toString(16)}"), DfuError

    public data class TransferFailed(
        override val message: String,
        override val cause: Throwable? = null,
    ) : Exception(message, cause), DfuError

    public data class FirmwareParseError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : Exception(message, cause), DfuError

    public data class Timeout(
        override val message: String = "DFU operation timed out",
    ) : Exception(message), DfuError

    public data class Aborted(
        override val message: String = "DFU was aborted",
    ) : Exception(message), DfuError
}
