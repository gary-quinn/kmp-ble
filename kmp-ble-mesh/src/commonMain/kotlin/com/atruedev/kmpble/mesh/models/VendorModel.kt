package com.atruedev.kmpble.mesh.models

import com.atruedev.kmpble.mesh.MeshModelId
import com.atruedev.kmpble.mesh.MeshOpcode

/**
 * Support for vendor-specific mesh models.
 *
 * Vendor models use 32-bit model IDs: (vendorId << 16) | vendorModelId.
 * Opcodes for vendor models use 3-byte encoding (0xC00000-0xFFFFFF).
 *
 * To use a vendor model, create a [VendorModelHandler] and register it
 * with the [com.atruedev.kmpble.mesh.models.ModelDispatcher].
 */
public data class VendorModelId(
    /** SIG-assigned company ID. */
    val companyId: UShort,
    /** Vendor-assigned model identifier. */
    val modelId: UShort,
) {
    /** Combined 32-bit mesh model ID. */
    val meshModelId: MeshModelId get() = MeshModelId.vendor(companyId, modelId)

    /** Opcode base for this vendor model (upper 2 bytes are vendorId). */
    val opcodeBase: UInt get() = companyId.toUInt() shl 16
}

/**
 * Handler for vendor model messages.
 *
 * Implement this interface to handle custom vendor model operations.
 * Register the handler with [ModelDispatcher.register].
 */
public fun interface VendorModelHandler {
    /**
     * Handle an incoming vendor model message.
     *
     * @param opcode The 3-byte opcode.
     * @param parameters The message parameters.
     * @return Response payload for acknowledged messages, or null.
     */
    public suspend fun handleMessage(opcode: MeshOpcode, parameters: ByteArray): ByteArray?
}
