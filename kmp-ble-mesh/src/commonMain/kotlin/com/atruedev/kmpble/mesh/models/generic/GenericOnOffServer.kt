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

/**
 * Generic Level Client for controlling variable-level mesh devices.
 *
 * The Generic Level model supports getting and setting a signed 16-bit
 * level value, typically mapped to brightness, volume, or position.
 */
public class GenericLevelClient internal constructor(
    private val network: MeshNetwork,
    private val appKey: ApplicationKey,
) {
    /** Get the current level. Returns -32768 to 32767. */
    public suspend fun get(elementAddress: MeshAddress.UnicastAddress): GenericLevelStatus {
        network.send(elementAddress, MeshModelId.GenericLevelServer,
            com.atruedev.kmpble.mesh.network.LevelOpcodes.GENERIC_LEVEL_GET,
            ByteArray(0), appKey, acknowledged = true)
        return GenericLevelStatus(0)
    }

    /** Set the level (acknowledged). */
    public suspend fun set(elementAddress: MeshAddress.UnicastAddress, level: Int): GenericLevelStatus {
        require(level in -32768..32767) { "Level out of range" }
        val payload = byteArrayOf(
            (level and 0xFF).toByte(),
            ((level shr 8) and 0xFF).toByte(),
        )
        network.send(elementAddress, MeshModelId.GenericLevelServer,
            com.atruedev.kmpble.mesh.network.LevelOpcodes.GENERIC_LEVEL_SET,
            payload, appKey, acknowledged = true)
        return GenericLevelStatus(level)
    }

    /** Set the level (unacknowledged). */
    public suspend fun setUnacknowledged(elementAddress: MeshAddress.UnicastAddress, level: Int) {
        val payload = byteArrayOf(
            (level and 0xFF).toByte(),
            ((level shr 8) and 0xFF).toByte(),
        )
        network.send(elementAddress, MeshModelId.GenericLevelServer,
            com.atruedev.kmpble.mesh.network.LevelOpcodes.GENERIC_LEVEL_SET_UNACKNOWLEDGED,
            payload, appKey, acknowledged = false)
    }
}

/** Current level and optional target during transition. */
public data class GenericLevelStatus(
    val level: Int,
    val targetLevel: Int? = null,
)
