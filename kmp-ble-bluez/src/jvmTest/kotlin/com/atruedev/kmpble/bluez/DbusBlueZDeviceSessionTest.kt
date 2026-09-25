package com.atruedev.kmpble.bluez

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.bluez.Device1
import org.bluez.exceptions.BluezFailedException
import org.freedesktop.dbus.bin.EmbeddedDBusDaemon
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Runs [DbusBlueZDeviceSession] against a real D-Bus daemon: `Device1.Connect` and `Device1.Pair`
 * return no value, which the fake sessions used by the transport tests cannot model.
 */
class DbusBlueZDeviceSessionTest {
    private lateinit var socketDir: Path
    private lateinit var daemon: EmbeddedDBusDaemon
    private lateinit var bluez: DBusConnection
    private lateinit var client: DBusConnection
    private val device = FakeDevice1()

    @BeforeTest
    fun setUp() {
        socketDir = Files.createTempDirectory("kmpble-dbus")
        val address = "unix:path=${socketDir.resolve("bus")}"
        daemon = EmbeddedDBusDaemon("$address,listen=true").apply { startInBackgroundAndWait(DAEMON_START_MILLIS) }
        bluez = DBusConnectionBuilder.forAddress(address).withShared(false).build()
        bluez.requestBusName(BlueZ.SERVICE)
        bluez.exportObject(DEVICE_PATH, device)
        client = DBusConnectionBuilder.forAddress(address).withShared(false).build()
    }

    @AfterTest
    fun tearDown() {
        runCatching { client.close() }
        runCatching { bluez.close() }
        runCatching { daemon.close() }
        socketDir.toFile().deleteRecursively()
    }

    @Test
    fun connectCompletesWhenBlueZRepliesWithoutAValue() =
        runBlocking {
            val call = session().connect()

            awaitDone(call)
            call.result()

            assertEquals(1, device.connects.get())
        }

    @Test
    fun pairCompletesWhenBlueZRepliesWithoutAValue() =
        runBlocking {
            val call = session().pair()

            awaitDone(call)
            call.result()

            assertEquals(1, device.pairs.get())
        }

    @Test
    fun connectErrorIsRethrownByResult() =
        runBlocking<Unit> {
            device.failConnect = true
            val call = session().connect()

            awaitDone(call)

            assertFailsWith<Exception> { call.result() }
        }

    private fun session() = DbusBlueZDeviceSession(client, ADAPTER_PATH, DEVICE_PATH, ADDRESS)

    private suspend fun awaitDone(call: BlueZPendingCall) {
        withTimeout(5.seconds) { while (!call.isDone()) delay(10.milliseconds) }
    }

    private class FakeDevice1 : Device1 {
        val connects = AtomicInteger()
        val pairs = AtomicInteger()

        @Volatile var failConnect = false

        override fun getObjectPath(): String = DEVICE_PATH

        override fun Connect() {
            Thread.sleep(REPLY_DELAY_MILLIS)
            connects.incrementAndGet()
            if (failConnect) throw BluezFailedException("le-connection-abort-by-local")
        }

        override fun Pair() {
            Thread.sleep(REPLY_DELAY_MILLIS)
            pairs.incrementAndGet()
        }

        override fun Disconnect() {}

        override fun ConnectProfile(uuid: String) {}

        override fun DisconnectProfile(uuid: String) {}

        override fun CancelPairing() {}
    }

    private companion object {
        const val ADAPTER_PATH = "/org/bluez/hci0"
        const val DEVICE_PATH = "/org/bluez/hci0/dev_C0_98_E5_49_00_01"
        const val ADDRESS = "C0:98:E5:49:00:01"
        const val DAEMON_START_MILLIS = 5_000L
        const val REPLY_DELAY_MILLIS = 100L
    }
}
