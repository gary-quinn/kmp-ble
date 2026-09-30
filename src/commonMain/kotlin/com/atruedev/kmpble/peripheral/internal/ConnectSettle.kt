package com.atruedev.kmpble.peripheral.internal

import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.ConnectionFailed
import com.atruedev.kmpble.error.ConnectionFailureReason
import com.atruedev.kmpble.peripheral.state.ConnectionEvent
import com.atruedev.kmpble.peripheral.state.State
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeMark

/**
 * Holds connect() until the state is [State.Connected.Ready] or [State.Disconnected].
 *
 * Platforms release the connect slot from their discovery callbacks even when the state
 * machine rejected the discovery events because the state had moved elsewhere (for
 * example into Connecting.Authenticating). Returning then leaves a peripheral that reads
 * and writes but never gets notifications: observations enable their CCCD only once the
 * state is Ready, and the resubscribe pass has already run.
 *
 * [deadline] is the end of the connect budget, not a fresh window, so a stuck connect
 * still fails within about one connect timeout. If the state has not settled by then,
 * [abort] tears the link down, the state is driven to [State.Disconnected.ByError] and
 * the connect fails with [ConnectionFailed]. A connect that already ended in
 * [State.Disconnected] returns as before.
 */
internal suspend fun PeripheralContext.awaitConnectSettled(
    deadline: TimeMark,
    abort: suspend () -> Unit,
) {
    // Checked before the timeout: a transport phase that used the whole budget still
    // returns normally when it ended Ready.
    if (state.value.isConnectSettled()) return
    val settled = withTimeoutOrNull(-deadline.elapsedNow()) { state.first { it.isConnectSettled() } }
    if (settled != null) return

    val error =
        ConnectionFailed(
            "Connection did not become ready before the connect timeout (stuck in ${state.value.displayName})",
            ConnectionFailureReason.TIMEOUT,
        )
    abort()
    // A Connected.* state needs two ConnectionLost events: one into Disconnecting.Error, one out.
    repeat(2) {
        if (state.value !is State.Disconnected) processEvent(ConnectionEvent.ConnectionLost(error))
    }
    throw BleException(error)
}

private fun State.isConnectSettled(): Boolean = this is State.Connected.Ready || this is State.Disconnected
