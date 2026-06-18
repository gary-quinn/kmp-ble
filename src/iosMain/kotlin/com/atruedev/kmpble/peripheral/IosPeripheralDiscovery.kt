package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.connection.internal.ConnectionEvent
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.peripheral.internal.findCharacteristic
import com.atruedev.kmpble.scanner.uuidFrom
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicPropertyAuthenticatedSignedWrites
import platform.CoreBluetooth.CBCharacteristicPropertyIndicate
import platform.CoreBluetooth.CBCharacteristicPropertyNotify
import platform.CoreBluetooth.CBCharacteristicPropertyRead
import platform.CoreBluetooth.CBCharacteristicPropertyWrite
import platform.CoreBluetooth.CBCharacteristicPropertyWriteWithoutResponse
import platform.CoreBluetooth.CBDescriptor
import platform.CoreBluetooth.CBService
import kotlin.uuid.ExperimentalUuidApi

/**
 * State for a single service discovery cycle, confined to the peripheral's serial dispatcher.
 */
internal data class DiscoveryCycle(
    val generation: Int,
    val pendingServices: Set<String>,
    val discoveredServices: MutableList<DiscoveredService> = mutableListOf(),
)

/**
 * Service discovery pipeline for [IosPeripheral].
 */

@OptIn(ExperimentalUuidApi::class)
internal suspend fun IosPeripheral.handleServicesDiscovered(event: AppleCallbackEvent.DidDiscoverServices) {
    if (event.error != null) {
        val status = event.error.toGattStatus()
        peripheralContext.processEvent(ConnectionEvent.DiscoveryFailed(GattError("discoverServices", status)))
        slots.completeConnect()
        slots.failDiscovery(BleException(GattError("discoverServices", status)))
        currentDiscovery = null
        return
    }

    val cbServices = cbPeripheral.services?.filterIsInstance<CBService>().orEmpty()
    if (cbServices.isEmpty()) {
        finishDiscovery(emptyList())
        currentDiscovery = null
        return
    }

    val generation = discoveryGeneration
    val pending = cbServices.map { it.UUID.UUIDString }.toSet()
    currentDiscovery = DiscoveryCycle(generation = generation, pendingServices = pending)

    cbServices.forEach { bridge.discoverCharacteristics(it.UUID.UUIDString) }
}

@OptIn(ExperimentalUuidApi::class)
internal suspend fun IosPeripheral.handleCharacteristicsDiscovered(
    event: AppleCallbackEvent.DidDiscoverCharacteristics,
) {
    val cycle = currentDiscovery
    if (cycle == null) return // No active discovery cycle (discarded or completed)

    // Ignore stale callbacks from previous discovery generations
    if (cycle.generation != discoveryGeneration) return

    (cycle.pendingServices as MutableSet<String>).remove(event.serviceUuid)

    if (event.error != null) {
        val status = event.error.toGattStatus()
        peripheralContext.processEvent(
            ConnectionEvent.DiscoveryFailed(GattError("discoverCharacteristics", status)),
        )
        currentDiscovery = null
        slots.completeConnect()
        slots.failDiscovery(BleException(GattError("discoverCharacteristics", status)))
        return
    }

    if (cycle.pendingServices.isNotEmpty()) return

    // All services' characteristics discovered - build final service list
    val discovered =
        cbPeripheral.services
            ?.filterIsInstance<CBService>()
            ?.map { it.toDiscoveredService(this) }
            .orEmpty()
    finishDiscovery(discovered)
    currentDiscovery = null
}

@OptIn(ExperimentalUuidApi::class)
internal suspend fun IosPeripheral.finishDiscovery(discovered: List<DiscoveredService>) {
    peripheralContext.processEvent(ConnectionEvent.ServicesDiscovered)
    peripheralContext.updateServices(discovered)
    resubscribeObservations()
    peripheralContext.processEvent(ConnectionEvent.ConfigurationComplete)
    slots.completeConnect()
    slots.completeDiscovery(discovered)
}

@OptIn(ExperimentalUuidApi::class)
internal suspend fun IosPeripheral.resubscribeObservations() {
    for (key in observationManager.getObservationsToResubscribe()) {
        val char = findCharacteristic(key.serviceUuid, key.charUuid)
        if (char != null) enableNotifications(char) else observationManager.completeObservation(key)
    }
}

@OptIn(ExperimentalUuidApi::class)
internal fun CBService.toDiscoveredService(peripheral: IosPeripheral): DiscoveredService {
    val serviceUuid = uuidFrom(UUID.UUIDString)
    val chars =
        characteristics
            ?.filterIsInstance<CBCharacteristic>()
            ?.map { cbChar ->
                val charUuid = uuidFrom(cbChar.UUID.UUIDString)
                val props = cbChar.properties.toInt()
                val descs = mutableListOf<Descriptor>()
                val char =
                    Characteristic(
                        serviceUuid = serviceUuid,
                        uuid = charUuid,
                        properties =
                            Characteristic.Properties(
                                read = (props and CBCharacteristicPropertyRead.toInt()) != 0,
                                write = (props and CBCharacteristicPropertyWrite.toInt()) != 0,
                                writeWithoutResponse =
                                    (props and CBCharacteristicPropertyWriteWithoutResponse.toInt()) != 0,
                                signedWrite =
                                    (props and CBCharacteristicPropertyAuthenticatedSignedWrites.toInt()) != 0,
                                notify = (props and CBCharacteristicPropertyNotify.toInt()) != 0,
                                indicate = (props and CBCharacteristicPropertyIndicate.toInt()) != 0,
                            ),
                        descriptors = descs,
                    )
                peripheral.nativeCharMap[char] = cbChar
                cbChar.descriptors?.filterIsInstance<CBDescriptor>()?.forEach { cbDesc ->
                    val desc = Descriptor(char, uuidFrom(cbDesc.UUID.UUIDString))
                    descs.add(desc)
                    peripheral.nativeDescMap[desc] = cbDesc
                }
                char
            }.orEmpty()

    return DiscoveredService(uuid = serviceUuid, characteristics = chars)
}
