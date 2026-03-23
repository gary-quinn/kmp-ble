package com.atruedev.kmpble.dfu

/**
 * Failure conditions during a firmware update.
 *
 * Every subtype is both a [DfuError] and an [Exception], so errors can be
 * caught with standard `try`/`catch` and also pattern-matched via `when`.
 * Errors are surfaced through [DfuProgress.Failed] when using [DfuController].
 */
public sealed interface DfuError {

    /** Peripheral was not in [Connected][com.atruedev.kmpble.connection.State.Connected] state when the DFU started. */
    public data class NotConnected(
        override val message: String = "Peripheral is not connected",
    ) : Exception(message), DfuError

    /** The peripheral does not expose the required DFU GATT service. */
    public data class ServiceNotFound(
        override val message: String = "DFU service not found on peripheral",
    ) : Exception(message), DfuError

    /** A required DFU GATT characteristic is missing from the service. */
    public data class CharacteristicNotFound(
        val name: String,
        override val message: String = "DFU characteristic not found: $name",
    ) : Exception(message), DfuError

    /**
     * The peripheral returned an error response to a DFU command.
     *
     * @property opcode the DFU opcode that was rejected
     * @property resultCode the Nordic DFU result code (see Nordic DFU spec for values)
     */
    public data class ProtocolError(
        val opcode: Int,
        val resultCode: Int,
        override val message: String,
    ) : Exception(message), DfuError

    /**
     * CRC32 verification failed after transferring an object.
     *
     * @property expected CRC computed locally from the source data
     * @property actual CRC reported by the peripheral
     */
    public data class ChecksumMismatch(
        val expected: UInt,
        val actual: UInt,
    ) : Exception("CRC32 mismatch: expected 0x${expected.toString(16)}, actual 0x${actual.toString(16)}"), DfuError

    /** Catch-all for transport-level failures (disconnects, write errors). */
    public data class TransferFailed(
        override val message: String,
        override val cause: Throwable? = null,
    ) : Exception(message, cause), DfuError

    /** The firmware .zip archive could not be parsed. */
    public data class FirmwareParseError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : Exception(message, cause), DfuError

    /** A DFU command did not receive a response within the configured [DfuOptions.commandTimeout]. */
    public data class Timeout(
        override val message: String = "DFU operation timed out",
    ) : Exception(message), DfuError

    /** The DFU was cancelled by calling [DfuController.abort]. */
    public data class Aborted(
        override val message: String = "DFU was aborted",
    ) : Exception(message), DfuError
}
