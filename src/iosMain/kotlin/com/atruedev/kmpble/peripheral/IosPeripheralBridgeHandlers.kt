package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.gatt.internal.GattResult
import com.atruedev.kmpble.gatt.internal.PendingOp
import com.atruedev.kmpble.scanner.uuidFrom
import kotlinx.coroutines.launch
import platform.Foundation.NSData

/**
 * Bridge event dispatch and value handling for [IosPeripheral].
 */

internal fun IosPeripheral.handleBridgeEvent(event: AppleCallbackEvent) {
    peripheralContext.scope.launch {
        when (event) {
            is AppleCallbackEvent.DidDiscoverServices -> handleServicesDiscovered(event)
            is AppleCallbackEvent.DidDiscoverCharacteristics -> handleCharacteristicsDiscovered(event)
            is AppleCallbackEvent.DidUpdateValueForCharacteristic -> handleCharacteristicValue(event)
            is AppleCallbackEvent.DidWriteValueForCharacteristic ->
                pendingOps.complete(PendingOp.CharacteristicWrite, event.error.toGattStatus())
            is AppleCallbackEvent.DidUpdateValueForDescriptor -> handleDescriptorValue(event)
            is AppleCallbackEvent.DidWriteValueForDescriptor ->
                pendingOps.complete(PendingOp.DescriptorWrite, event.error.toGattStatus())
            is AppleCallbackEvent.DidReadRSSI -> handleRssi(event)
            is AppleCallbackEvent.DidOpenL2CAPChannel -> handleDidOpenL2CAPChannel(event)
        }
    }
}

/**
 * K/N maps both `didUpdateValue` (read response, notification) and `didWriteValue`
 * (write response) to this single signature. Disambiguate by which slot is armed:
 * the GATT queue ensures only one read/write is pending.
 */
internal fun IosPeripheral.handleCharacteristicValue(event: AppleCallbackEvent.DidUpdateValueForCharacteristic) {
    val cbChar = event.characteristic
    val error = event.error
    when {
        pendingOps.has(PendingOp.CharacteristicWrite) ->
            pendingOps.complete(PendingOp.CharacteristicWrite, error.toGattStatus())
        pendingOps.has(PendingOp.CharacteristicRead) -> {
            val value = cbChar.value?.toByteArray() ?: byteArrayOf()
            pendingOps.complete(PendingOp.CharacteristicRead, GattResult(value, error.toGattStatus()))
        }
        else -> {
            val value = cbChar.value?.toByteArray() ?: return
            val svcUuid = uuidFrom(cbChar.service?.UUID?.UUIDString ?: return)
            val charUuid = uuidFrom(cbChar.UUID.UUIDString)
            observationManager.emitByUuid(svcUuid, charUuid, value)
        }
    }
}

internal fun IosPeripheral.handleDescriptorValue(event: AppleCallbackEvent.DidUpdateValueForDescriptor) {
    val error = event.error
    if (pendingOps.has(PendingOp.DescriptorWrite)) {
        pendingOps.complete(PendingOp.DescriptorWrite, error.toGattStatus())
    } else {
        val value = (event.descriptor.value as? NSData)?.toByteArray() ?: byteArrayOf()
        pendingOps.complete(PendingOp.DescriptorRead, GattResult(value, error.toGattStatus()))
    }
}

internal fun IosPeripheral.handleRssi(event: AppleCallbackEvent.DidReadRSSI) {
    if (event.error == null) {
        pendingOps.complete(PendingOp.RssiRead, event.rssi.intValue)
    } else {
        pendingOps.fail(
            PendingOp.RssiRead,
            BleException(GattError("readRssi", event.error.toGattStatus())),
        )
    }
}
