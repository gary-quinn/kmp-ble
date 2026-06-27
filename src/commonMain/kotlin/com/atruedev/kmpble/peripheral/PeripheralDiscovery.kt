package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.DiscoveredService
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
public interface PeripheralDiscovery {
    public val services: StateFlow<List<DiscoveredService>?>

    public suspend fun refreshServices(): List<DiscoveredService>

    public fun findCharacteristic(
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
    ): Characteristic?

    public fun findDescriptor(
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
        descriptorUuid: Uuid,
    ): Descriptor?
}
