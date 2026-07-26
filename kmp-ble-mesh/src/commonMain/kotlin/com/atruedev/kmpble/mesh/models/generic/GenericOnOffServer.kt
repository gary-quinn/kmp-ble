package com.atruedev.kmpble.mesh.models.generic

import com.atruedev.kmpble.mesh.*

/**
 * Generic OnOff Server for a local mesh element.
 *
 * Represents a binary-state device on the mesh network. The server
 * maintains the current on/off state and handles incoming GET/SET
 * messages from remote clients.
 */
public class GenericOnOffServer internal constructor(
    private val element: MeshElement,
    private val appKey: ApplicationKey,
) {
    /** Current on/off state. */
    public val state: kotlinx.coroutines.flow.StateFlow<Boolean>
        get() = _state

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(false)

    /**
     * Set the state locally (e.g., from a physical switch).
     */
    public suspend fun setState(on: Boolean) {
        _state.value = on
    }

    /**
     * Handle an incoming message for this model.
     *
     * @param source The unicast address of the sender.
     * @param opcode The received opcode.
     * @param params The message parameters.
     * @return Response payload for acknowledged messages, null for unacknowledged.
     */
    internal fun handleMessage(
        source: MeshAddress.UnicastAddress,
        opcode: MeshOpcode,
        params: ByteArray,
    ): ByteArray? = when (opcode.value) {
        0x8201u -> handleGet()      // Generic OnOff Get
        0x8202u -> handleSet(params) // Generic OnOff Set
        0x8203u -> { handleSet(params); null } // Set Unacknowledged
        else -> null
    }

    private fun handleGet(): ByteArray {
        val present = if (_state.value) 1 else 0
        return byteArrayOf(present.toByte())
    }

    private fun handleSet(params: ByteArray): ByteArray {
        if (params.isNotEmpty()) {
            _state.value = params[0].toInt() != 0
        }
        return handleGet() // Return status
    }
}
