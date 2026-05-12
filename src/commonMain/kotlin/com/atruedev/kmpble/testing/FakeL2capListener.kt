package com.atruedev.kmpble.testing

import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.l2cap.L2capException
import com.atruedev.kmpble.l2cap.L2capListener
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake [L2capListener] for unit tests.
 *
 * Tests can drive accept events via [simulateIncoming] and inspect state
 * through [psm] / [isOpen].
 */
public class FakeL2capListener(
    private val assignedPsm: Int = 0x80,
) : L2capListener {
    private val _isOpen = MutableStateFlow(false)
    override val isOpen: StateFlow<Boolean> = _isOpen.asStateFlow()

    private var _psm: Int = 0
    override val psm: Int get() = _psm

    private val _incoming = MutableSharedFlow<L2capChannel>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val incoming: Flow<L2capChannel> = _incoming.asSharedFlow()

    private var closed = false

    override suspend fun open(secure: Boolean) {
        if (closed) throw L2capException.InvalidState("Listener has been closed")
        if (_isOpen.value) throw L2capException.InvalidState("Listener already open")
        _psm = assignedPsm
        _isOpen.value = true
    }

    override fun close() {
        closed = true
        _isOpen.value = false
    }

    /** Test helper: emit an accepted [channel] to subscribers of [incoming]. */
    public fun simulateIncoming(channel: L2capChannel) {
        check(_isOpen.value) { "Cannot simulate incoming on a closed listener" }
        _incoming.tryEmit(channel)
    }
}
