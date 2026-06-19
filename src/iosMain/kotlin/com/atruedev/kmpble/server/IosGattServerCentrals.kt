package com.atruedev.kmpble.server

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import platform.CoreBluetooth.CBCentral

// --- Central tracking + sweep ---

/** Track or refresh a central. Returns `true` if newly discovered. */
internal fun IosGattServer.trackCentral(central: CBCentral): Boolean {
    val id = central.id
    val isNew = connectedCentrals.trackOrRefresh(id, central)
    if (isNew) {
        _connections.update { it + ServerConnection(Identifier(id)) }
        logEvent(
            BleLogEvent.ServerClientEvent(
                Identifier(id),
                "connected (${connectedCentrals.size} total)",
            ),
        )
    }
    return isNew
}

internal suspend fun IosGattServer.runSweepLoop() {
    while (true) {
        delay(centralSweepInterval)
        try {
            evictIdleCentrals()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logEvent(BleLogEvent.Error(null, "central sweep failed", e))
        }
    }
}

internal suspend fun IosGattServer.evictIdleCentrals() {
    val evicted = connectedCentrals.evictIdle()
    for ((id, _) in evicted) {
        subscriptions.values.forEach { it.remove(id) }
        val identifier = Identifier(id)
        _connections.update { list -> list.filter { it.device != identifier } }
        _connectionEvents.emit(ServerConnectionEvent.Disconnected(identifier))
        logEvent(
            BleLogEvent.ServerClientEvent(identifier, "evicted (idle > $centralIdleTimeout)"),
        )
    }
}
