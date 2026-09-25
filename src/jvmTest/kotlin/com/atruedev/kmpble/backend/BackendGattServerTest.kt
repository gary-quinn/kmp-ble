package com.atruedev.kmpble.backend

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.server.ServerConnectionEvent
import com.atruedev.kmpble.server.ServerException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(KmpBleBackendApi::class, ExperimentalUuidApi::class)
class BackendGattServerTest {
    private val serviceUuid = Uuid.parse("0000feed-0000-1000-8000-00805f9b34fb")
    private val statusUuid = Uuid.parse("0000beef-0000-1000-8000-00805f9b34fb")
    private val commandUuid = Uuid.parse("0000cafe-0000-1000-8000-00805f9b34fb")
    private val central = Identifier("11:22:33:44:55:66")

    private fun server(
        transport: FakeGattServerTransport,
    ): Pair<com.atruedev.kmpble.server.GattServer, MutableList<ByteArray>> {
        val backend = FakeBackend().apply { gattServer = transport }
        val received = CopyOnWriteArrayList<ByteArray>()
        val server =
            backend.newGattServer {
                service(serviceUuid) {
                    characteristic(statusUuid) {
                        properties {
                            read = true
                            notify = true
                        }
                        permissions { read = true }
                        onRead { BleData(byteArrayOf(1, 2, 3, 4)) }
                    }
                    characteristic(commandUuid) {
                        properties {
                            write = true
                            writeWithoutResponse = true
                        }
                        permissions { write = true }
                        onWrite { _, data, _ ->
                            received += data.toByteArray()
                            if (data.size > 4) GattStatus.InvalidAttributeLength else GattStatus.Success
                        }
                    }
                }
            }
        return server to received
    }

    @Test
    fun openPublishesSpecsAndConnectionsAreTracked() =
        runBlocking<Unit> {
            val transport = FakeGattServerTransport()
            val (server, _) = server(transport)
            val connected =
                async(
                    start = CoroutineStart.UNDISPATCHED,
                ) { withTimeout(2.seconds) { server.connectionEvents.first() } }
            server.open()

            val spec = transport.openedServices!!.single()
            assertEquals(serviceUuid, spec.uuid)
            assertEquals(listOf(statusUuid, commandUuid), spec.characteristics.map { it.uuid })

            transport.emit(GattServerEvent.Connected(central, "phone"))
            assertEquals(ServerConnectionEvent.Connected(central), connected.await())
            withTimeout(2.seconds) { server.connections.first { it.isNotEmpty() } }

            transport.emit(GattServerEvent.Disconnected(central))
            withTimeout(2.seconds) { server.connections.first { it.isEmpty() } }
            server.close()
            assertTrue(transport.closed)
        }

    @Test
    fun readHonoursOffsetAndRejectsUnknownCharacteristic() =
        runBlocking<Unit> {
            val transport = FakeGattServerTransport()
            val (server, _) = server(transport)
            server.open()

            transport.emit(GattServerEvent.ReadRequest(1, central, statusUuid, offset = 2))
            val (id, status, value) = withTimeout(2.seconds) { transport.responses.receive() }
            assertEquals(1L, id)
            assertEquals(GattStatus.Success, status)
            assertContentEquals(byteArrayOf(3, 4), value)

            transport.emit(GattServerEvent.ReadRequest(2, central, commandUuid, offset = 0))
            assertEquals(
                GattStatus.RequestNotSupported,
                withTimeout(2.seconds) { transport.responses.receive() }.second,
            )

            transport.emit(GattServerEvent.ReadRequest(3, central, statusUuid, offset = 9))
            assertEquals(GattStatus.InvalidOffset, withTimeout(2.seconds) { transport.responses.receive() }.second)
            server.close()
        }

    @Test
    fun writesDispatchAssembleFragmentsAndPropagateHandlerStatus() =
        runBlocking<Unit> {
            val transport = FakeGattServerTransport()
            val (server, received) = server(transport)
            server.open()

            transport.emit(
                GattServerEvent.WriteRequest(
                    requestId = 1,
                    device = central,
                    writes =
                        listOf(
                            AttributeWrite(commandUuid, 0, byteArrayOf(1, 2)),
                            AttributeWrite(commandUuid, 2, byteArrayOf(3)),
                        ),
                    responseNeeded = true,
                ),
            )
            assertEquals(GattStatus.Success, withTimeout(2.seconds) { transport.responses.receive() }.second)
            assertContentEquals(byteArrayOf(1, 2, 3), received.single())

            transport.emit(
                GattServerEvent.WriteRequest(
                    2,
                    central,
                    listOf(AttributeWrite(commandUuid, 0, ByteArray(6))),
                    responseNeeded = true,
                ),
            )
            assertEquals(
                GattStatus.InvalidAttributeLength,
                withTimeout(2.seconds) { transport.responses.receive() }.second,
            )

            transport.emit(
                GattServerEvent.WriteRequest(
                    3,
                    central,
                    listOf(AttributeWrite(statusUuid, 0, byteArrayOf(1))),
                    responseNeeded = true,
                ),
            )
            assertEquals(GattStatus.WriteNotPermitted, withTimeout(2.seconds) { transport.responses.receive() }.second)
            server.close()
        }

    @Test
    fun notifyRequiresOpenServerKnownCharacteristicAndConnectedDevice() =
        runBlocking<Unit> {
            val transport = FakeGattServerTransport()
            val (server, _) = server(transport)
            assertFailsWith<ServerException.NotOpen> { server.notify(statusUuid, null, BleData(byteArrayOf(1))) }
            server.open()

            assertFailsWith<ServerException.NotifyFailed> { server.notify(serviceUuid, null, BleData(byteArrayOf(1))) }
            assertFailsWith<ServerException.DeviceNotConnected> {
                server.notify(
                    statusUuid,
                    central,
                    BleData(byteArrayOf(1)),
                )
            }

            server.notify(statusUuid, null, BleData(byteArrayOf(1)))
            transport.emit(GattServerEvent.Subscribed(central, statusUuid))
            withTimeout(2.seconds) { server.connections.first { it.isNotEmpty() } }
            server.indicate(statusUuid, central, BleData(byteArrayOf(2)))
            assertEquals(
                listOf(Triple(statusUuid, null, false), Triple(statusUuid, central, true)),
                transport.notifications.toList(),
            )
            server.close()
        }

    @Test
    fun openFailureIsReportedAsOpenFailed() =
        runBlocking<Unit> {
            val transport = FakeGattServerTransport().apply { openFailure = IllegalStateException("no adapter") }
            val (server, _) = server(transport)
            assertFailsWith<ServerException.OpenFailed> { server.open() }
            assertTrue(transport.closed)
        }
}
