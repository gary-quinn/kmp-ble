package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.ServiceDiscoveryError
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.peripheral.internal.findCharacteristic
import com.atruedev.kmpble.peripheral.state.ConnectionEvent
import com.atruedev.kmpble.peripheral.state.State
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
    val pendingServices: MutableList<String>,
    val discoveredServices: MutableList<DiscoveredService> = mutableListOf(),
)

/**
 * Service discovery pipeline for [IosPeripheral].
 */

@OptIn(ExperimentalUuidApi::class)
internal suspend fun IosPeripheral.handleServicesDiscovered(event: AppleCallbackEvent.DidDiscoverServices) {
    if (event.error != null) {
        val status = event.error.toGattStatus()
        val discoveryError = ServiceDiscoveryError(serviceUuid = null, status = status)
        peripheralContext.processEvent(ConnectionEvent.DiscoveryFailed(discoveryError))
        slots.completeConnect()
        slots.failDiscovery(BleException(discoveryError))
        currentDiscovery = null
        return
    }

    val cbServices = cbPeripheral.services?.filterIsInstance<CBService>().orEmpty()
    if (cbServices.isEmpty()) {
        finishDiscovery(emptyList())
        currentDiscovery = null
        return
    }

    val generation = discoveryGeneration.value
    // Keep one entry per service, not per UUID - a peripheral may expose more than one
    // service with the same UUID, and each one gets its own didDiscoverCharacteristicsForService
    // callback.
    val pending = cbServices.map { it.UUID.UUIDString }.toMutableList()
    currentDiscovery = DiscoveryCycle(generation = generation, pendingServices = pending)

    cbServices.forEach { bridge.discoverCharacteristics(it) }
}

@OptIn(ExperimentalUuidApi::class)
internal suspend fun IosPeripheral.handleCharacteristicsDiscovered(
    event: AppleCallbackEvent.DidDiscoverCharacteristics,
) {
    val cycle = currentDiscovery
    if (cycle == null) return // No active discovery cycle (discarded or completed)

    // Ignore stale callbacks from previous discovery generations
    if (cycle.generation != discoveryGeneration.value) return

    // Removes only the first matching entry, so a duplicate-UUID service still has
    // its own outstanding entry after this one is cleared.
    cycle.pendingServices.remove(event.serviceUuid)

    if (event.error != null) {
        val status = event.error.toGattStatus()
        peripheralContext.processEvent(
            ConnectionEvent.DiscoveryFailed(ServiceDiscoveryError(serviceUuid = event.serviceUuid, status = status)),
        )
        currentDiscovery = null
        slots.completeConnect()
        slots.failDiscovery(BleException(ServiceDiscoveryError(serviceUuid = event.serviceUuid, status = status)))
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
    knownServicesValid.value = true
    peripheralContext.processEvent(ConnectionEvent.ServicesDiscovered)
    peripheralContext.updateServices(discovered)
    resubscribeObservations()
    peripheralContext.processEvent(ConnectionEvent.ConfigurationComplete)
    slots.completeConnect()
    slots.completeDiscovery(discovered)
}

/**
 * Pure boolean check: whether [cbServices] are usable from cache given the validity flag.
 * Extracted so tests can exercise the logic without constructing an IosPeripheral.
 */
internal fun serviceCacheUsable(
    knownServicesValid: Boolean,
    cbServices: List<CBService>,
): Boolean =
    knownServicesValid &&
        cbServices.isNotEmpty() &&
        cbServices.all { it.characteristics != null }

internal fun IosPeripheral.canReuseServiceCache(): Boolean {
    val cbServices = cbPeripheral.services?.filterIsInstance<CBService>().orEmpty()
    return serviceCacheUsable(knownServicesValid.value, cbServices)
}

/**
 * Finalizes discovery from services CoreBluetooth already has cached on [IosPeripheral.cbPeripheral],
 * skipping a redundant native `discoverServices`/`discoverCharacteristics` round trip. Only valid
 * when [IosPeripheral.knownServicesValid] holds - i.e. no `didModifyServices` callback has
 * invalidated the cache since the last full discovery.
 */
@OptIn(ExperimentalUuidApi::class)
internal suspend fun IosPeripheral.finishDiscoveryFromCache(cbServices: List<CBService>) {
    finishDiscovery(cbServices.map { it.toDiscoveredService(this) })
}

/**
 * The peripheral's GATT table changed. Cached services/characteristics are no longer
 * trustworthy - invalidate them and, if still connected, rediscover immediately rather
 * than waiting for the next reconnect.
 *
 * Does NOT arm a connect slot (slots.armConnect) because the peripheral is already
 * connected when didModifyServices fires -- armConnect would throw if the original
 * connect flow still holds the slot. Instead, uses tryArmDiscovery to guard against
 * concurrent discovery cycles, then runs discoverServices() fire-and-forget like the
 * normal connect path's handleConnectionCallback does. The async callback chain
 * (didDiscoverServices -> didDiscoverCharacteristics -> finishDiscovery) handles
 * completion, including repopulating nativeCharMap and resubscribing observations.
 */
internal suspend fun IosPeripheral.handleServicesModified() {
    knownServicesValid.value = false
    if (peripheralContext.state.value !is State.Connected) return
    if (!slots.tryArmDiscovery()) return
    discoveryGeneration.incrementAndGet()
    nativeCharMap.clear()
    nativeDescMap.clear()
    currentDiscovery = null
    bridge.discoverServices()
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
