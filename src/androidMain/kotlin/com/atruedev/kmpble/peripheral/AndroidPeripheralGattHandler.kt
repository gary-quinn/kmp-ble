@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.peripheral

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import com.atruedev.kmpble.connection.ConnectionSubratingParameters
import com.atruedev.kmpble.connection.ConnectionSubratingResult
import com.atruedev.kmpble.connection.PhyUpdate
import com.atruedev.kmpble.peripheral.state.ConnectionState
import com.atruedev.kmpble.peripheral.state.StateTransitionEvent
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.ServiceDiscoveryError
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.gatt.internal.CCCD_UUID
import com.atruedev.kmpble.gatt.internal.DISABLE_NOTIFICATION_VALUE
import com.atruedev.kmpble.gatt.internal.ENABLE_INDICATION_VALUE
import com.atruedev.kmpble.gatt.internal.ENABLE_NOTIFICATION_VALUE
import com.atruedev.kmpble.gatt.internal.GattResult
import com.atruedev.kmpble.gatt.internal.PendingOp
import com.atruedev.kmpble.gatt.internal.PhyUpdateResult
import com.atruedev.kmpble.peripheral.internal.awaitGatt
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.uuid.Uuid

/**
 * GATT event dispatch and notification management for [AndroidPeripheral].
 * Extracted to keep the facade under 300 lines.
 */

internal fun AndroidPeripheral.handleGattEvent(event: GattCallbackEvent) {
    peripheralContext.scope.launch {
        when (event) {
            is GattCallbackEvent.ConnectionStateChanged -> handleConnectionStateChanged(event)
            is GattCallbackEvent.ServicesDiscovered -> handleServicesDiscovered(event)
            is GattCallbackEvent.MtuChanged -> handleMtuChanged(event)
            is GattCallbackEvent.CharacteristicRead ->
                pendingOps.complete(
                    PendingOp.CharacteristicRead,
                    GattResult(event.value, event.status.toGattStatus()),
                )
            is GattCallbackEvent.CharacteristicWrite ->
                pendingOps.complete(PendingOp.CharacteristicWrite, event.status.toGattStatus())
            is GattCallbackEvent.CharacteristicChanged -> {
                val charUuid = Uuid.parse(event.characteristic.uuid.toString())
                val serviceUuid =
                    Uuid.parse(
                        event.characteristic.service.uuid
                            .toString(),
                    )
                observationManager.emitByUuid(serviceUuid, charUuid, event.value)
            }
            is GattCallbackEvent.DescriptorRead ->
                pendingOps.complete(
                    PendingOp.DescriptorRead,
                    GattResult(event.value, event.status.toGattStatus()),
                )
            is GattCallbackEvent.DescriptorWrite ->
                pendingOps.complete(PendingOp.DescriptorWrite, event.status.toGattStatus())
            is GattCallbackEvent.ReadRemoteRssi -> handleRssiResult(event)
            is GattCallbackEvent.PhyUpdated -> handlePhyUpdated(event)
            is GattCallbackEvent.PhyRead -> handlePhyRead(event)
            is GattCallbackEvent.SubrateChanged -> handleSubrateChanged(event)
        }
    }
}

internal suspend fun AndroidPeripheral.handleServicesDiscovered(event: GattCallbackEvent.ServicesDiscovered) {
    val status = event.status.toGattStatus()
    if (!status.isSuccess()) {
        val discoveryError = ServiceDiscoveryError(serviceUuid = null, status = status)
        peripheralContext.processEvent(StateTransitionEvent.DiscoveryFailed(discoveryError))
        slots.completeConnect()
        slots.failDiscovery(BleException(discoveryError))
        return
    }

    val discovered = event.services.map { it.toDiscoveredService(this) }
    peripheralContext.processEvent(StateTransitionEvent.ServicesDiscovered)
    peripheralContext.updateServices(discovered)
    resubscribeObservations()
    peripheralContext.processEvent(StateTransitionEvent.ConfigurationComplete)
    slots.completeConnect()
    slots.completeDiscovery(discovered)
}

internal suspend fun AndroidPeripheral.resubscribeObservations() {
    for (key in observationManager.getObservationsToResubscribe()) {
        val char = findCharacteristic(key.serviceUuid, key.charUuid)
        if (char != null) enableNotifications(char) else observationManager.completeObservation(key)
    }
}

internal suspend fun AndroidPeripheral.handleMtuChanged(event: GattCallbackEvent.MtuChanged) {
    if (event.status.toGattStatus().isSuccess()) peripheralContext.updateMtu(event.mtu)
    pendingOps.complete(PendingOp.MtuRequest, event.mtu)
}

internal fun AndroidPeripheral.handleRssiResult(event: GattCallbackEvent.ReadRemoteRssi) {
    val status = event.status.toGattStatus()
    if (status.isSuccess()) {
        pendingOps.complete(PendingOp.RssiRead, event.rssi)
    } else {
        pendingOps.fail(PendingOp.RssiRead, BleException(GattError("readRssi", status)))
    }
}

