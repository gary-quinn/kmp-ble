package com.atruedev.kmpble.server

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.emptyBleData
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.scanner.uuidFrom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class GattServerBuilderTest {

    private val serviceUuid = Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb")
    private val charUuid1 = Uuid.parse("00002a37-0000-1000-8000-00805f9b34fb")
    private val charUuid2 = Uuid.parse("00002a38-0000-1000-8000-00805f9b34fb")

    @Test
    fun builder_creates_service_with_characteristics() {
        val builder = GattServerBuilder()
        builder.service(serviceUuid) {
            characteristic(charUuid1) {
                properties { read = true }
                permissions { read = true }
                onRead { emptyBleData() }
            }
        }

        assertEquals(1, builder.services.size)
        assertEquals(serviceUuid, builder.services[0].uuid)
        assertEquals(1, builder.services[0].characteristics.size)
        assertEquals(charUuid1, builder.services[0].characteristics[0].uuid)
    }

    @Test
    fun characteristic_with_read_and_write_handlers() {
        val builder = GattServerBuilder()
        builder.service(serviceUuid) {
            characteristic(charUuid1) {
                properties { read = true; write = true }
                permissions { read = true; write = true }
                onRead { _ -> BleData(byteArrayOf(0x01)) }
                onWrite { _, _, _ -> GattStatus.Success }
            }
        }

        val charDef = builder.services[0].characteristics[0]
        assertNotNull(charDef.readHandler)
        assertNotNull(charDef.writeHandler)
    }

    @Test
    fun characteristic_properties_map_correctly() {
        val builder = GattServerBuilder()
        builder.service(serviceUuid) {
            characteristic(charUuid1) {
                properties {
                    read = true
                    write = true
                    writeWithoutResponse = true
                    notify = true
                    indicate = true
                }
                permissions {
                    read = true
                    readEncrypted = true
                    write = true
                    writeEncrypted = true
                }
                onRead { emptyBleData() }
                onWrite { _, _, _ -> GattStatus.Success }
            }
        }

        val charDef = builder.services[0].characteristics[0]
        assertTrue(charDef.properties.read)
        assertTrue(charDef.properties.write)
        assertTrue(charDef.properties.writeWithoutResponse)
        assertTrue(charDef.properties.notify)
        assertTrue(charDef.properties.indicate)
        assertTrue(charDef.permissions.read)
        assertTrue(charDef.permissions.readEncrypted)
        assertTrue(charDef.permissions.write)
        assertTrue(charDef.permissions.writeEncrypted)
    }

    @Test
    fun string_uuid_shorthand_parses_correctly() {
        val builder = GattServerBuilder()
        builder.service("180d") {
            characteristic("2a37") {
                properties { notify = true }
                permissions { read = true }
            }
        }

        assertEquals(uuidFrom("180d"), builder.services[0].uuid)
        assertEquals(uuidFrom("2a37"), builder.services[0].characteristics[0].uuid)
    }

    @Test
    fun multiple_services_supported() {
        val serviceUuid2 = Uuid.parse("0000180f-0000-1000-8000-00805f9b34fb")

        val builder = GattServerBuilder()
        builder.service(serviceUuid) {
            characteristic(charUuid1) {
                properties { read = true }
                permissions { read = true }
                onRead { emptyBleData() }
            }
        }
        builder.service(serviceUuid2) {
            characteristic(charUuid2) {
                properties { notify = true }
                permissions { read = true }
            }
        }

        assertEquals(2, builder.services.size)
        assertEquals(serviceUuid, builder.services[0].uuid)
        assertEquals(serviceUuid2, builder.services[1].uuid)
    }

    @Test
    fun empty_service_is_valid() {
        val builder = GattServerBuilder()
        builder.service(serviceUuid) {}

        assertEquals(1, builder.services.size)
        assertTrue(builder.services[0].characteristics.isEmpty())
    }

    @Test
    fun notify_only_characteristic_has_null_handlers() {
        val builder = GattServerBuilder()
        builder.service(serviceUuid) {
            characteristic(charUuid1) {
                properties { notify = true }
                permissions { read = true }
            }
        }

        val charDef = builder.services[0].characteristics[0]
        assertNull(charDef.readHandler)
        assertNull(charDef.writeHandler)
    }

    @Test
    fun default_properties_are_all_false() {
        val builder = GattServerBuilder()
        builder.service(serviceUuid) {
            characteristic(charUuid1) {
                properties { }
                permissions { }
            }
        }

        val charDef = builder.services[0].characteristics[0]
        assertFalse(charDef.properties.read)
        assertFalse(charDef.properties.write)
        assertFalse(charDef.properties.writeWithoutResponse)
        assertFalse(charDef.properties.notify)
        assertFalse(charDef.properties.indicate)
        assertFalse(charDef.permissions.read)
        assertFalse(charDef.permissions.readEncrypted)
        assertFalse(charDef.permissions.write)
        assertFalse(charDef.permissions.writeEncrypted)
    }

    @Test
    fun characteristic_with_descriptors() {
        val descUuid = Uuid.parse("00002901-0000-1000-8000-00805f9b34fb")

        val builder = GattServerBuilder()
        builder.service(serviceUuid) {
            characteristic(charUuid1) {
                properties { notify = true }
                permissions { read = true }
                descriptor(descUuid)
            }
        }

        val charDef = builder.services[0].characteristics[0]
        assertEquals(1, charDef.descriptors.size)
        assertEquals(descUuid, charDef.descriptors[0].uuid)
    }

    // --- Validation tests ---

    @Test
    fun read_property_without_handler_throws() {
        val builder = GattServerBuilder()
        assertFailsWith<IllegalArgumentException> {
            builder.service(serviceUuid) {
                characteristic(charUuid1) {
                    properties { read = true }
                    permissions { read = true }
                }
            }
        }
    }

    @Test
    fun write_property_without_handler_throws() {
        val builder = GattServerBuilder()
        assertFailsWith<IllegalArgumentException> {
            builder.service(serviceUuid) {
                characteristic(charUuid1) {
                    properties { write = true }
                    permissions { write = true }
                }
            }
        }
    }

    @Test
    fun duplicate_characteristic_uuids_throws() {
        val builder = GattServerBuilder()
        assertFailsWith<IllegalArgumentException> {
            builder.service(serviceUuid) {
                characteristic(charUuid1) {
                    properties { notify = true }
                }
                characteristic(charUuid1) {
                    properties { notify = true }
                }
            }
        }
    }

    @Test
    fun duplicate_service_uuids_throws() {
        val builder = GattServerBuilder()
        builder.service(serviceUuid) {}
        assertFailsWith<IllegalArgumentException> {
            builder.service(serviceUuid) {}
        }
    }
}
