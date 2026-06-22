package com.atruedev.kmpble.connection

/**
 * Result of a [com.atruedev.kmpble.peripheral.Peripheral.requestConnectionSubrating]
 * call.
 */
public sealed interface ConnectionSubratingResult {
    /**
     * The subrating request was accepted and the platform returned the
     * successfully negotiated parameters.
     */
    public data class Accepted(
        val parameters: ConnectionSubratingParameters,
    ) : ConnectionSubratingResult

    /**
     * Connection subrating is not supported on this platform or OS version.
     */
    public data object NotSupported : ConnectionSubratingResult

    /**
     * The peripheral rejected the subrating request or the operation failed.
     */
    public data class Rejected(
        val reason: String? = null,
    ) : ConnectionSubratingResult
}
