package com.atruedev.kmpble.mesh.models.generic

import com.atruedev.kmpble.mesh.*

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
