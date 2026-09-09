package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.ConnectionFailureReason
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.OperationFailed
import com.atruedev.kmpble.error.ServiceDiscoveryError
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.peripheral.internal.findCharacteristic
import com.atruedev.kmpble.peripheral.state.ConnectionEvent
import com.atruedev.kmpble.peripheral.state.State
import com.atruedev.kmpble.scanner.byteArrayFromAny
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
internal fun BlueZPeripheral.handleDevicePropertiesChanged(changed: Map<String, Any?>) {
    if ((changed["Connected"] as? Boolean) == false) {
        peripheralContext.scope.launch {
            if (peripheralContext.state.value is State.Connected ||
                peripheralContext.state.value is State.Connecting
            ) {
                handleRemoteDisconnect()
            }
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
internal fun BlueZPeripheral.handleGattPropertiesChanged(
    path: String,
    changed: Map<String, Any?>,
) {
    if (!changed.containsKey("Value")) return
    val nativeChar = nativeCharPathMap.entries.firstOrNull { (_, charPath) -> charPath == path }?.key ?: return
    val bytes = byteArrayFromAny(changed["Value"]) ?: return
    observationManager.emitByUuid(nativeChar.serviceUuid, nativeChar.uuid, bytes)
}

internal suspend fun BlueZPeripheral.awaitAndFinishDiscovery() {
    val session = requireSession()
    session.refreshGattServices()
    val discovered = session.getGattServices().map { it.toDiscoveredService(this) }
    if (discovered.isEmpty()) {
        val failure = ServiceDiscoveryError(serviceUuid = null, status = com.atruedev.kmpble.error.GattStatus.Failure)
        peripheralContext.processEvent(ConnectionEvent.DiscoveryFailed(failure))
        slots.failDiscovery(BleException(failure))
        slots.completeConnect()
        return
    }
    peripheralContext.processEvent(ConnectionEvent.ServicesDiscovered)
    peripheralContext.updateServices(discovered)
    peripheralContext.processEvent(ConnectionEvent.ConfigurationComplete)
    slots.completeConnect()
    slots.completeDiscovery(discovered)
    // Resubscribe off the connect call stack: gattQueue.enqueue deadlocks when
    // connectInternal already holds peripheralContext.dispatcher.
    peripheralContext.scope.launch {
        resubscribeObservations()
    }
}

internal suspend fun BlueZPeripheral.resubscribeObservations() {
    for (key in observationManager.getObservationsToResubscribe()) {
        val char = services.value.findCharacteristic(key.serviceUuid, key.charUuid)
        if (char != null) {
            enableNotifications(char)
        } else {
            observationManager.completeObservation(key)
        }
    }
}

internal suspend fun BlueZPeripheral.enableNotifications(characteristic: Characteristic) {
    val session = requireSession()
    val charPath = requireNativeCharPath(characteristic)
    peripheralContext.gattQueue.enqueue {
        session.startNotify(charPath).unwrapBlueZWrite("startNotify")
    }
}

internal fun BlueZPeripheral.disableNotificationsBestEffort(characteristic: Characteristic) {
    if (peripheralContext.state.value !is State.Connected) return
    val charPath = nativeCharPathMap[characteristic] ?: return
    peripheralContext.scope.launch {
        runCatching {
            requireSession().stopNotify(charPath)
        }
    }
}

internal suspend fun BlueZPeripheral.handleRemoteDisconnect() {
    peripheralContext.processEvent(ConnectionEvent.RemoteDisconnected)
    onDisconnectCleanup()
    slots.failDiscovery(BleException(ConnectionLost("Remote disconnect", ConnectionFailureReason.LINK_LOSS)))
    slots.completeConnect()
}

internal suspend fun BlueZPeripheral.handleLinkDown(disconnectRequested: Boolean) {
    val bleError =
        if (disconnectRequested) {
            OperationFailed("disconnect")
        } else {
            ConnectionLost("Remote disconnect", ConnectionFailureReason.LINK_LOSS)
        }
    peripheralContext.processEvent(ConnectionEvent.ConnectionLost(bleError))
    if (disconnectRequested) slots.completeDisconnect()
    onDisconnectCleanup()
    slots.failDiscovery(BleException(bleError))
    slots.completeConnect()
}

internal fun Result<Unit>.unwrapBlueZWrite(label: String) {
    onFailure { throw BleException(GattError(label, it.toBlueZGattStatus())) }
}

internal fun Result<ByteArray>.unwrapBlueZRead(label: String): ByteArray =
    getOrElse { throw BleException(GattError(label, it.toBlueZGattStatus())) }
