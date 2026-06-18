package com.atruedev.kmpble.error

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

/** The initial connection attempt failed before a link was established. */
public data class ConnectionFailed(
    val reason: String,
    val platformCode: Int? = null,
    val recoveryHint: String = "Check Bluetooth is enabled and the peripheral is in range.",
) : ConnectionError

/** An established connection was lost unexpectedly. */
public data class ConnectionLost(
    val reason: String,
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
