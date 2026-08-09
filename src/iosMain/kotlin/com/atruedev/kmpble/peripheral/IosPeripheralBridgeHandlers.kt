package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.gatt.internal.GattResult
import com.atruedev.kmpble.gatt.internal.GenerationSnapshot
import com.atruedev.kmpble.gatt.internal.PendingOp
import com.atruedev.kmpble.scanner.uuidFrom
import kotlinx.coroutines.launch
import platform.Foundation.NSData

/**
 * Bridge event dispatch and value handling for [IosPeripheral].
 */

internal fun IosPeripheral.handleBridgeEvent(event: AppleCallbackEvent) {
    // Runs on the CoreBluetooth delegate queue. Stamp the armed generation per
    // op BEFORE dispatch: the launch below executes later on the serialized
    // dispatcher, where a retry may have re-armed a slot. Completing with the
    // stamped generation makes a stale callback no-op instead of clobbering the
    // retry (see PendingOperations).
    val generations = pendingOps.generationSnapshot()
    peripheralContext.scope.launch {
        when (event) {
            is AppleCallbackEvent.DidDiscoverServices -> handleServicesDiscovered(event)
            is AppleCallbackEvent.DidDiscoverCharacteristics -> handleCharacteristicsDiscovered(event)
            is AppleCallbackEvent.DidUpdateValueForCharacteristic -> handleCharacteristicValue(event, generations)
            is AppleCallbackEvent.DidWriteValueForCharacteristic ->
                pendingOps.complete(
                    PendingOp.CharacteristicWrite,
                    generations[PendingOp.CharacteristicWrite],
                    event.error.toGattStatus(),
                )
            is AppleCallbackEvent.DidUpdateValueForDescriptor -> handleDescriptorValue(event, generations)
            is AppleCallbackEvent.DidWriteValueForDescriptor ->
                pendingOps.complete(
                    PendingOp.DescriptorWrite,
                    generations[PendingOp.DescriptorWrite],
                    event.error.toGattStatus(),
                )
            is AppleCallbackEvent.DidReadRSSI -> handleRssi(event, generations)
            is AppleCallbackEvent.DidOpenL2CAPChannel -> handleDidOpenL2CAPChannel(event)
            is AppleCallbackEvent.DidModifyServices -> handleServicesModified()
        }
    }
}

/**
 * K/N maps both `didUpdateValue` (read response, notification) and `didWriteValue`
 * (write response) to this single signature. Disambiguate by which slot is armed:
 * the GATT queue ensures only one read/write is pending.
 */
internal fun IosPeripheral.handleCharacteristicValue(
    event: AppleCallbackEvent.DidUpdateValueForCharacteristic,
    generations: GenerationSnapshot,
) {
    val cbChar = event.characteristic
    val error = event.error
    when {
        pendingOps.has(PendingOp.CharacteristicWrite) ->
            pendingOps.complete(
                PendingOp.CharacteristicWrite,
                generations[PendingOp.CharacteristicWrite],
                error.toGattStatus(),
            )
        pendingOps.has(PendingOp.CharacteristicRead) -> {
            val value = cbChar.value?.toByteArray() ?: byteArrayOf()
            pendingOps.complete(
                PendingOp.CharacteristicRead,
                generations[PendingOp.CharacteristicRead],
                GattResult(value, error.toGattStatus()),
            )
        }
        else -> {
            val value = cbChar.value?.toByteArray() ?: return
            val svcUuid = uuidFrom(cbChar.service?.UUID?.UUIDString ?: return)
            val charUuid = uuidFrom(cbChar.UUID.UUIDString)
            observationManager.emitByUuid(svcUuid, charUuid, value)
        }
    }
}

internal fun IosPeripheral.handleDescriptorValue(
    event: AppleCallbackEvent.DidUpdateValueForDescriptor,
    generations: GenerationSnapshot,
) {
    val error = event.error
    if (pendingOps.has(PendingOp.DescriptorWrite)) {
        pendingOps.complete(
            PendingOp.DescriptorWrite,
            generations[PendingOp.DescriptorWrite],
            error.toGattStatus(),
        )
    } else {
        val value = (event.descriptor.value as? NSData)?.toByteArray() ?: byteArrayOf()
        pendingOps.complete(
            PendingOp.DescriptorRead,
            generations[PendingOp.DescriptorRead],
            GattResult(value, error.toGattStatus()),
        )
    }
}

internal fun IosPeripheral.handleRssi(
    event: AppleCallbackEvent.DidReadRSSI,
    generations: GenerationSnapshot,
) {
    if (event.error == null) {
        pendingOps.complete(PendingOp.RssiRead, generations[PendingOp.RssiRead], event.rssi.intValue)
    } else {
        pendingOps.fail(
            PendingOp.RssiRead,
            generations[PendingOp.RssiRead],
            BleException(GattError("readRssi", event.error.toGattStatus())),
        )
    }
}
