package com.atruedev.kmpble.peripheral

import platform.CoreBluetooth.CBAttributePermissionsReadable
import platform.CoreBluetooth.CBCharacteristicPropertyRead
import platform.CoreBluetooth.CBMutableCharacteristic
import platform.CoreBluetooth.CBMutableService
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBUUID
import platform.Foundation.setValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class AttributeCacheRepairTest {
    private fun service(uuid: String) = CBMutableService(type = CBUUID.UUIDWithString(uuid), primary = true)

    // The public setter rejects services (it calls setService: on every entry), so the corrupted cache is written
    // straight into the backing ivar, as CoreBluetooth's cache rebuild does.
    private fun CBService.cacheCharacteristics(entries: List<Any>) = setValue(entries, forKey = "_characteristics")

    private fun characteristic(uuid: String) =
        CBMutableCharacteristic(
            type = CBUUID.UUIDWithString(uuid),
            properties = CBCharacteristicPropertyRead,
            value = null,
            permissions = CBAttributePermissionsReadable,
        )

    @Test
    fun soundTableIsLeftAlone() {
        val battery = service("180F")
        val level = characteristic("2A19")
        battery.setCharacteristics(listOf(level))

        val corrupted = dropForeignCharacteristics(listOf(battery))

        assertEquals(emptyList(), corrupted)
        assertEquals(1, battery.characteristics?.size)
        assertSame(level, battery.characteristics?.single())
    }

    @Test
    fun servicesListedAsCharacteristicsAreDroppedAcrossTheCycle() {
        val battery = service("180F")
        val stray = service("181E")
        val level = characteristic("2A19")
        battery.cacheCharacteristics(listOf(level, stray))
        stray.cacheCharacteristics(listOf(battery))

        val corrupted = dropForeignCharacteristics(listOf(battery))

        assertEquals(listOf("180F", "181E"), corrupted)
        assertSame(level, battery.characteristics?.single())
        assertEquals(0, stray.characteristics?.size)
    }

    @Test
    fun serviceListingItselfIsRepairedOnce() {
        val battery = service("180F")
        battery.cacheCharacteristics(listOf(battery))

        val corrupted = dropForeignCharacteristics(listOf(battery, battery))

        assertEquals(listOf("180F"), corrupted)
        assertEquals(0, battery.characteristics?.size)
    }
}
