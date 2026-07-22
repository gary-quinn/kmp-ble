package com.atruedev.kmpble.mesh

import kotlin.time.Duration

/**
 * Root of the BLE Mesh error hierarchy.
 *
 * Errors are organized into composable sealed sub-interfaces following the
 * pattern established by [com.atruedev.kmpble.error.BleError] in the core
 * library. This allows callers to pattern-match at the granularity they need.
 */
public sealed interface MeshError

// --- Provisioning Errors ---

/** Errors that occur during the provisioning process. */
public sealed interface ProvisioningError : MeshError

public data class ProvisioningFailed(
    /** The provisioning phase where failure occurred. */
    val phase: ProvisioningPhase,
    /** Human-readable reason for the failure. */
    val reason: String,
    /** Suggestion for how to recover from this error. */
    val recoveryHint: String =
        "Retry provisioning. Verify the device is in range and not already provisioned.",
) : ProvisioningError

public data class ProvisioningTimeout(
    /** The timeout duration that was exceeded. */
    val timeout: Duration,
    val recoveryHint: String =
        "Increase provisioning timeout or verify device responsiveness.",
) : ProvisioningError

public data class OobAuthenticationFailed(
    /** The OOB method that failed. */
    val method: String,
    val recoveryHint: String =
        "Verify the OOB key matches the device, or try a different OOB method.",
) : ProvisioningError

public data class DeviceAlreadyProvisioned(
    val uuid: String,
    val recoveryHint: String =
        "Device is already provisioned on another network. Reset the device first.",
) : ProvisioningError

/** Phases of the provisioning protocol for error reporting. */
public enum class ProvisioningPhase {
    DISCOVERY,
    CAPABILITIES_EXCHANGE,
    PUBLIC_KEY_EXCHANGE,
    AUTHENTICATION,
    DATA_DISTRIBUTION,
    COMPLETE,
}

// --- Transport Errors ---

/** Errors related to mesh transport (bearer, proxy, network). */
public sealed interface MeshTransportError : MeshError

public data class ProxyConnectionFailed(
    val reason: String,
    val recoveryHint: String =
        "Verify the proxy node is in range and has the proxy feature enabled.",
) : MeshTransportError

public data class ProxyDisconnected(
    val reason: String,
    val recoveryHint: String =
        "Proxy connection lost. Reconnecting automatically if reconnection strategy is configured.",
) : MeshTransportError

public data class MessageTimeout(
    /** Description of the operation that timed out (e.g., "Generic OnOff Get"). */
    val operation: String,
    /** The timeout duration that was exceeded. */
    val timeout: Duration,
    val recoveryHint: String =
        "No response received from mesh node. Check that the device is powered and in range.",
) : MeshTransportError

public data class MessageRejected(
    val reason: String,
    val recoveryHint: String =
        "The mesh message was rejected by the network layer (possible replay or key issue).",
) : MeshTransportError

// --- Crypto Errors ---

/** Errors related to mesh cryptography. */
public sealed interface MeshCryptoError : MeshError

public data class EncryptionFailed(
    val details: String,
    val recoveryHint: String =
        "Encryption failure. Verify that the correct keys are configured for this network.",
) : MeshCryptoError

public data class DecryptionFailed(
    val details: String,
    val recoveryHint: String =
        "Decryption failure. Possible IV Index mismatch, wrong key, or corrupted PDU.",
) : MeshCryptoError

// --- Configuration Errors ---

/** Errors returned by the Configuration Client. */
public sealed interface ConfigurationError : MeshError

public data class ConfigurationRejected(
    /** The configuration opcode that was rejected. */
    val opcode: Int,
    /** Status code returned by the Configuration Server. */
    val statusCode: UByte,
    val recoveryHint: String =
        "Configuration command was rejected by the node. Check that the operation is valid for this node.",
) : ConfigurationError

public data class CompositionDataUnavailable(
    val nodeAddress: MeshAddress.UnicastAddress,
    val recoveryHint: String =
        "Composition data not available. Verify the node is connected and configured.",
) : ConfigurationError

// --- General Errors ---

public data class NodeNotFound(
    val address: MeshAddress.UnicastAddress,
    val recoveryHint: String = "Node not found in network. Verify the unicast address.",
) : MeshError

public data class InvalidParameters(
    val message: String,
    val recoveryHint: String = "Invalid parameters for mesh operation.",
) : MeshError

public data class MeshNotSupported(
    override val message: String = "BLE Mesh is not supported on this platform",
) : Exception(message), MeshError

// --- Exception Wrapper ---

/**
 * Exception wrapper for [MeshError] values, allowing them to be thrown.
 *
 * Pattern matches [com.atruedev.kmpble.error.BleException] from core library.
 */
public data class MeshException(
    public val error: MeshError,
) : Exception(error.toString())
