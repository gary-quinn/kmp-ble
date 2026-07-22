package com.atruedev.kmpble.mesh.network

import com.atruedev.kmpble.mesh.AccessMessage
import com.atruedev.kmpble.mesh.MeshOpcode

/**
 * Access Layer — formats and parses application-level mesh messages.
 *
 * The Access Layer handles:
 * - Opcode encoding (1, 2, or 3 bytes depending on value)
 * - Application payload formatting
 * - Message type classification (GET, SET, STATUS, etc.)
 */
internal object AccessLayer {
    /**
     * Encode an access message from opcode and parameters.
     */
    fun encode(opcode: MeshOpcode, parameters: ByteArray): ByteArray =
        opcode.toBytes() + parameters

    /**
     * Decode an access message from raw bytes.
     */
    fun decode(data: ByteArray): AccessMessage {
        require(data.isNotEmpty()) { "Access message too short" }

        val opcodeValue = when {
            data[0].toInt() and 0xFF <= 0x7F -> data[0].toInt().toUInt() and 0x7Fu
            data[0].toInt() and 0xFF in 0x80..0xBF -> {
                require(data.size >= 2) { "2-byte opcode too short" }
                ((data[0].toInt() and 0xFF shl 8) or
                    (data[1].toInt() and 0xFF)).toUInt()
            }
            else -> {
                require(data.size >= 3) { "3-byte opcode too short" }
                ((data[0].toInt() and 0xFF shl 16) or
                    (data[1].toInt() and 0xFF shl 8) or
                    (data[2].toInt() and 0xFF)).toUInt()
            }
        }

        val opcode = MeshOpcode(opcodeValue)
        val paramOffset = opcode.byteCount

        return AccessMessage(
            opcode = opcode,
            parameters = data.copyOfRange(paramOffset, data.size),
            isAcknowledged = !isUnacknowledgedOpcode(opcodeValue),
        )
    }

    /**
     * Check if an opcode is unacknowledged (SET_UNACKNOWLEDGED).
     *
     * Unacknowledged opcodes have bit 0 set in the opcode value
     * for SET-type messages.
     */
    private fun isUnacknowledgedOpcode(opcode: UInt): Boolean =
        (opcode and 1u) != 0u
}

/** Known opcodes for Generic OnOff model. */
internal object OnOffOpcodes {
    val GENERIC_ONOFF_GET: MeshOpcode = MeshOpcode(0x8201u)
    val GENERIC_ONOFF_SET: MeshOpcode = MeshOpcode(0x8202u)
    val GENERIC_ONOFF_SET_UNACKNOWLEDGED: MeshOpcode = MeshOpcode(0x8203u)
    val GENERIC_ONOFF_STATUS: MeshOpcode = MeshOpcode(0x8204u)
}

/** Known opcodes for Generic Level model. */
internal object LevelOpcodes {
    val GENERIC_LEVEL_GET: MeshOpcode = MeshOpcode(0x8205u)
    val GENERIC_LEVEL_SET: MeshOpcode = MeshOpcode(0x8206u)
    val GENERIC_LEVEL_SET_UNACKNOWLEDGED: MeshOpcode = MeshOpcode(0x8207u)
    val GENERIC_LEVEL_STATUS: MeshOpcode = MeshOpcode(0x8208u)
}
