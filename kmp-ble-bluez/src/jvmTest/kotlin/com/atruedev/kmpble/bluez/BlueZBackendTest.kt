package com.atruedev.kmpble.bluez

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.backend.AdvertisingSetSpec
import com.atruedev.kmpble.backend.BleBackends
import com.atruedev.kmpble.backend.GattServerEvent
import com.atruedev.kmpble.backend.ServerCharacteristicSpec
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.server.AdvertiseMode
import com.atruedev.kmpble.server.AdvertiseTxPower
import com.atruedev.kmpble.server.ServerCharacteristic
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.exceptions.DBusExecutionException
import org.freedesktop.dbus.types.UInt16
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class BlueZBackendTest {
    private val characteristic = Uuid.parse("0000beef-0000-1000-8000-00805f9b34fb")
    private val devicePath = "/org/bluez/hci0/dev_11_22_33_44_55_66"

    @Test
    fun serviceLoaderFindsBlueZBackend() {
        val backend = BleBackends.available().single { it.id == BlueZ.BACKEND_ID }
        assertEquals(BlueZ.isLinux(), backend.isSupported())
        assertTrue(backend is BlueZBackend)
    }

    @Test
    fun toBlueZPeripheralIsSharedPerIdentifier() {
        val advertisement =
            Advertisement(
                identifier = Identifier("AA:BB:CC:DD:EE:FF"),
                name = null,
                rssi = -50,
                txPower = null,
                isConnectable = true,
                serviceUuids = emptyList(),
                manufacturerData = emptyMap(),
                serviceData = emptyMap(),
                timestampNanos = 0,
            )
        val first = advertisement.toBlueZPeripheral()
        assertSame(first, advertisement.toBlueZPeripheral())
        assertEquals(Identifier("AA:BB:CC:DD:EE:FF"), first.identifier)
        first.close()
    }

    @Test
    fun uuidAndFlagMappingFollowsBlueZNames() {
        assertEquals(Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb"), normalizeUuid("180d"))
        assertEquals(
            Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb"),
            normalizeUuid("0000180D-0000-1000-8000-00805F9B34FB"),
        )
        val properties = flagsToProperties(listOf("encrypt-read", "reliable-write", "indicate"))
        assertTrue(properties.read)
        assertTrue(properties.indicate)
        assertEquals(false, properties.signedWrite)
        assertTrue(flagsToProperties(listOf("authenticated-signed-writes")).signedWrite)
    }

    @Test
    fun handleTableIsStablePerPath() {
        val handles = BlueZHandleTable()
        val first = handles.handleFor("/a")
        assertEquals(first, handles.handleFor("/a"))
        assertEquals("/a", handles.pathFor(first))
        assertNull(handles.handleOrNull("/b"))
        assertTrue(handles.handleFor("/b") != first)
    }

    @Test
    fun agentParsesDevicePaths() {
        assertEquals("11:22:33:44:55:66", BlueZPairingAgent.addressFromPath(devicePath))
        assertNull(BlueZPairingAgent.addressFromPath("/org/bluez/hci0"))
    }

    @Test
    fun serverFlagsReflectPropertiesAndEncryption() {
        val spec =
            ServerCharacteristicSpec(
                uuid = characteristic,
                properties = ServerCharacteristic.Properties(read = true, write = true, notify = true),
                permissions = ServerCharacteristic.Permissions(readEncrypted = true, write = true),
                descriptors = emptyList(),
            )
        assertEquals(listOf("encrypt-read", "write", "notify"), characteristicFlags(spec))
    }

    @Test
    fun serverReadRequestsWaitForTheHandlerResponse() {
        val server = BlueZGattServerTransport(requestTimeoutMs = 2_000)
        val events = CopyOnWriteArrayList<GattServerEvent>()
        server.setEventListener { event ->
            events += event
            if (event is GattServerEvent.ReadRequest) {
                thread { server.respond(event.requestId, GattStatus.Success, byteArrayOf(9, 8)) }
            }
        }

        val value =
            server.onRead(
                characteristic,
                mapOf(
                    "device" to Variant(DBusPath(devicePath)),
                    "offset" to Variant(UInt16(1)),
                ),
            )
        assertContentEquals(byteArrayOf(9, 8), value)
        val request = events.single() as GattServerEvent.ReadRequest
        assertEquals(Identifier("11:22:33:44:55:66"), request.device)
        assertEquals(1, request.offset)
        server.close()
    }

    @Test
    fun serverWritesMapStatusToBlueZErrors() {
        val server = BlueZGattServerTransport(requestTimeoutMs = 2_000)
        server.setEventListener { event ->
            if (event is GattServerEvent.WriteRequest) {
                thread { server.respond(event.requestId, GattStatus.InvalidAttributeLength, null) }
            }
        }
        val rejected =
            assertFailsWith<DBusExecutionException> {
                server.onWrite(characteristic, byteArrayOf(1), mapOf("type" to Variant("request")))
            }
        assertEquals("org.bluez.Error\$InvalidValueLength", rejected.javaClass.name)

        val offset =
            assertFailsWith<DBusExecutionException> {
                server.onWrite(characteristic, byteArrayOf(1), mapOf("offset" to Variant(UInt16(4))))
            }
        assertEquals("org.bluez.Error\$InvalidOffset", offset.javaClass.name)

        server.onWrite(characteristic, byteArrayOf(1), mapOf("type" to Variant("command")))
        server.close()
    }

    @Test
    fun advertisementPropertiesFollowLEAdvertisement1() {
        val spec =
            AdvertisingSetSpec(
                name = "kmp",
                serviceUuids = listOf(characteristic),
                manufacturerData = mapOf(0x004C to byteArrayOf(1)),
                serviceData = mapOf(characteristic to byteArrayOf(2)),
                connectable = true,
                scannable = true,
                includeTxPower = true,
                mode = AdvertiseMode.LowLatency,
                txPower = AdvertiseTxPower.High,
                legacy = false,
                primaryPhy = Phy.Le1M,
                secondaryPhy = Phy.LeCoded,
            )
        val properties = BlueZAdvertisement("/adv/0", spec).properties()
        assertEquals("peripheral", properties.getValue("Type").value)
        assertEquals("kmp", properties.getValue("LocalName").value)
        assertEquals(
            listOf(characteristic.toString()),
            (properties.getValue("ServiceUUIDs").value as Array<*>).toList(),
        )
        assertEquals(listOf("tx-power"), (properties.getValue("Includes").value as Array<*>).toList())
        assertEquals(UInt32(100), properties.getValue("MinInterval").value)
        assertEquals(1.toShort(), properties.getValue("TxPower").value)
        assertEquals("Coded", properties.getValue("SecondaryChannel").value)
        assertEquals(
            "broadcast",
            BlueZAdvertisement("/adv/1", spec.copyConnectable(false)).properties().getValue("Type").value,
        )
    }

    private fun AdvertisingSetSpec.copyConnectable(connectable: Boolean): AdvertisingSetSpec =
        AdvertisingSetSpec(
            name,
            serviceUuids,
            manufacturerData,
            serviceData,
            connectable,
            scannable,
            includeTxPower,
            mode,
            txPower,
            legacy,
            primaryPhy,
            secondaryPhy,
        )
}
