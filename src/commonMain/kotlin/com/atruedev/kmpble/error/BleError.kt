package com.atruedev.kmpble.error

public sealed interface BleError

// --- Connection-level errors ---

public sealed interface ConnectionError : BleError

public data class ConnectionFailed(val reason: String, val platformCode: Int? = null) : ConnectionError

public data class ConnectionLost(val reason: String, val platformCode: Int? = null) : ConnectionError

// --- GATT operation errors ---

public sealed interface GattOperationError : BleError

public data class GattError(val operation: String, val status: GattStatus) : GattOperationError

// --- Authentication / encryption errors (composable with GattOperationError) ---

public sealed interface AuthError : BleError

public data class AuthenticationFailed(
    val reason: String,
    val platformCode: Int? = null,
) : AuthError, GattOperationError

public data class EncryptionFailed(val reason: String, val platformCode: Int? = null) : AuthError, GattOperationError

// --- Operation constraint errors ---

public sealed interface OperationConstraintError : BleError

public data class MtuExceeded(val attempted: Int, val maximum: Int) : OperationConstraintError

public data class OperationFailed(val message: String) : BleError
