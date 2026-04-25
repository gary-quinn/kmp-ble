package com.atruedev.kmpble.peripheral.internal

import com.atruedev.kmpble.gatt.DiscoveredService
import kotlinx.coroutines.CompletableDeferred

/**
 * One-shot completion handles for the connect / discovery / disconnect lifecycle.
 *
 * Mutation is confined to the owning peripheral's serial dispatcher
 * (`limitedParallelism(1)`), so no `@Volatile` or other synchronization primitives
 * are required. Each slot rejects re-entrant arming - a second `armConnect()`
 * call while a connect is already in flight throws, surfacing the bug instead
 * of silently overwriting the deferred.
 */
internal class LifecycleSlots {
    private var connect: CompletableDeferred<Unit>? = null
    private var discovery: CompletableDeferred<List<DiscoveredService>>? = null
    private var disconnect: CompletableDeferred<Unit>? = null

    fun armConnect(): CompletableDeferred<Unit> {
        check(connect == null) { "connect() is already in progress on this peripheral" }
        return CompletableDeferred<Unit>().also { connect = it }
    }

    fun armDiscovery(): CompletableDeferred<List<DiscoveredService>> {
        check(discovery == null) { "service discovery is already in progress" }
        return CompletableDeferred<List<DiscoveredService>>().also { discovery = it }
    }

    fun armDisconnect(): CompletableDeferred<Unit> {
        check(disconnect == null) { "disconnect() is already in progress on this peripheral" }
        return CompletableDeferred<Unit>().also { disconnect = it }
    }

    fun completeConnect() {
        connect?.complete(Unit)
        connect = null
    }

    fun completeDiscovery(services: List<DiscoveredService>) {
        discovery?.complete(services)
        discovery = null
    }

    fun failDiscovery(cause: Throwable) {
        discovery?.completeExceptionally(cause)
        discovery = null
    }

    fun completeDisconnect() {
        disconnect?.complete(Unit)
        disconnect = null
    }

    fun clearConnect() {
        connect = null
    }

    fun clearDiscovery() {
        discovery = null
    }

    fun clearDisconnect() {
        disconnect = null
    }
}
