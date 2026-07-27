package com.atruedev.kmpble.mesh.models.generic

import com.atruedev.kmpble.mesh.*
import com.atruedev.kmpble.mesh.network.LevelOpcodes

/**
 * Generic Level Server for a local mesh element.
 *
 * Represents a variable-level device on the mesh network (e.g., dimmable
 * light, volume control, position servo). The server maintains the current
 * signed 16-bit level and handles incoming GET/SET messages from remote
 * clients.
 *
 * Level range: -32768 to 32767. Common mappings include:
 * - 0x0000 = off, 0x7FFF = full brightness (lighting)
 * - 0x0000 = mute, 0x7FFF = max volume (audio)
 */
public class GenericLevelServer internal constructor(
    private val element: MeshElement,
    private val appKey: ApplicationKey,
) {
    /** Current level state (-32768 to 32767). */
    public val state: kotlinx.coroutines.flow.StateFlow<Int>
        get() = _state

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(0)

    /** Minimum allowed level. */
    public var minLevel: Int = -32768
        private set

    /** Maximum allowed level. */
    public var maxLevel: Int = 32767
        private set

    /**
     * Set the level range for this server.
     */
    public fun setRange(min: Int, max: Int) {
        require(min in -32768..32767) { "minLevel out of range" }
        require(max in -32768..32767) { "maxLevel out of range" }
        require(min <= max) { "minLevel must be <= maxLevel" }
        minLevel = min
        maxLevel = max
    }

    /**
     * Set the level locally (e.g., from a physical knob or slider).
     *
     * The value is clamped to [minLevel]..[maxLevel].
     */
    public suspend fun setLevel(level: Int) {
        _state.value = level.coerceIn(minLevel, maxLevel)
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
        LevelOpcodes.GENERIC_LEVEL_GET.value -> handleGet()
        LevelOpcodes.GENERIC_LEVEL_SET.value -> handleSet(params)
        LevelOpcodes.GENERIC_LEVEL_SET_UNACKNOWLEDGED.value -> {
            handleSet(params); null
        }
        else -> null
    }

    private fun handleGet(): ByteArray {
        val level = _state.value
        return byteArrayOf(
            (level and 0xFF).toByte(),
            ((level shr 8) and 0xFF).toByte(),
        )
    }

    private fun handleSet(params: ByteArray): ByteArray {
        if (params.size >= 2) {
            val rawLevel = ((params[1].toInt() and 0xFF) shl 8) or
                (params[0].toInt() and 0xFF)
            // Sign-extend from 16-bit
            val level = if (rawLevel > 32767) rawLevel - 65536 else rawLevel
            _state.value = level.coerceIn(minLevel, maxLevel)
        }
        return handleGet() // Return status
    }
}
