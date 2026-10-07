package com.atruedev.kmpble.peripheral.internal

import com.atruedev.kmpble.error.BleError
import com.atruedev.kmpble.peripheral.state.ConnectionEvent
import com.atruedev.kmpble.peripheral.state.State

/**
 * Records a lost link and drives the state machine all the way to [State.Disconnected].
 *
 * From a Connected.* state one ConnectionLost only reaches Disconnecting.Error; a second
 * one is needed to reach [State.Disconnected.ByError]. Stopping at Disconnecting.Error
 * left the peripheral there for good: the reconnection handler waits for Disconnected, and
 * the next connect()'s ConnectRequested is rejected, so that connect ran with every
 * connection event rejected and never reached Ready.
 */
internal suspend fun PeripheralContext.processLinkLoss(error: BleError) {
    repeat(MAX_LINK_LOSS_TRANSITIONS) {
        if (state.value is State.Disconnected) return
        processEvent(ConnectionEvent.ConnectionLost(error))
    }
}

private const val MAX_LINK_LOSS_TRANSITIONS = 2
