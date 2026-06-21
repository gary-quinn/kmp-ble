package com.atruedev.kmpble.testing

import com.atruedev.kmpble.isochronous.IsochronousChannel
import com.atruedev.kmpble.isochronous.IsochronousException
import com.atruedev.kmpble.isochronous.IsochronousListener
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake [IsochronousListener] for unit tests.
 *
 * Tests can drive accept events via [simulateIncoming] and inspect state
 * through [isOpen].
 */
public class FakeIsochronousListener : IsochronousListener {
    private val _isOpen = MutableStateFlow(false)
    override val isOpen: StateFlow<Boolean> = _isOpen.asStateFlow()

    private val _incoming =
        MutableSharedFlow<IsochronousChannel>(
            replay = 0,
            extraBufferCapacity = 16,
        )
    override val incoming: SharedFlow<IsochronousChannel> = _incoming.asSharedFlow()

    private var closed = false
    private var openCount = 0

    override suspend fun open(
        secure: Boolean,
        mtu: Int?,
    ) {
        if (closed) throw IsochronousException.OpenFailed("Listener has been closed")
        if (_isOpen.value) throw IsochronousException.OpenFailed("Listener already open")
        _isOpen.value = true
        openCount++
    }

    override fun close() {
        closed = true
        _isOpen.value = false
    }

    /**
     * Test helper: emit an accepted [channel] to subscribers of [incoming].
     *
     * Must start the collector BEFORE emitting (SharedFlow with replay=0
     * suspends on emit if no subscriber has available buffer capacity).
     */
    public suspend fun simulateIncoming(channel: IsochronousChannel) {
        check(_isOpen.value) { "Cannot simulate incoming on a closed listener" }
        _incoming.emit(channel)
    }
}
