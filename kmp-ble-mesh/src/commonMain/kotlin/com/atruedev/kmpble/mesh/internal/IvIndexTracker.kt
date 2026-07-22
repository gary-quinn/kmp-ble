package com.atruedev.kmpble.mesh.internal

import com.atruedev.kmpble.mesh.IvIndex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks the IV Index and handles IV Update procedures.
 *
 * The IV Index is a 32-bit value propagated network-wide via Secure Network
 * Beacons. It increments during IV Update to prevent nonce reuse when
 * sequence numbers approach their 24-bit limit.
 *
 * During IV Update, the tracker accepts messages using either the current
 * or next IV Index. Once the update is confirmed, it transitions to the
 * new value and resets all sequence numbers.
 */
internal class IvIndexTracker(
    initialIvIndex: IvIndex = IvIndex.INITIAL,
) {
    private val _ivIndex = MutableStateFlow(initialIvIndex)
    val ivIndex: StateFlow<IvIndex> = _ivIndex.asStateFlow()

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    /** The IV Index currently used for sending messages. */
    val currentSendIvIndex: IvIndex get() = _ivIndex.value

    /**
     * Determine the IV Index to use for decrypting an incoming message.
     *
     * If IV Update is in progress, messages with the new IV Index
     * will be decrypted with the next value.
     *
     * @param ivi The IVI bit from the network PDU (0 = normal, 1 = update).
     * @return The IV Index to use for decryption.
     */
    fun resolveReceiveIvIndex(ivi: Int): IvIndex {
        return if (ivi == 0) {
            _ivIndex.value
        } else {
            // IV Update in progress -- use next IV Index
            IvIndex(_ivIndex.value.value - 1u)
        }
    }

    /** Begin IV Update procedure. */
    fun beginUpdate() {
        _isUpdating.value = true
    }

    /** Confirm IV Update completion. */
    fun confirmUpdate() {
        _ivIndex.value = IvIndex(_ivIndex.value.value + 1u)
        _isUpdating.value = false
    }

    /** Get the current value for state persistence. */
    fun snapshot(): IvIndex = _ivIndex.value
}
