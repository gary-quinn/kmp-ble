package com.atruedev.kmpble.peripheral

import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBService
import platform.Foundation.setValue

/**
 * CoreBluetooth rebuilds a bonded peripheral's attribute table from its own cache. After the peripheral
 * changes its GATT layout without indicating Service Changed, that cache can put a CBService inside
 * another service's characteristics and close a cycle. CoreBluetooth then recurses forever when it
 * invalidates the table on disconnect, and overflows the stack of the app's process. Dropping every
 * non-characteristic from those lists breaks the cycle. The table is still wrong, so the caller fails
 * discovery with [com.atruedev.kmpble.error.AttributeCacheCorrupted].
 *
 * Must run on CoreBluetooth's delegate queue, the queue the invalidation runs on.
 *
 * @return UUIDs of the services whose characteristics held something else, empty when the table is sound.
 */
internal fun dropForeignCharacteristics(services: List<CBService>): List<String> {
    val repaired = mutableListOf<String>()
    val visited = mutableListOf<CBService>()
    val pending = ArrayDeque(services)
    while (pending.isNotEmpty()) {
        val service = pending.removeFirst()
        if (visited.any { it === service }) continue
        visited += service
        val entries = service.characteristics ?: continue
        val foreign = entries.filterNot { it is CBCharacteristic }
        if (foreign.isEmpty()) continue
        repaired += service.UUID.UUIDString
        // CBService.characteristics is read-only in the public API; key-value coding reaches its storage.
        service.setValue(entries.filterIsInstance<CBCharacteristic>(), forKey = "characteristics")
        foreign.filterIsInstance<CBService>().forEach(pending::addLast)
    }
    return repaired
}
