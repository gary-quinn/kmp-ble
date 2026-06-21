package com.atruedev.kmpble.error

import kotlin.time.Duration

/**
 * Root of the composable BLE error hierarchy.
 *
 * Errors are organized into sealed sub-interfaces ([ConnectionError], [GattOperationError],
 * [AuthError], [OperationConstraintError]) that can be composed - e.g. [AuthenticationFailed]
 * implements both [AuthError] and [GattOperationError], allowing callers to pattern-match at
 * the granularity they need.
 */
public sealed interface BleError

// --- Connection-level errors ---

/** Errors that occur during connection establishment or while connected. */
public sealed interface ConnectionError : BleError

/**
 * Machine-readable reason a BLE connection or operation failed.
 *
 * Platform error codes are mapped once to these constants so callers can
 * dispatch on [when] instead of parsing raw integers or string messages.
 * Retry policies can use transient vs permanent categorization
 * (e.g. [TIMEOUT] and [LINK_LOSS] are retryable; [UNKNOWN_DEVICE] is not).
 */
public enum class ConnectionFailureReason {
    /** The operation did not complete within the configured timeout. Transient - retryable. */
    TIMEOUT,

    /** The radio link dropped unexpectedly. Transient - retryable. */
    LINK_LOSS,

    /** The device could not be found or connectGatt returned null. Permanent for this scan cycle. */
    UNKNOWN_DEVICE,

    /** The peripheral rejected pairing or the bond no longer exists. May be resolved by re-pairing. */
    AUTHENTICATION_FAILED,

    /** The platform GATT stack returned a non-success connection status. Transient if GATT 133. */
    GATT_ERROR,

    /** Bonding was required but the user rejected it or it timed out. */
    BONDING_FAILED,

    /** The peripheral actively refused the connection. Permanent. */
    CONNECTION_REJECTED,

    /** A platform-reported error that does not fit a known category. Inspect [platformCode]. */
    UNKNOWN,
}

/** The initial connection attempt failed before a link was established. */
public data class ConnectionFailed(
    val reason: String,
    val failureReason: ConnectionFailureReason = ConnectionFailureReason.UNKNOWN,
    val platformCode: Int? = null,
    val recoveryHint: String = "Check Bluetooth is enabled and the peripheral is in range.",
) : ConnectionError

/** An established connection was lost unexpectedly. */
public data class ConnectionLost(
    val reason: String,
    val failureReason: ConnectionFailureReason = ConnectionFailureReason.LINK_LOSS,
    val platformCode: Int? = null,
    val recoveryHint: String = "Connection lost. Move closer and retry.",
) : ConnectionError

// --- GATT operation errors ---

/** Errors returned by individual GATT read/write/observe operations. */
public sealed interface GattOperationError : BleError

/** A GATT operation returned a non-success [status]. */
public data class GattError(
    val operation: String,
    val status: GattStatus,
    val recoveryHint: String = "GATT operation failed. Verify the characteristic exists and supports this operation.",
) : GattOperationError

/** Service or characteristic discovery failed on a connected peripheral. */
public data class ServiceDiscoveryError(
    /** UUID of the service that failed discovery, or null if the root discoverServices call failed. */
    val serviceUuid: String? = null,
    val status: GattStatus,
    val recoveryHint: String =
        "Service discovery failed. Verify the peripheral supports the requested services.",
) : GattOperationError

/** A GATT operation targeting a specific characteristic failed. */
public data class CharacteristicError(
    val charUuid: String,
    val operation: String,
    val status: GattStatus,
    val recoveryHint: String =
        "Characteristic operation failed. Verify the characteristic exists and supports this operation.",
) : GattOperationError

// --- Authentication / encryption errors (composable with GattOperationError) ---

/** Errors related to BLE pairing, bonding, or link encryption. */
public sealed interface AuthError : BleError

/** The peripheral rejected the operation because authentication (bonding) failed or is missing. */
public data class AuthenticationFailed(
    val reason: String,
    val platformCode: Int? = null,
    val recoveryHint: String = "Pairing failed. Forget and re-pair in Bluetooth settings.",
) : AuthError,
    GattOperationError

/** The link encryption required for this operation could not be established. */
public data class EncryptionFailed(
    val reason: String,
    val platformCode: Int? = null,
    val recoveryHint: String = "Link encryption failed. Try bonding first, then access encrypted characteristics.",
) : AuthError,
    GattOperationError

// --- Operation constraint errors ---

/** Errors caused by violating a protocol or transport constraint. */
public sealed interface OperationConstraintError : BleError

/** The write payload ([attempted] bytes) exceeds the negotiated MTU ([maximum] bytes). */
public data class MtuExceeded(
    val attempted: Int,
    val maximum: Int,
    val recoveryHint: String = "Payload exceeds negotiated MTU. Reduce payload size or request a larger MTU.",
) : OperationConstraintError

/** A GATT characteristic or descriptor handle is stale - the peripheral disconnected or services were invalidated. */
public data class StaleGattHandle(
    val handleType: String,
    val uuid: String,
    val recoveryHint: String = "GATT handle is stale. Reconnect and re-discover services.",
) : GattOperationError

/** A catch-all for operation failures that don't fit a more specific category. */
public data class OperationFailed(
    val message: String,
    val recoveryHint: String = "Operation failed. Retry. If persistent, disconnect and reconnect to the peripheral.",
) : BleError

/**
 * A BLE operation timed out.
 *
 * [operation] identifies which operation exceeded its configured [timeout].
 * Compare [timeout] against [OperationTimeouts] values to adjust per-operation
 * timeout limits.
 */
public data class PeripheralTimeout(
    /** Human-readable operation name (e.g., "connect", "serviceDiscovery", "read"). */
    val operation: String,
    /** The configured timeout that was exceeded. */
    val timeout: Duration,
    val recoveryHint: String =
        "Operation timed out. Increase the timeout via ConnectionOptions.timeouts " +
            "or verify the device is in range.",
) : BleError

/**
 * Exception wrapper for [BleError] values, allowing them to be thrown as exceptions.
 *
 * All GATT and connection failures surface as [BleException]. Callers should catch
 * this type and inspect [error] for the structured failure:
 * ```
 * try {
 *     peripheral.read(characteristic)
 * } catch (e: BleException) {
 *     when (e.error) {
 *         is GattError -> handleGattError(e.error as GattError)
 *         is ConnectionLost -> handleDisconnect()
 *         else -> handleGenericError(e.error)
 *     }
 * }
 * ```
 */
public data class BleException(
    public val error: BleError,
) : Exception(error.toString())
