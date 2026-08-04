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
    // Drop callbacks from an interrupted/superseded cycle or a dead connection: a stale
    // callback that slips through would start a second native discovery pass against a
    // newer cycle's (replaced) CBService objects.
    if (!DiscoveryPolicy.acceptsDidDiscoverServices(
            callbackGeneration = event.generation,
            currentGeneration = discoveryGeneration.value,
            cycleAlreadyActive = currentDiscovery != null,
            peripheralConnected = peripheralContext.state.value !is State.Disconnected,
        )
    ) {
        return
    }

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

    val generation = event.generation
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
    val cycle = currentDiscovery ?: return // No active discovery cycle (discarded or completed)
    // Drop callbacks from a superseded generation or a dead connection.
    if (!DiscoveryPolicy.acceptsDidDiscoverCharacteristics(
            cycleGeneration = cycle.generation,
            currentGeneration = discoveryGeneration.value,
            peripheralConnected = peripheralContext.state.value !is State.Disconnected,
        )
    ) {
        return
    }

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

    // A didModifyServices arrived while this cycle was in flight - its publish may predate
    // the table change. Re-run discovery so the next publish reflects the current table;
    // otherwise the invalidation would be silently lost and the stale table cached forever.
    if (servicesChangedWhileDiscovering.value) {
        servicesChangedWhileDiscovering.value = false
        handleServicesModified()
    }
}

internal fun IosPeripheral.canReuseServiceCache(): Boolean {
    val cbServices = cbPeripheral.services?.filterIsInstance<CBService>().orEmpty()
    return DiscoveryPolicy.canReuseServiceCache(
        validated = knownServicesValid.value,
        connectedAtCreation = connectedAtCreation,
        servicesPresent = cbServices.isNotEmpty(),
        allServicesHaveCharacteristics = cbServices.all { it.characteristics != null },
    )
}

/**
 * Finalizes discovery from services CoreBluetooth already has cached on [IosPeripheral.cbPeripheral],
 * skipping a redundant native `discoverServices`/`discoverCharacteristics` round trip. Only valid
 * when [canReuseServiceCache] holds - the cache is complete and either a completed cycle or
 * the peripheral's connected-at-creation state vouches for it.
 */
@OptIn(ExperimentalUuidApi::class)
internal suspend fun IosPeripheral.finishDiscoveryFromCache(cbServices: List<CBService>) {
    finishDiscovery(cbServices.map { it.toDiscoveredService(this) })
}

/**
 * The peripheral's GATT table changed. Cached services/characteristics are no longer
 * trustworthy - invalidate them and, if still connected, re-discover immediately rather
 * than waiting for the next reconnect.
 *
 * Runs a fresh `discoverServices(null)` pass. The CBService objects handed to
 * `peripheral(_:didModifyServices:)` are invalidated handles that Apple documents as no
 * longer usable, so they must never be passed back into discoverCharacteristics.
 *
 * Does NOT arm a connect slot (slots.armConnect) because the peripheral is already
 * connected when didModifyServices fires -- armConnect would throw if the original
 * connect flow still holds the slot. Instead, uses tryArmDiscovery to guard against
 * concurrent discovery cycles; if one is in flight, the invalidation is recorded so
 * [finishDiscovery] re-runs discovery after it completes instead of caching stale services.
 */
internal suspend fun IosPeripheral.handleServicesModified() {
    knownServicesValid.value = false
    if (peripheralContext.state.value !is State.Connected) return
    if (!slots.tryArmDiscovery()) {
        // A discovery cycle is already in flight and will publish pre-invalidation
        // services. Record the invalidation so finishDiscovery re-runs discovery.
        servicesChangedWhileDiscovering.value = true
        return
    }
    discoveryGeneration.incrementAndGet()
    nativeCharMap.clear()
    nativeDescMap.clear()
    currentDiscovery = null
    bridge.discoverServices(discoveryGeneration.value)
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
