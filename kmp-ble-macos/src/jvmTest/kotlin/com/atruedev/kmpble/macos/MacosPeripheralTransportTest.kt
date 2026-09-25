package com.atruedev.kmpble.macos

import app.cash.turbine.test
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.backend.PeripheralEvent
import com.atruedev.kmpble.backend.peripheral
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.OperationTimeouts
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.ConnectionFailed
import com.atruedev.kmpble.error.ConnectionFailureReason
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.l2cap.L2capException
import com.atruedev.kmpble.macos.FakeMacosNativeApi.Companion.CCCD
import com.atruedev.kmpble.macos.FakeMacosNativeApi.Companion.CONTROL
import com.atruedev.kmpble.macos.FakeMacosNativeApi.Companion.HEART_RATE
import com.atruedev.kmpble.macos.FakeMacosNativeApi.Companion.IDENTIFIER
import com.atruedev.kmpble.macos.FakeMacosNativeApi.Companion.PERIPHERAL
import com.atruedev.kmpble.macos.internal.CbManagerState
import com.atruedev.kmpble.macos.internal.MacosEvent
import com.atruedev.kmpble.macos.internal.MacosPeripheralTransport
import com.atruedev.kmpble.peripheral.state.State
import com.atruedev.kmpble.scanner.uuidFrom
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class MacosPeripheralTransportTest {
    private val options =
        ConnectionOptions(timeouts = OperationTimeouts(connect = 2.seconds, serviceDiscovery = 2.seconds))

    private fun transport(
        configure: FakeMacosNativeApi.() -> Unit = {
        },
    ): Pair<MacosPeripheralTransport, FakeMacosNativeApi> {
        val (stack, api) = FakeMacosNativeApi.stack(configure)
        return MacosPeripheralTransport(
            stack,
            IDENTIFIER,
            settleTimeout = 1.seconds,
            writeReadyTimeout = 300.milliseconds,
        ) to
            api
    }

    @Test
    fun discoveryWalksServicesCharacteristicsAndDescriptors() =
        runBlocking<Unit> {
            val (transport, _) = transport()
            transport.connect(options)
            val services = transport.discoverServices()

            val service = services.single()
            assertEquals(uuidFrom("180D"), service.uuid)
            val heartRate = service.characteristics.single { it.handle == HEART_RATE }
            assertTrue(heartRate.properties.read)
            assertTrue(heartRate.properties.notify)
            assertEquals(uuidFrom("2902"), heartRate.descriptors.single().uuid)
            val control = service.characteristics.single { it.handle == CONTROL }
            assertTrue(control.properties.write && control.properties.writeWithoutResponse)
            assertEquals(185, transport.mtu())
            transport.close()
        }

    @Test
    fun readResponsesAndNotificationsAreDisambiguated() =
        runBlocking<Unit> {
            val (transport, api) = transport()
            val events = CopyOnWriteArrayList<PeripheralEvent>()
            transport.setEventListener { events += it }
            transport.connect(options)
            transport.discoverServices()

            assertContentEquals(byteArrayOf(0x4B), transport.read(HEART_RATE))
            api.emitNow(MacosEvent.ValueUpdated(PERIPHERAL, HEART_RATE, 0, null, byteArrayOf(0x22)))
            val notification = assertIs<PeripheralEvent.ValueChanged>(events.single())
            assertEquals(HEART_RATE, notification.characteristic)
            assertContentEquals(byteArrayOf(0x22), notification.value)
            transport.close()
        }

    @Test
    fun writesHonourResponseTypeAndFlowControl() =
        runBlocking<Unit> {
            val (transport, api) = transport()
            transport.connect(options)
            transport.discoverServices()

            transport.write(CONTROL, byteArrayOf(1), WriteType.WithResponse)
            transport.write(CONTROL, byteArrayOf(2), WriteType.WithoutResponse)
            assertEquals(listOf(true, false), api.writes.map { it.third })

            api.canSendWithoutResponse = false
            val pending = launch { transport.write(CONTROL, byteArrayOf(3), WriteType.WithoutResponse) }
            delay(50)
            assertEquals(2, api.writes.size)
            api.canSendWithoutResponse = true
            api.emitNow(MacosEvent.ReadyToWrite(PERIPHERAL))
            withTimeout(2.seconds) { pending.join() }
            assertEquals(3, api.writes.size)

            api.canSendWithoutResponse = false
            assertFailsWith<BleException> { transport.write(CONTROL, byteArrayOf(4), WriteType.WithoutResponse) }
            transport.close()
        }

    @Test
    fun cccdWritesBecomeSetNotify() =
        runBlocking<Unit> {
            val (transport, api) = transport()
            transport.connect(options)
            transport.discoverServices()

            transport.writeDescriptor(CCCD, byteArrayOf(0x01, 0x00))
            transport.writeDescriptor(CCCD, byteArrayOf(0x00, 0x00))
            assertEquals(listOf(HEART_RATE to true, HEART_RATE to false), api.notifyCalls.toList())
            assertContentEquals(byteArrayOf(0x01, 0x00), transport.readDescriptor(CCCD))
            assertEquals(-61, transport.readRssi())
            transport.close()
        }

    @Test
    fun disconnectAndAdapterOffBecomeEvents() =
        runBlocking<Unit> {
            val (transport, api) = transport()
            val events = CopyOnWriteArrayList<PeripheralEvent>()
            transport.setEventListener { events += it }
            transport.connect(options)
            api.emitNow(MacosEvent.Disconnected(PERIPHERAL, 1007, "The specified device has disconnected from us."))
            val disconnected = assertIs<PeripheralEvent.Disconnected>(events.single())
            assertEquals(ConnectionFailureReason.LINK_LOSS, assertIs<ConnectionLost>(disconnected.error).failureReason)

            transport.connect(options)
            api.emitNow(MacosEvent.CentralState(CbManagerState.POWERED_OFF))
            assertEquals(PeripheralEvent.AdapterOff, events.last())
            transport.close()
        }

    @Test
    fun errorFreeDisconnectFollowedByPowerOffIsAdapterOff() =
        runBlocking<Unit> {
            val (transport, api) = transport()
            val events = CopyOnWriteArrayList<PeripheralEvent>()
            transport.setEventListener { events += it }
            transport.connect(options)
            api.answerReads = false
            val pendingRead = async { runCatching { transport.read(HEART_RATE) } }
            withTimeout(2.seconds) { while ("read" !in api.calls) delay(5) }

            api.emitNow(MacosEvent.Disconnected(PERIPHERAL, 0, null))
            assertIs<BleException>(withTimeout(1.seconds) { pendingRead.await() }.exceptionOrNull())
            assertTrue(events.isEmpty())
            api.emitNow(MacosEvent.CentralState(CbManagerState.POWERED_OFF))
            delay(700)

            assertEquals(listOf<PeripheralEvent>(PeripheralEvent.AdapterOff), events.toList())
            transport.close()
        }

    @Test
    fun errorFreeDisconnectWithAdapterAlreadyOffIsAdapterOff() =
        runBlocking<Unit> {
            val (transport, api) = transport()
            val events = CopyOnWriteArrayList<PeripheralEvent>()
            transport.setEventListener { events += it }
            transport.connect(options)

            api.liveCentralState = CbManagerState.POWERED_OFF
            api.emitNow(MacosEvent.Disconnected(PERIPHERAL, 0, null))

            assertEquals(listOf<PeripheralEvent>(PeripheralEvent.AdapterOff), events.toList())
            transport.close()
        }

    @Test
    fun errorFreeRemoteDisconnectIsReportedAfterTheGracePeriod() =
        runBlocking<Unit> {
            val (transport, api) = transport()
            val events = CopyOnWriteArrayList<PeripheralEvent>()
            transport.setEventListener { events += it }
            transport.connect(options)

            api.emitNow(MacosEvent.Disconnected(PERIPHERAL, 0, null))
            withTimeout(2.seconds) { while (events.isEmpty()) delay(10) }

            assertIs<PeripheralEvent.Disconnected>(events.single())
            transport.close()
        }

    @Test
    fun connectFailuresAreTyped() =
        runBlocking<Unit> {
            val (unknown, _) = transport { knownPeripherals = mutableMapOf() }
            assertEquals(
                ConnectionFailureReason.UNKNOWN_DEVICE,
                assertIs<ConnectionFailed>(
                    assertFailsWith<BleException> { unknown.connect(options) }.error,
                ).failureReason,
            )

            val (rejected, _) =
                transport {
                    connectAnswer = {
                        MacosEvent.ConnectFailed(
                            it,
                            1010,
                            "Connection failed",
                        )
                    }
                }
            assertEquals(
                ConnectionFailureReason.GATT_ERROR,
                assertIs<ConnectionFailed>(
                    assertFailsWith<BleException> { rejected.connect(options) }.error,
                ).failureReason,
            )

            val (off, _) = transport { initialCentralState = CbManagerState.POWERED_OFF }
            assertEquals(
                Macos.ERROR_ADAPTER_OFF,
                assertIs<ConnectionFailed>(
                    assertFailsWith<BleException> {
                        off.connect(options)
                    }.error,
                ).platformCode,
            )

            val (missing, _) = transport { missingUsageHost = "/Applications/Host.app" }
            assertEquals(
                Macos.ERROR_USAGE_DESCRIPTION_MISSING,
                assertIs<ConnectionFailed>(
                    assertFailsWith<BleException> { missing.connect(options) }.error,
                ).platformCode,
            )
        }

    @Test
    fun cancelledConnectCancelsThePendingConnection() =
        runBlocking<Unit> {
            val (transport, api) = transport { connectAnswer = { null } }
            val job = launch { runCatching { transport.connect(options) } }
            withTimeout(2.seconds) { while ("connect" !in api.calls) delay(5) }
            job.cancel()
            job.join()
            assertTrue("cancelConnect" in api.calls)
        }

    @Test
    fun closeWhileConnectingFailsTheConnectAndCancelsIt() =
        runBlocking<Unit> {
            val (transport, api) = transport { connectAnswer = { null } }
            val connect = async { runCatching { transport.connect(options) } }
            withTimeout(2.seconds) { while ("connect" !in api.calls) delay(5) }
            transport.close()
            val failure = withTimeout(2.seconds) { connect.await() }.exceptionOrNull()
            assertIs<ConnectionLost>(assertIs<BleException>(failure).error)
            assertTrue("cancelConnect" in api.calls)
            assertIs<ConnectionLost>(assertFailsWith<BleException> { transport.connect(options) }.error)
        }

    @Test
    fun overlappingL2capOpenIsATypedFailure() =
        runBlocking<Unit> {
            val (transport, api) = transport()
            transport.connect(options)
            api.answerL2capOpen = false
            val first = async { runCatching { transport.openL2capChannel(0x81, secure = true) } }
            withTimeout(2.seconds) { while (api.calls.none { it == "openL2cap" }) delay(5) }
            assertFailsWith<L2capException.OpenFailed> { transport.openL2capChannel(0x82, secure = true) }
            transport.close()
            assertIs<BleException>(withTimeout(2.seconds) { first.await() }.exceptionOrNull())
        }

    @Test
    fun l2capChannelsOpenAndBufferEarlyData() =
        runBlocking<Unit> {
            val (transport, api) = transport()
            transport.connect(options)
            val stream = transport.openL2capChannel(0x81, secure = true)
            api.emitNow(MacosEvent.L2capData(FakeMacosNativeApi.CHANNEL, byteArrayOf(9)))

            val received = CopyOnWriteArrayList<ByteArray>()
            stream.setListener(
                object : com.atruedev.kmpble.backend.L2capStreamListener {
                    override fun onData(data: ByteArray) {
                        received += data
                    }

                    override fun onClosed(reason: String?) {}
                },
            )
            assertContentEquals(byteArrayOf(9), received.single())
            stream.write(byteArrayOf(1, 2))
            assertContentEquals(byteArrayOf(1, 2), api.l2capWrites.single().second)
            api.l2capWriteResult = -1
            assertFailsWith<L2capException.WriteFailed> { stream.write(byteArrayOf(3)) }
            assertFailsWith<L2capException.OpenFailed> {
                transport.openL2capChannel(FakeMacosNativeApi.REJECTED_PSM, secure = true)
            }
            transport.close()
        }

    @Test
    fun genericPeripheralRunsOverCoreBluetoothTransport() =
        runBlocking<Unit> {
            val (stack, api) = FakeMacosNativeApi.stack()
            val backend = MacosBackend { stack }
            val peripheral = backend.peripheral(Identifier(IDENTIFIER), IDENTIFIER)

            peripheral.connect(options)
            assertIs<State.Connected.Ready>(peripheral.state.value)
            assertEquals(182, peripheral.maximumWriteValueLength.value)
            assertFalse(peripheral.supportsReliableWrite)
            val heartRate = assertNotNull(peripheral.findCharacteristic(uuidFrom("180D"), uuidFrom("2A37")))
            assertContentEquals(byteArrayOf(0x4B), peripheral.read(heartRate))

            peripheral.observeValues(heartRate).test {
                withTimeout(2.seconds) { while (api.notifyCalls.isEmpty()) delay(5) }
                delay(20)
                api.emitNow(MacosEvent.ValueUpdated(PERIPHERAL, HEART_RATE, 0, null, byteArrayOf(0x31)))
                assertContentEquals(byteArrayOf(0x31), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            withTimeout(2.seconds) { while (api.notifyCalls.size < 2) delay(5) }

            peripheral.disconnect()
            assertIs<State.Disconnected.ByRequest>(peripheral.state.value)
            peripheral.close()
            withTimeout(2.seconds) { peripheral.state.first { it is State.Disconnected } }
            assertTrue("release" in api.calls)
        }
}