internal fun AndroidPeripheral.handlePhyUpdated(event: GattCallbackEvent.PhyUpdated) {
    val status = event.status.toGattStatus()
    val phyUpdate =
        PhyUpdate(
            txPhy = phyConstantToPhy(event.txPhy),
            rxPhy = phyConstantToPhy(event.rxPhy),
        )
    if (pendingOps.has(PendingOp.PhyUpdate)) {
        pendingOps.complete(
            PendingOp.PhyUpdate,
            PhyUpdateResult(
                txPhyConstant = event.txPhy,
                rxPhyConstant = event.rxPhy,
                status = status,
            ),
        )
    }
    _phyUpdate.tryEmit(phyUpdate)
}

internal fun AndroidPeripheral.handlePhyRead(event: GattCallbackEvent.PhyRead) {
    if (pendingOps.has(PendingOp.PhyRead)) {
        val status = event.status.toGattStatus()
        pendingOps.complete(
            PendingOp.PhyRead,
            PhyUpdateResult(
                txPhyConstant = event.txPhy,
                rxPhyConstant = event.rxPhy,
                status = status,
            ),
        )
    }
}

internal fun AndroidPeripheral.handleSubrateChanged(event: GattCallbackEvent.SubrateChanged) {
    val status = event.status.toGattStatus()
    if (pendingOps.has(PendingOp.SubrateRequest)) {
        if (status.isSuccess()) {
            pendingOps.complete(
                PendingOp.SubrateRequest,
                ConnectionSubratingResult.Accepted(
                    ConnectionSubratingParameters(
                        subrateFactor = event.subrateFactor,
                        subrateLatency = event.subrateLatency,
                        continuationNumber = event.continuationNumber,
                        supervisionTimeout = event.supervisionTimeout,
                    ),
                ),
            )
        } else {
            pendingOps.complete(
                PendingOp.SubrateRequest,
                ConnectionSubratingResult.Rejected("status=$status"),
            )
        }
    }
}

internal fun android.bluetooth.BluetoothGattService.toDiscoveredService(
    peripheral: AndroidPeripheral,
): DiscoveredService {
    val svcUuid = Uuid.parse(uuid.toString())
    return DiscoveredService(
        uuid = svcUuid,
        characteristics =
            characteristics.map { nativeChar ->
                val char = nativeChar.toCharacteristic(svcUuid)
                peripheral.nativeCharMap[char] = nativeChar
                char.descriptors.forEachIndexed { i, desc ->
                    if (i < nativeChar.descriptors.size) peripheral.nativeDescMap[desc] = nativeChar.descriptors[i]
                }
                char
            },
    )
}

internal fun BluetoothGattCharacteristic.toCharacteristic(serviceUuid: Uuid): Characteristic {
    val charUuid = Uuid.parse(uuid.toString())
    val props =
        Characteristic.Properties(
            read = (properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0,
            write = (properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0,
            writeWithoutResponse = (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0,
            signedWrite = (properties and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE) != 0,
            notify = (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0,
            indicate = (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0,
        )
    val descs = mutableListOf<Descriptor>()
    val char = Characteristic(serviceUuid, charUuid, props, descs)
    descriptors.forEach { descs.add(Descriptor(char, Uuid.parse(it.uuid.toString()))) }
    return char
}

internal suspend fun AndroidPeripheral.enableNotifications(characteristic: Characteristic) {
    val native = requireNativeChar(characteristic)
    bridge.setCharacteristicNotification(native, true)
    val cccd = native.getDescriptor(UUID.fromString(CCCD_UUID.toString())) ?: return
    val value = if (characteristic.properties.indicate) ENABLE_INDICATION_VALUE else ENABLE_NOTIFICATION_VALUE
    peripheralContext.gattQueue.enqueue {
        val status =
            pendingOps.awaitGatt(PendingOp.DescriptorWrite, "enableNotifications") {
                bridge.writeDescriptor(cccd, value)
            }
        if (!status.isSuccess()) throw BleException(GattError("enableNotifications", status))
    }
}

/**
 * Best-effort CCCD disable. Failures during flow completion must not propagate
 * back into the consumer's collector.
 */
internal fun AndroidPeripheral.disableNotificationsBestEffort(characteristic: Characteristic) {
    if (peripheralContext.state.value !is ConnectionState.Connected) return
    val native = nativeCharMap[characteristic] ?: return
    bridge.setCharacteristicNotification(native, false)
    val cccd = native.getDescriptor(UUID.fromString(CCCD_UUID.toString())) ?: return
    peripheralContext.scope.launch {
        try {
            peripheralContext.gattQueue.enqueue {
                pendingOps.awaitGatt(PendingOp.DescriptorWrite, "disableNotifications") {
                    bridge.writeDescriptor(cccd, DISABLE_NOTIFICATION_VALUE)
                }
            }
        } catch (_: Throwable) {
            // best-effort
        }
    }
}

internal fun WriteType.toAndroidWriteType(): Int =
    when (this) {
        WriteType.WithResponse -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        WriteType.WithoutResponse -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        WriteType.Signed -> BluetoothGattCharacteristic.WRITE_TYPE_SIGNED
    }
