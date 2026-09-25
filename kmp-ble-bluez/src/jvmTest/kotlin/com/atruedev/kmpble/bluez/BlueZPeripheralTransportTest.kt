package com.atruedev.kmpble.bluez

import app.cash.turbine.test
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.backend.BleBackend
import com.atruedev.kmpble.backend.PeripheralEvent
import com.atruedev.kmpble.backend.PeripheralTransport
import com.atruedev.kmpble.backend.peripheral
import com.atruedev.kmpble.bonding.BondRemovalResult
import com.atruedev.kmpble.bonding.BondState
import com.atruedev.kmpble.connection.BondingPreference
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.OperationTimeouts
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.ConnectionFailed
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.peripheral.Peripheral
import com.atruedev.kmpble.peripheral.state.State
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.bluez.exceptions.BluezNotPermittedException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
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
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class BlueZPeripheralTransportTest {
    private val options =
        ConnectionOptions(timeouts = OperationTimeouts(connect = 2.seconds, serviceDiscovery = 2.seconds))
    private val heartRateService = Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb")
    private val heartRate = Uuid.parse("00002a37-0000-1000-8000-00805f9b34fb")
    private val control = Uuid.parse("00002a39-0000-1000-8000-00805f9b34fb")

    private fun transport(
        session: FakeBlueZDeviceSession = FakeBlueZDeviceSession(),
    ): Pair<BlueZPeripheralTransport, FakeBlueZDeviceSessionFactory> {
        val factory = FakeBlueZDeviceSessionFactory(session)
        return BlueZPeripheralTransport(
            address = session.address,
            devicePath = session.devicePath,
            sessionFactory = factory,
            pollInterval = 5.milliseconds,
            tableChangeDebounce = 20.milliseconds,
            linkLossGrace = LINK_LOSS_GRACE,
        ) to factory
    }

    private fun peripheralOver(transport: PeripheralTransport): Peripheral =
        object : BleBackend by BlueZBackend() {
            override fun createPeripheralTransport(
                identifier: Identifier,
                handle: String?,
            ): PeripheralTransport = transport
        }.peripheral(Identifier("AA:BB:CC:DD:EE:FF"))

    @Test
    fun discoveryMapsHandlesFlagsAndMtu() =
        runBlocking<Unit> {
            val session = FakeBlueZDeviceSession()
            val (transport, factory) = transport(session)
            transport.connect(options)
            val services = transport.discoverServices()

            assertEquals(1, factory.openCalls)
            assertEquals(listOf(session.devicePath), factory.requestedPaths.toList())
            val characteristics = services.single().characteristics
            val write = characteristics.single { it.uuid == control }
            assertTrue(write.properties.write)
            assertTrue(write.properties.writeWithoutResponse)
            assertTrue(write.properties.signedWrite)
            assertFalse(characteristics.single { it.uuid == heartRate }.properties.signedWrite)
            assertEquals(1, characteristics.single { it.uuid == heartRate }.descriptors.size)
            assertEquals(247, transport.mtu())
            transport.close()
        }

    @Test
    fun writeTypesMapToBlueZOptions() =
        runBlocking<Unit> {
            val session = FakeBlueZDeviceSession()
            val (transport, _) = transport(session)
            transport.connect(options)
            val handle =
                transport
                    .discoverServices()
                    .single()
                    .characteristics
                    .single { it.uuid == control }
                    .handle

            transport.write(handle, byteArrayOf(1), WriteType.WithResponse)
            transport.write(handle, byteArrayOf(2), WriteType.WithoutResponse)
            transport.writeReliable(handle, byteArrayOf(3))
            assertEquals(listOf("request", "command", "reliable"), session.writes.map { it.third })
            assertTrue(session.writes.all { it.first == FakeBlueZDeviceSession.WRITE_PATH })
            transport.close()
        }

    @Test
    fun notificationsAndLinkLossBecomeEvents() =
        runBlocking<Unit> {
            val session = FakeBlueZDeviceSession()
            val (transport, _) = transport(session)
            val events = CopyOnWriteArrayList<PeripheralEvent>()
            transport.setEventListener { events += it }
            transport.connect(options)
            val handle =
                transport
                    .discoverServices()
                    .single()
                    .characteristics
                    .single { it.uuid == heartRate }
                    .handle

            session.simulateValue(FakeBlueZDeviceSession.CHAR_PATH, byteArrayOf(0x33))
            val value = assertIs<PeripheralEvent.ValueChanged>(events.single())
            assertEquals(handle, value.characteristic)
            assertContentEquals(byteArrayOf(0x33), value.value)

            session.signals?.onAdapterPowered(false)
            assertEquals(PeripheralEvent.AdapterOff, events.last())
            session.simulateDisconnected()
            withTimeout(2.seconds) { while (events.last() !is PeripheralEvent.Disconnected) delay(5) }
            transport.close()
        }

    @Test
    fun linkLossIsReportedAfterTheGraceWindow() =
        runBlocking<Unit> {
            val session = FakeBlueZDeviceSession()
            val (transport, _) = transport(session)
            val events = CopyOnWriteArrayList<PeripheralEvent>()
            transport.setEventListener { events += it }
            transport.connect(options)

            session.simulateDisconnected()
            assertTrue(events.isEmpty())
            withTimeout(2.seconds) { while (events.isEmpty()) delay(5) }

            assertIs<PeripheralEvent.Disconnected>(events.single())
            transport.close()
        }

    @Test
    fun adapterPowerOffRightAfterLinkLossReportsAdapterOff() =
        runBlocking<Unit> {
            val session = FakeBlueZDeviceSession()
            val (transport, _) = transport(session)
            val events = CopyOnWriteArrayList<PeripheralEvent>()
            transport.setEventListener { events += it }
            transport.connect(options)

            session.simulateDisconnected()
            session.signals?.onAdapterPowered(false)
            delay(LINK_LOSS_GRACE * 3)

            assertEquals(listOf<PeripheralEvent>(PeripheralEvent.AdapterOff), events.toList())
            transport.close()
        }

    @Test
    fun reconnectWithinTheGraceWindowDropsTheEarlierLinkLoss() =
        runBlocking<Unit> {
            val session = FakeBlueZDeviceSession()
            val (transport, _) = transport(session)
            val events = CopyOnWriteArrayList<PeripheralEvent>()
            transport.setEventListener { events += it }
            transport.connect(options)

            session.simulateDisconnected()
            transport.connect(options)
            delay(LINK_LOSS_GRACE * 3)

            assertTrue(events.none { it is PeripheralEvent.Disconnected })
            transport.close()
        }

    @Test
    fun adapterPowerOffWhileConnectedEndsBySystemEvent() =
        runBlocking<Unit> {
            val session = FakeBlueZDeviceSession()
            val (transport, _) = transport(session)
            val peripheral = peripheralOver(transport)
            peripheral.connect(options)

            session.simulateDisconnected()
            session.signals?.onAdapterPowered(false)

            assertEquals(
                State.Disconnected.BySystemEvent,
                withTimeout(2.seconds) { peripheral.state.first { it is State.Disconnected } },
            )
            peripheral.close()
        }

    @Test
    fun gattTableChangeAfterDiscoveryIsDebouncedIntoServicesChanged() =
        runBlocking<Unit> {
            val session = FakeBlueZDeviceSession()
            val (transport, _) = transport(session)
            val events = CopyOnWriteArrayList<PeripheralEvent>()
            transport.setEventListener { events += it }
            transport.connect(options)
            session.signals!!.onGattTableChanged()
            delay(60)
            assertTrue(events.isEmpty())

            transport.discoverServices()
            repeat(3) { session.signals!!.onGattTableChanged() }
            withTimeout(2.seconds) { while (events.none { it == PeripheralEvent.ServicesChanged }) delay(5) }
            delay(60)
            assertEquals(1, events.count { it == PeripheralEvent.ServicesChanged })
            transport.close()
        }

    @Test
    fun blueZErrorsMapToCommonErrors() =
        runBlocking<Unit> {
            val session = FakeBlueZDeviceSession()
            val (transport, _) = transport(session)
            transport.connect(options)
            val handle =
                transport
                    .discoverServices()
                    .single()
                    .characteristics
                    .single { it.uuid == heartRate }
                    .handle

            session.readFailure = BluezNotPermittedException("denied")
            assertEquals(
                GattStatus.WriteNotPermitted,
                assertIs<GattError>(
                    assertFailsWith<BleException> {
                        transport.read(handle)
                    }.error,
                ).status,
            )

            session.readFailure = null
            session.connected = false
            assertIs<ConnectionLost>(assertFailsWith<BleException> { transport.read(handle) }.error)
            transport.close()
        }

    @Test
    fun connectFailureIsTypedAndRssiUsesLastReportedValue() =
        runBlocking<Unit> {
            val failing =
                FakeBlueZDeviceSession(connectFailure = FakeBlueZDeviceSession.failed("le-connection-abort-by-local"))
            val (transport, _) = transport(failing)
            val error = assertFailsWith<BleException> { transport.connect(options) }.error
            assertEquals(BlueZ.ERROR_CONNECT_FAILED, assertIs<ConnectionFailed>(error).platformCode)
            transport.close()

            val silent = FakeBlueZDeviceSession(rssiValue = null)
            val (quiet, _) = transport(silent)
            quiet.connect(options)
            assertEquals(
                GattStatus.RequestNotSupported,
                assertIs<GattError>(
                    assertFailsWith<BleException> {
                        quiet.readRssi()
                    }.error,
                ).status,
            )
            quiet.close()
        }

    @Test
    fun cancelledConnectAbortsWithDisconnect() =
        runBlocking<Unit> {
            val session = FakeBlueZDeviceSession().apply { connectBlock = CountDownLatch(1) }
            val (transport, _) = transport(session)
            val job = launch { runCatching { transport.connect(options) } }
            withTimeout(2.seconds) { while (session.connectCalls == 0) delay(5) }
            job.cancel()
            job.join()
            withTimeout(2.seconds) { while (session.disconnectCalls == 0) delay(5) }
            transport.close()
        }

    @Test
    fun reconnectingDoesNotStackSignalHandlers() =
        runBlocking<Unit> {
            val session = FakeBlueZDeviceSession()
            val (transport, _) = transport(session)
            repeat(3) {
                transport.connect(options)
                transport.disconnect()
            }
            assertEquals(1, session.registerCalls)
            transport.close()
        }

    @Test
    fun readEchoesAreNotDeliveredAsNotifications() =
        runBlocking<Unit> {
            val session = FakeBlueZDeviceSession().apply { echoReads = true }
            val (transport, _) = transport(session)
            val events = CopyOnWriteArrayList<PeripheralEvent>()
            transport.setEventListener { events += it }
            transport.connect(options)
            val handle =
                transport
                    .discoverServices()
                    .single()
                    .characteristics
                    .single { it.uuid == heartRate }
                    .handle

            assertContentEquals(byteArrayOf(0x4B), transport.read(handle))
            session.simulateValue(FakeBlueZDeviceSession.CHAR_PATH, byteArrayOf(0x4C))
            assertContentEquals(byteArrayOf(0x4C), assertIs<PeripheralEvent.ValueChanged>(events.single()).value)
            transport.close()
        }

    @Test
    fun removeBondWhileConnectedReportsDisconnect() =
        runBlocking<Unit> {
            val session = FakeBlueZDeviceSession()
            val (transport, _) = transport(session)
            val events = CopyOnWriteArrayList<PeripheralEvent>()
            transport.setEventListener { events += it }
            transport.connect(options)
            assertEquals(BondRemovalResult.Success, transport.removeBond())
            assertIs<ConnectionLost>(assertIs<PeripheralEvent.Disconnected>(events.last()).error)
            transport.close()
        }

    @Test
    fun bondingUsesPairAndRemoveDevice() =
        runBlocking<Unit> {
            val session = FakeBlueZDeviceSession()
            val (transport, _) = transport(session)
            assertEquals(BondState.NotBonded, transport.bondState())
            transport.createBond()
            assertEquals(1, session.pairCalls)
            assertEquals(BondState.Bonded, transport.bondState())
            assertEquals(BondRemovalResult.Success, transport.removeBond())
            assertEquals(1, session.removeCalls)
            transport.close()
        }

    @Test
    fun genericPeripheralRunsEndToEndOverBlueZTransport() =
        runBlocking<Unit> {
            val session = FakeBlueZDeviceSession()
            val (transport, _) = transport(session)
            val peripheral = peripheralOver(transport)

            peripheral.connect(options.copy(bondingPreference = BondingPreference.Required))
            assertIs<State.Connected.Ready>(peripheral.state.value)
            assertEquals(BondState.Bonded, peripheral.bondState.value)
            assertEquals(244, peripheral.maximumWriteValueLength.value)
            assertTrue(peripheral.supportsReliableWrite)

            val characteristic = assertNotNull(peripheral.findCharacteristic(heartRateService, heartRate))
            assertContentEquals(byteArrayOf(0x4B), peripheral.read(characteristic))
            peripheral.observeValues(characteristic).test {
                withTimeout(2.seconds) { while (session.notifyStarts.isEmpty()) delay(5) }
                delay(20)
                session.simulateValue(FakeBlueZDeviceSession.CHAR_PATH, byteArrayOf(0x21))
                assertContentEquals(byteArrayOf(0x21), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            withTimeout(2.seconds) { while (session.notifyStops.isEmpty()) delay(5) }

            session.simulateDisconnected()
            withTimeout(2.seconds) { peripheral.state.first { it is State.Disconnected } }
            peripheral.close()
            withTimeout(2.seconds) { while (session.closeCalls == 0) delay(5) }
        }

    private companion object {
        val LINK_LOSS_GRACE = 50.milliseconds
    }
}
