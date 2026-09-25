package com.atruedev.kmpble.bluez

import com.atruedev.kmpble.backend.GattServerEvent
import com.atruedev.kmpble.backend.ServerCharacteristicSpec
import com.atruedev.kmpble.backend.ServerServiceSpec
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.server.ServerCharacteristic
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.bluez.GattCharacteristic1
import org.bluez.GattManager1
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.bin.EmbeddedDBusDaemon
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.ObjectManager
import org.freedesktop.dbus.types.Variant
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Runs [BlueZGattServerTransport] against a real D-Bus daemon. bluetoothd does not wait for the
 * reply to `WriteValue` on a characteristic that allows write without response, so consecutive
 * writes reach the application back to back and must be handled in the order they were sent.
 */
@OptIn(ExperimentalUuidApi::class)
class DbusBlueZGattServerTest {
    private lateinit var socketDir: Path
    private lateinit var address: String
    private lateinit var daemon: EmbeddedDBusDaemon
    private lateinit var bluez: DBusConnection
    private val manager = FakeGattManager()

    @BeforeTest
    fun setUp() {
        socketDir = Files.createTempDirectory("kmpble-dbus")
        address = "unix:path=${socketDir.resolve("bus")}"
        daemon = EmbeddedDBusDaemon("$address,listen=true").apply { startInBackgroundAndWait(DAEMON_START_MILLIS) }
        bluez = DBusConnectionBuilder.forAddress(address).withShared(false).build()
        bluez.requestBusName(BlueZ.SERVICE)
        bluez.exportObject("/", FakeObjectManager())
        bluez.exportObject(ADAPTER_PATH, manager)
    }

    @AfterTest
    fun tearDown() {
        runCatching { bluez.close() }
        runCatching { daemon.close() }
        socketDir.toFile().deleteRecursively()
    }

    @Test
    fun writesWithoutAwaitedRepliesAreHandledInOrder() =
        runBlocking {
            val serverBus = AtomicReference<DBusConnection>()
            val transport =
                BlueZGattServerTransport(
                    openBus = { BlueZ.openGattServerBus(address).also(serverBus::set) },
                    isHostSupported = { true },
                )
            val received = CopyOnWriteArrayList<Int>()
            transport.setEventListener { event ->
                if (event is GattServerEvent.WriteRequest) {
                    received += event.writes
                        .single()
                        .value[0]
                        .toInt() and 0xFF
                    transport.respond(event.requestId, GattStatus.Success, null)
                }
            }
            transport.open(listOf(service))
            val characteristic =
                bluez.getRemoteObject(
                    serverBus.get().uniqueName,
                    "${manager.application.get()}/service0/char0",
                    GattCharacteristic1::class.java,
                )

            repeat(WRITES) { bluez.callMethodAsync(characteristic, "WriteValue", byteArrayOf(it.toByte()), options) }
            withTimeout(10.seconds) { while (received.size < WRITES) delay(10.milliseconds) }

            assertEquals((0 until WRITES).toList(), received.toList())
            transport.close()
        }

    private val service =
        ServerServiceSpec(
            Uuid.parse("6b6d7062-6c65-4e00-8000-000000000001"),
            listOf(
                ServerCharacteristicSpec(
                    Uuid.parse("6b6d7062-6c65-4e00-8000-000000000002"),
                    ServerCharacteristic.Properties(write = true, writeWithoutResponse = true),
                    ServerCharacteristic.Permissions(write = true),
                    emptyList(),
                ),
            ),
        )

    private val options: Map<String, Variant<*>> =
        mapOf("device" to Variant(DBusPath("$ADAPTER_PATH/dev_C0_98_E5_49_00_01")))

    private class FakeObjectManager : ObjectManager {
        override fun getObjectPath(): String = "/"

        override fun GetManagedObjects(): Map<DBusPath, Map<String, Map<String, Variant<*>>>> =
            mapOf(
                DBusPath(ADAPTER_PATH) to
                    mapOf(
                        BlueZ.ADAPTER_INTERFACE to mapOf("Address" to Variant("00:AA:01:00:00:00")),
                        BlueZ.GATT_MANAGER_INTERFACE to emptyMap(),
                    ),
            )
    }

    private class FakeGattManager : GattManager1 {
        val application = AtomicReference<String>()

        override fun getObjectPath(): String = ADAPTER_PATH

        override fun RegisterApplication(
            application: DBusPath,
            options: Map<String, Variant<*>>,
        ) {
            this.application.set(application.path)
        }

        override fun UnregisterApplication(application: DBusPath) {}
    }

    private companion object {
        const val ADAPTER_PATH = "/org/bluez/hci0"
        const val DAEMON_START_MILLIS = 5_000L
        const val WRITES = 200
    }
}
