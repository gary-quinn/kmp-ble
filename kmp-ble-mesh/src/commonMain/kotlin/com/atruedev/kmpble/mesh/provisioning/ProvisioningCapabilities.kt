package com.atruedev.kmpble.mesh.provisioning

/**
 * Capabilities reported by an unprovisioned device during provisioning.
 *
 * Received in Phase 2 (Capabilities Exchange) of the provisioning protocol.
 * The 8-byte PDU encodes the device's element count, supported algorithms,
 * and available OOB authentication methods.
 */
public data class ProvisioningCapabilities(
    val numberOfElements: Int,
    val algorithms: Int,
    val publicKeyType: Boolean,
    val staticOobType: Boolean,
    val outputOobActions: Int,
    val outputOobSize: Int,
    val inputOobActions: Int,
    val inputOobSize: Int,
) {
    /** Whether FIPS P-256 ECDH is supported (required by the spec). */
    public val supportsFipsP256: Boolean get() = (algorithms and 1) != 0

    public companion object {
        /** Parse capabilities from a raw PDU (8 bytes after the type byte). */
        public fun fromBytes(data: ByteArray): ProvisioningCapabilities {
            require(data.size >= 8) {
                "Capabilities PDU must be at least 8 bytes, got ${data.size}"
            }
            return ProvisioningCapabilities(
                numberOfElements = data[0].toInt() and 0xFF,
                algorithms = data[1].toInt() and 0xFF,
                publicKeyType = data[2].toInt() != 0,
                staticOobType = data[3].toInt() != 0,
                outputOobActions = data[4].toInt() and 0xFF,
                outputOobSize = data[5].toInt() and 0xFF,
                inputOobActions = data[6].toInt() and 0xFF,
                inputOobSize = data[7].toInt() and 0xFF,
            )
        }
    }
}
