package com.atruedev.kmpble.mesh.models

import com.atruedev.kmpble.mesh.*

/**
 * Routes incoming mesh messages to registered model handlers.
 *
 * The ModelDispatcher matches incoming access messages to model handlers
 * based on opcode. Standard SIG model opcodes are built-in; vendor model
 * opcodes can be registered dynamically.
 */
internal class ModelDispatcher {
    private val handlers = mutableMapOf<UInt, suspend (MeshMessage) -> ByteArray?>()

    /**
     * Register a handler for a specific opcode.
     *
     * @param opcode The opcode to handle.
     * @param handler Function that receives the message and returns
     *   a response payload (for acknowledged messages) or null.
     */
    fun register(opcode: MeshOpcode, handler: suspend (MeshMessage) -> ByteArray?) {
        handlers[opcode.value] = handler
    }

    /**
     * Register handlers for a standard model.
     *
     * @param handlers Map of opcode to handler function.
     */
    fun registerModel(handlers: Map<MeshOpcode, suspend (MeshMessage) -> ByteArray?>) {
        handlers.forEach { (opcode, handler) -> register(opcode, handler) }
    }

    /**
     * Dispatch an incoming message to the appropriate handler.
     *
     * @param message The incoming mesh message.
     * @return Response payload if the message was acknowledged and handled,
     *   or null if no handler is registered.
     */
    suspend fun dispatch(message: MeshMessage): ByteArray? {
        val handler = handlers[message.opcode.value] ?: return null
        return handler(message)
    }

    /** Check if an opcode has a registered handler. */
    fun hasHandler(opcode: MeshOpcode): Boolean =
        handlers.containsKey(opcode.value)

    /** Remove all registered handlers. */
    fun clear() { handlers.clear() }
}
