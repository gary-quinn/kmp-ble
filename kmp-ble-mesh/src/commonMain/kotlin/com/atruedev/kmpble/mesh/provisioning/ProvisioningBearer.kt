package com.atruedev.kmpble.mesh.provisioning

import com.atruedev.kmpble.mesh.MeshException
import com.atruedev.kmpble.mesh.ProvisioningFailed
import com.atruedev.kmpble.mesh.ProvisioningPhase
import kotlinx.coroutines.flow.Flow

/**
 * Abstract provisioning bearer for communicating with unprovisioned devices.
 *
 * Two concrete implementations exist:
 * - PB-ADV: Provisioning over the Advertising Bearer
 * - PB-GATT: Provisioning over the GATT Bearer (via Provisioning Service UUID 1827)
 */
public interface ProvisioningBearer : AutoCloseable {
    /** Whether the bearer is currently open for communication. */
    public val isOpen: Boolean

    /** Incoming provisioning PDUs from the unprovisioned device. */
    public val incomingPdus: Flow<ByteArray>

    /** Send a provisioning PDU to the unprovisioned device. */
    public suspend fun send(pdu: ByteArray)

    /** Open the bearer and establish communication. */
    public suspend fun open()

    override fun close()
}

/** Types of provisioning bearers. */
public enum class ProvisioningBearerType {
    /** Provisioning over the Advertising Bearer (PB-ADV). */
    PB_ADV,

    /** Provisioning over the GATT Bearer (PB-GATT, UUID 1827). */
    PB_GATT,
}

/**
 * Exception thrown when provisioning fails at a specific phase.
 */
public class ProvisioningException(
    public val phase: ProvisioningPhase,
    reason: String,
) : Exception("Provisioning failed at $phase: $reason")
