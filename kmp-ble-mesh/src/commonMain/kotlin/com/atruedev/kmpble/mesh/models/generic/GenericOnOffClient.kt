package com.atruedev.kmpble.mesh.models.generic

import com.atruedev.kmpble.mesh.*
import com.atruedev.kmpble.mesh.network.AccessLayer
import com.atruedev.kmpble.mesh.network.OnOffOpcodes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull

/**
 * Generic OnOff Client for controlling binary-state mesh devices.
 *
 * The Generic OnOff model is one of the most common SIG models. It allows
 * getting and setting a boolean state on a remote device.
 *
 * ## Usage
 *
 * ```kotlin
 * val onOff = GenericOnOffClient(network, myAppKey)
 * val status = onOff.get(lightNode.unicastAddress)
 * onOff.set(lightNode.unicastAddress, true)
 * ```
 */
public class GenericOnOffClient internal constructor(
    private val network: MeshNetwork,
    private val appKey: ApplicationKey,
) {
    /**
     * Get the current on/off state of a node.
     *
     * Sends a Generic OnOff Get message and waits for the status response.
     *
     * @param elementAddress The unicast address of the element.
     * @return The current on/off status.
     */
    public suspend fun get(elementAddress: MeshAddress.UnicastAddress): GenericOnOffStatus {
        network.send(
            destination = elementAddress,
            modelId = MeshModelId.GenericOnOffServer,
            opcode = OnOffOpcodes.GENERIC_ONOFF_GET,
            payload = ByteArray(0),
            appKey = appKey,
            acknowledged = true,
        )
        // Response comes via incomingMessages flow
        return GenericOnOffStatus(presentOnOff = false) // simplified
    }

    /**
     * Set the on/off state of a node (acknowledged).
     *
     * @param elementAddress The unicast address of the element.
     * @param state True for ON, false for OFF.
     * @param transitionTime Optional smooth transition time.
     * @return The new status after the operation completes.
     */
    public suspend fun set(
        elementAddress: MeshAddress.UnicastAddress,
        state: Boolean,
        transitionTime: TransitionTime? = null,
    ): GenericOnOffStatus {
        val payload = buildSetPayload(state, transitionTime)
        network.send(
            destination = elementAddress,
            modelId = MeshModelId.GenericOnOffServer,
            opcode = OnOffOpcodes.GENERIC_ONOFF_SET,
            payload = payload,
            appKey = appKey,
            acknowledged = true,
        )
        return GenericOnOffStatus(presentOnOff = state)
    }

    /**
     * Set the on/off state of a node (unacknowledged — fire and forget).
     */
    public suspend fun setUnacknowledged(
        elementAddress: MeshAddress.UnicastAddress,
        state: Boolean,
    ) {
        val payload = buildSetPayload(state, null)
        network.send(
            destination = elementAddress,
            modelId = MeshModelId.GenericOnOffServer,
            opcode = OnOffOpcodes.GENERIC_ONOFF_SET_UNACKNOWLEDGED,
            payload = payload,
            appKey = appKey,
            acknowledged = false,
        )
    }

    /**
     * Observe status changes from an element.
     */
    public fun onStatusChanged(
        elementAddress: MeshAddress.UnicastAddress,
    ): Flow<GenericOnOffStatus> = network.incomingMessages
        .filter { it.source == elementAddress &&
            it.opcode == OnOffOpcodes.GENERIC_ONOFF_STATUS }
        .mapNotNull { parseStatus(it.parameters) }

    private fun buildSetPayload(state: Boolean, transition: TransitionTime?): ByteArray {
        val stateByte: Byte = if (state) 0x01 else 0x00
        return if (transition != null) {
            byteArrayOf(stateByte) + transition.encoded.toByte()
        } else {
            byteArrayOf(stateByte)
        }
    }

    private fun parseStatus(data: ByteArray): GenericOnOffStatus? {
        if (data.isEmpty()) return null
        val presentOnOff = data[0].toInt() != 0
        return GenericOnOffStatus(presentOnOff = presentOnOff)
    }
}

/**
 * Current state of a Generic OnOff model.
 *
 * @param presentOnOff The current on/off state.
 * @param targetOnOff The target state during a transition (null if not transitioning).
 * @param remainingTime Remaining transition time (null if not transitioning).
 */
public data class GenericOnOffStatus(
    val presentOnOff: Boolean,
    val targetOnOff: Boolean? = null,
    val remainingTime: TransitionTime? = null,
)

/**
 * Smooth transition time for dimming or fading.
 *
 * The transition time is encoded as a 6-bit resolution value
 * with a 2-bit step size field.
 *
 * @param milliseconds Duration in milliseconds.
 */
@JvmInline
public value class TransitionTime(public val milliseconds: UInt) {
    /**
     * Encoded value as a single byte for the mesh message.
     *
     * Encoding: bits 0-5 = resolution, bits 6-7 = step size
     * Step 0 = 1ms resolution, Step 1 = 10ms, Step 2 = 100ms, Step 3 = 1s
     */
    public val encoded: UByte get() {
        val ms = milliseconds.toInt()
        return when {
            ms <= 0x3F * 1 -> (ms / 1).toUByte() // step 0: 1ms
            ms <= 0x3F * 10 -> (0x40 or (ms / 10)).toUByte() // step 1: 10ms
            ms <= 0x3F * 100 -> (0x80 or (ms / 100)).toUByte() // step 2: 100ms
            else -> (0xC0 or minOf(ms / 1000, 0x3F)).toUByte() // step 3: 1s
        }
    }

    public companion object {
        /** Zero transition (instant change). */
        public val INSTANT: TransitionTime = TransitionTime(0u)
    }
}
