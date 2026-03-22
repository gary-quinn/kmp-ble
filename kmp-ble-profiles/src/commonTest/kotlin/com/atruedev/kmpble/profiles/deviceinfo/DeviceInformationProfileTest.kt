package com.atruedev.kmpble.profiles.deviceinfo

import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DeviceInformationProfileTest {

    @Test
    fun readsAllStringFields() = runTest {
        val peripheral = FakePeripheral {
            service("180a") {
                characteristic("2a29") {
                    properties(read = true)
                    onRead { "Acme Corp".encodeToByteArray() }
                }
                characteristic("2a24") {
                    properties(read = true)
                    onRead { "Widget-3000".encodeToByteArray() }
                }
                characteristic("2a25") {
                    properties(read = true)
                    onRead { "SN123456".encodeToByteArray() }
                }
                characteristic("2a27") {
                    properties(read = true)
                    onRead { "1.0".encodeToByteArray() }
                }
                characteristic("2a26") {
                    properties(read = true)
                    onRead { "2.1".encodeToByteArray() }
                }
                characteristic("2a28") {
                    properties(read = true)
                    onRead { "3.2".encodeToByteArray() }
                }
            }
        }
        peripheral.connect()
        val info = peripheral.readDeviceInformation()
        assertEquals("Acme Corp", info.manufacturerName)
        assertEquals("Widget-3000", info.modelNumber)
        assertEquals("SN123456", info.serialNumber)
        assertEquals("1.0", info.hardwareRevision)
        assertEquals("2.1", info.firmwareRevision)
        assertEquals("3.2", info.softwareRevision)
    }

    @Test
    fun missingCharacteristicsReturnNull() = runTest {
        val peripheral = FakePeripheral {
            service("180a") {
                characteristic("2a29") {
                    properties(read = true)
                    onRead { "Acme".encodeToByteArray() }
                }
            }
        }
        peripheral.connect()
        val info = peripheral.readDeviceInformation()
        assertEquals("Acme", info.manufacturerName)
        assertNull(info.modelNumber)
        assertNull(info.serialNumber)
    }

    @Test
    fun readsPnpId() = runTest {
        val peripheral = FakePeripheral {
            service("180a") {
                characteristic("2a50") {
                    properties(read = true)
                    onRead {
                        byteArrayOf(
                            0x01,                   // vendorIdSource: Bluetooth SIG
                            0x0D, 0x00,             // vendorId: 13
                            0x01, 0x00,             // productId: 1
                            0x10, 0x00,             // productVersion: 16
                        )
                    }
                }
            }
        }
        peripheral.connect()
        val info = peripheral.readDeviceInformation()
        val pnp = info.pnpId
        assertNotNull(pnp)
        assertEquals(1, pnp.vendorIdSource)
        assertEquals(13, pnp.vendorId)
        assertEquals(1, pnp.productId)
        assertEquals(16, pnp.productVersion)
    }

    @Test
    fun noServiceReturnsAllNulls() = runTest {
        val peripheral = FakePeripheral {}
        peripheral.connect()
        val info = peripheral.readDeviceInformation()
        assertNull(info.manufacturerName)
        assertNull(info.modelNumber)
        assertNull(info.pnpId)
    }
}
