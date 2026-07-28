package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.bleDataFromNSData
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.StaleGattHandle
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.internal.NotConnectedException
import com.atruedev.kmpble.internal.StateRestorationHandler
import com.atruedev.kmpble.peripheral.internal.PeripheralRegistry
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBDescriptor
import platform.Foundation.NSData
import kotlin.time.Duration.Companion.seconds

/**
 * Internal helpers for [IosPeripheral] extracted to keep the facade under 300 lines.
 */

internal fun IosPeripheral.checkNotClosed() {
    check(!_closed.value) { "Peripheral is closed" }
}

internal fun IosPeripheral.requireNativeCbChar(c: Characteristic): CBCharacteristic =
    nativeCharMap[c]
        ?: throw BleException(StaleGattHandle("characteristic", c.uuid.toString()))

internal fun IosPeripheral.requireNativeCbDesc(d: Descriptor): CBDescriptor =
    nativeDescMap[d] ?: throw BleException(StaleGattHandle("descriptor", d.uuid.toString()))

internal fun IosPeripheral.onDisconnectCleanup() {
    nativeCharMap.clear()
    nativeDescMap.clear()
    closeL2capChannels()
    observationManager.onDisconnect()
    pendingOps.cancelAll(NotConnectedException())
}

internal fun ByteArray.toNSData(): NSData = BleData(this).nsData

internal fun NSData.toByteArray(): ByteArray = bleDataFromNSData(this).toByteArray()

internal val L2CAP_OPEN_TIMEOUT = 30.seconds
internal val DISCONNECT_TIMEOUT = 5.seconds
internal val DISCOVERY_TIMEOUT = 10.seconds
internal const val ATT_HEADER_SIZE = 3

/**
 * Lifecycle cleanup for [IosPeripheral].
 *
 * Extracted from the main class to keep it under 250 lines. Handles resource
 * disposal, observer deregistration, and state restoration persistence.
 */
internal fun IosPeripheral.closeInternal() {
    if (_closed.value) return
    _closed.value = true
    reconnectionHandler.stop()
    bondManager.stop()
    pairingRequestHandler.closeSync()
    closeL2capChannels()
    centralDelegate.unregisterConnectionCallback(identifier.value)

    // Invalidate in-flight discovery cycle callbacks before teardown.
    discoveryGeneration.incrementAndGet()
    currentDiscovery = null
    bridge.close()

    observationManager.onObservationsChanged = null
    observationManager.clear()
    StateRestorationHandler.default.clearPersistedObservations(identifier.value)
    peripheralContext.close()
    PeripheralRegistry.remove(identifier)
}

/**
 * Service discovery refresh for [IosPeripheral].
 *
 * Extracted from the main class to keep it under 250 lines.
 */
internal suspend fun IosPeripheral.refreshServicesInternal(): List<DiscoveredService> {
    checkNotClosed()
    return withContext(peripheralContext.dispatcher) {
        val deferred = slots.armDiscovery()
        // New discovery cycle: increment generation to invalidate stale callbacks
        discoveryGeneration.incrementAndGet()
        // Clear stale native handle mappings from previous cycle
        nativeCharMap.clear()
        nativeDescMap.clear()
        bridge.discoverServices()
        try {
            withTimeout(currentTimeouts.serviceDiscovery) { deferred.await() }
        } finally {
            slots.clearDiscovery()
        }
    }
}
