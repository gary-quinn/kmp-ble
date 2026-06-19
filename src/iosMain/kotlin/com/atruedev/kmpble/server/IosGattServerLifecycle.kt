package com.atruedev.kmpble.server

import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import platform.CoreBluetooth.CBPeripheralManagerStatePoweredOn
import platform.Foundation.NSError

// --- Lifecycle (open / close / delegate setup) ---

internal suspend fun IosGattServer.openLifecycle() {
    if (isClosed.value != 0) {
        throw ServerException.OpenFailed(
            "This server instance has been closed and cannot be reopened. " +
                "Create a new instance via GattServer { ... } factory.",
        )
    }

    withContext(dispatcher) {
        if (isOpen.value != 0) return@withContext

        if (!IosGattServer.instanceLock.compareAndSet(0, 1)) {
            throw ServerException.OpenFailed(
                "Another IosGattServer is already open. iOS uses a single " +
                    "CBPeripheralManager - call close() on the existing server first.",
            )
        }

        logEvent(BleLogEvent.ServerLifecycle("opening"))

        try {
            openInternalLifecycle()
        } catch (e: Exception) {
            IosGattServer.instanceLock.value = 0
            throw e
        }
    }
}

private suspend fun IosGattServer.openInternalLifecycle() {
    setDelegateCallbacks(active = true)

    // Force lazy CBPeripheralManager init - the constructor fires
    // peripheralManagerDidUpdateState on our delegate.
    manager

    try {
        withTimeout(POWER_ON_TIMEOUT_MS) {
            delegate.managerState.first { it == CBPeripheralManagerStatePoweredOn }
        }
    } catch (_: TimeoutCancellationException) {
        setDelegateCallbacks(active = false)
        throw ServerException.OpenFailed(
            "Timeout waiting for Bluetooth to power on (state: ${delegate.managerState.value})",
        )
    }

    for (serviceDef in serviceDefinitions) {
        for (charDef in serviceDef.characteristics) {
            charDef.readHandler?.let { readHandlers[charDef.uuid] = it }
            charDef.writeHandler?.let { writeHandlers[charDef.uuid] = it }
        }
    }

    for (serviceDef in serviceDefinitions) {
        val nativeService = buildNativeService(serviceDef, characteristicCache)
        val deferred = CompletableDeferred<NSError?>()
        pendingServiceAdd = deferred
        manager.addService(nativeService)

        try {
            val error = withTimeout(SERVICE_ADD_TIMEOUT_MS) { deferred.await() }
            pendingServiceAdd = null
            if (error != null) {
                throw ServerException.OpenFailed(
                    "addService failed for ${serviceDef.uuid}: ${error.localizedDescription}",
                )
            }
        } catch (e: TimeoutCancellationException) {
            pendingServiceAdd = null
            throw ServerException.OpenFailed(
                "Timeout adding service ${serviceDef.uuid}",
            )
        }

        logEvent(BleLogEvent.ServerLifecycle("service added: ${serviceDef.uuid}"))
    }

    isOpen.value = 1

    scope.launch { runSweepLoop() }

    logEvent(
        BleLogEvent.ServerLifecycle("open (${serviceDefinitions.size} services)"),
    )
}

internal fun IosGattServer.closeLifecycle() {
    if (isClosed.compareAndSet(0, 1).not()) return

    val wasOpen = isOpen.compareAndSet(1, 0)
    if (!wasOpen) {
        setDelegateCallbacks(active = false)
        return
    }

    logEvent(BleLogEvent.ServerLifecycle("closing"))

    // Stop accepting new callbacks before cancelling scope - prevents
    // a GCD-queued callback from scope.launch-ing into a cancelled scope.
    setDelegateCallbacks(active = false)

    manager.removeAllServices()

    readyToUpdate.cancel(CancellationException("Server closed"))

    // Don't clear collections here - races with in-flight coroutines before cancellation.
    scope.cancel()
    _connections.value = emptyList()

    IosGattServer.instanceLock.value = 0
    logEvent(BleLogEvent.ServerLifecycle("closed"))
}

internal fun IosGattServer.setDelegateCallbacks(active: Boolean) {
    delegate.onServiceAdded = if (active) this::handleServiceAdded else null
    delegate.onReadRequest = if (active) this::handleReadRequest else null
    delegate.onWriteRequests = if (active) this::handleWriteRequests else null
    delegate.onSubscribe = if (active) this::handleSubscribe else null
    delegate.onReadyToUpdate = if (active) this::handleReadyToUpdate else null
}
