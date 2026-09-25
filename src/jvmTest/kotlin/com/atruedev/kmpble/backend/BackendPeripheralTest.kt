package com.atruedev.kmpble.backend

import app.cash.turbine.test
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.backend.internal.BackendPeripheral
import com.atruedev.kmpble.bonding.BondRemovalResult
import com.atruedev.kmpble.bonding.BondState
import com.atruedev.kmpble.bonding.PairingHandler
import com.atruedev.kmpble.bonding.PairingResponse
import com.atruedev.kmpble.connection.BondingPreference
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.EncryptionLevel
import com.atruedev.kmpble.connection.OperationTimeouts
import com.atruedev.kmpble.connection.ReconnectionStrategy
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.ConnectionFailed
import com.atruedev.kmpble.error.ConnectionFailureReason
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.error.PeripheralClosed
import com.atruedev.kmpble.error.StaleGattHandle
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.l2cap.L2capChannelError
import com.atruedev.kmpble.l2cap.L2capChannelState
import com.atruedev.kmpble.l2cap.L2capException
import com.atruedev.kmpble.peripheral.internal.PeripheralRegistry
import com.atruedev.kmpble.peripheral.state.State
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

@OptIn(KmpBleBackendApi::class, ExperimentalUuidApi::class)
class BackendPeripheralTest {
    private val identifier = Identifier("AA:BB:CC:DD:EE:FF")
    private val options =
        ConnectionOptions(timeouts = OperationTimeouts(connect = 2.seconds, serviceDiscovery = 2.seconds))

    @AfterTest
    fun tearDown() {
        PeripheralRegistry.clear()
    }

    private fun peripheral(
        transport: FakePeripheralTransport = FakePeripheralTransport(),
    ): Pair<BackendPeripheral, FakePeripheralTransport> = BackendPeripheral(identifier, transport) to transport

    private fun BackendPeripheral.heartRate(): Characteristic =
        assertNotNull(findCharacteristic(FakePeripheralTransport.SERVICE_UUID, FakePeripheralTransport.HEART_RATE_UUID))

    private fun BackendPeripheral.control(): Characteristic =
        assertNotNull(findCharacteristic(FakePeripheralTransport.SERVICE_UUID, FakePeripheralTransport.CONTROL_UUID))

    private suspend fun awaitCollector(peripheral: BackendPeripheral) {
        withTimeout(2.seconds) {
            while (!peripheral.observationManager.hasCollectors(
                    FakePeripheralTransport.SERVICE_UUID,
                    FakePeripheralTransport.HEART_RATE_UUID,
                )
            ) {
                delay(5)
            }
        }
    }

    private suspend fun BackendPeripheral.awaitState(predicate: (State) -> Boolean): State =
        withTimeout(3.seconds) { state.first(predicate) }

    @Test
    fun connectDiscoversServicesAndAppliesMtu() =
        runBlocking<Unit> {
            val (peripheral, transport) = peripheral()
            peripheral.connect(options)

            assertIs<State.Connected.Ready>(peripheral.state.value)
            assertEquals(1, transport.connectCalls)
            assertEquals(1, transport.discoverCalls)
            assertEquals(185, peripheral.mtu.value)
            assertEquals(182, peripheral.maximumWriteValueLength.value)
            assertEquals(BondState.NotBonded, peripheral.bondState.value)
            val heartRate = peripheral.heartRate()
            assertEquals(1, heartRate.descriptors.size)
            assertTrue(peripheral.supportsReliableWrite)
            peripheral.close()
        }

    @Test
    fun gattOperationsResolveTransportHandles() =
        runBlocking<Unit> {
            val (peripheral, transport) = peripheral()
            transport.values[FakePeripheralTransport.HEART_RATE_HANDLE] = byteArrayOf(0x55)
            peripheral.connect(options)

            assertContentEquals(byteArrayOf(0x55), peripheral.read(peripheral.heartRate()))
            peripheral.write(peripheral.control(), byteArrayOf(1, 2), WriteType.WithoutResponse)
            val write = transport.writes.single()
            assertEquals(FakePeripheralTransport.CONTROL_HANDLE, write.first)
            assertEquals(WriteType.WithoutResponse, write.third)

            val cccd = peripheral.heartRate().descriptors.single()
            assertContentEquals(byteArrayOf(0x01, 0x00), peripheral.readDescriptor(cccd))
            assertEquals(-42, peripheral.readRssi())
            assertEquals(185, peripheral.requestMtu(517))
            peripheral.close()
        }

    @Test
    fun writeSplitsPayloadByMaximumWriteLength() =
        runBlocking<Unit> {
            val transport = FakePeripheralTransport().apply { mtuValue = 23 }
            val (peripheral, _) = peripheral(transport)
            peripheral.connect(options)

            peripheral.write(peripheral.control(), ByteArray(45) { it.toByte() }, WriteType.WithResponse)
            assertEquals(listOf(20, 20, 5), transport.writes.map { it.second.size })
            peripheral.close()
        }

    @Test
    fun observeEnablesNotificationsAndDeliversValues() =
        runBlocking<Unit> {
            val (peripheral, transport) = peripheral()
            peripheral.connect(options)
            val heartRate = peripheral.heartRate()

            peripheral.observeValues(heartRate).test {
                awaitCollector(peripheral)
                withTimeout(2.seconds) { while (transport.notifyCalls.isEmpty()) delay(5) }
                assertEquals(FakePeripheralTransport.HEART_RATE_HANDLE to true, transport.notifyCalls.single())
                transport.emit(
                    PeripheralEvent.ValueChanged(FakePeripheralTransport.HEART_RATE_HANDLE, byteArrayOf(0x42)),
                )
                assertContentEquals(byteArrayOf(0x42), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            withTimeout(2.seconds) { while (transport.notifyCalls.size < 2) delay(5) }
            assertEquals(FakePeripheralTransport.HEART_RATE_HANDLE to false, transport.notifyCalls.last())
            peripheral.close()
        }

    @Test
    fun remoteDisconnectEndsInDisconnectedAndNotifiesObservers() =
        runBlocking<Unit> {
            val (peripheral, transport) = peripheral()
            peripheral.connect(options)

            peripheral.observe(peripheral.heartRate()).test {
                awaitCollector(peripheral)
                transport.dropLink()
                assertEquals(Observation.Disconnected, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            val state = peripheral.awaitState { it is State.Disconnected }
            val error = assertIs<State.Disconnected.ByError>(state).error
            assertEquals(ConnectionFailureReason.LINK_LOSS, assertIs<ConnectionLost>(error).failureReason)
            assertTrue(peripheral.services.value == null)
            peripheral.close()
        }

    @Test
    fun disconnectEndsByRequest() =
        runBlocking<Unit> {
            val (peripheral, transport) = peripheral()
            peripheral.connect(options)
            val heartRate = peripheral.heartRate()
            peripheral.disconnect()

            assertIs<State.Disconnected.ByRequest>(peripheral.state.value)
            assertEquals(1, transport.disconnectCalls)
            assertFailsWith<BleException> { peripheral.read(heartRate) }
            peripheral.close()
        }

    @Test
    fun connectFailureSurfacesTypedErrorAndReleasesLink() =
        runBlocking<Unit> {
            val transport =
                FakePeripheralTransport().apply {
                    connectFailure =
                        BleException(ConnectionFailed("rejected", ConnectionFailureReason.CONNECTION_REJECTED))
                }
            val (peripheral, _) = peripheral(transport)

            val error = assertFailsWith<BleException> { peripheral.connect(options) }.error
            assertEquals(ConnectionFailureReason.CONNECTION_REJECTED, assertIs<ConnectionFailed>(error).failureReason)
            assertIs<State.Disconnected.ByError>(peripheral.state.value)
            assertEquals(1, transport.disconnectCalls)
            peripheral.close()
        }

    @Test
    fun connectTimeoutMapsToTimeoutReason() =
        runBlocking<Unit> {
            val transport = FakePeripheralTransport().apply { connectGate = CompletableDeferred() }
            val (peripheral, _) = peripheral(transport)
            val quick = ConnectionOptions(timeouts = OperationTimeouts(connect = 150.milliseconds))

            val error = assertFailsWith<BleException> { peripheral.connect(quick) }.error
            assertEquals(ConnectionFailureReason.TIMEOUT, assertIs<ConnectionFailed>(error).failureReason)
            assertIs<State.Disconnected.ByError>(peripheral.state.value)
            peripheral.close()
        }

    @Test
    fun linkLossWhileConnectingFailsTheConnect() =
        runBlocking<Unit> {
            val gate = CompletableDeferred<Unit>()
            val transport = FakePeripheralTransport().apply { connectGate = gate }
            val (peripheral, _) = peripheral(transport)

            val failure = CompletableDeferred<Throwable>()
            val job = launchCatching(failure) { peripheral.connect(options) }
            withTimeout(2.seconds) { while (transport.connectCalls == 0) delay(5) }
            transport.emit(PeripheralEvent.Disconnected(ConnectionLost("dropped")))
            delay(50)
            gate.complete(Unit)
            job.join()

            val error = assertIs<BleException>(failure.await()).error
            assertEquals("dropped", assertIs<ConnectionLost>(error).reason)
            assertIs<State.Disconnected.ByError>(peripheral.state.value)
            peripheral.close()
        }

    @Test
    fun bondingRequiredPairsBeforeDiscovery() =
        runBlocking<Unit> {
            val (peripheral, transport) = peripheral()
            peripheral.connect(ConnectionOptions(bondingPreference = BondingPreference.Required))

            assertEquals(1, transport.bondCalls)
            assertEquals(BondState.Bonded, peripheral.bondState.value)
            assertEquals(EncryptionLevel.ESTABLISHED, peripheral.encryptionLevel.value)
            assertIs<State.Connected.Ready>(peripheral.state.value)

            assertEquals(BondRemovalResult.Success, peripheral.removeBond())
            withTimeout(2.seconds) { peripheral.bondState.first { it == BondState.NotBonded } }
            peripheral.close()
        }

    @Test
    fun bondingFailureDisconnects() =
        runBlocking<Unit> {
            val transport = FakePeripheralTransport().apply { bondFailure = IllegalStateException("no agent") }
            val (peripheral, _) = peripheral(transport)

            val error =
                assertFailsWith<BleException> {
                    peripheral.connect(ConnectionOptions(bondingPreference = BondingPreference.Required))
                }.error
            assertEquals(ConnectionFailureReason.BONDING_FAILED, assertIs<ConnectionFailed>(error).failureReason)
            assertIs<State.Disconnected.ByError>(peripheral.state.value)
            assertEquals(BondState.NotBonded, peripheral.bondState.value)
            peripheral.close()
        }

    @Test
    fun reconnectionStrategyReconnectsAfterLinkLoss() =
        runBlocking<Unit> {
            val (peripheral, transport) = peripheral()
            peripheral.connect(
                options.copy(
                    reconnectionStrategy = ReconnectionStrategy.LinearBackoff(delay = 50.milliseconds, maxAttempts = 3),
                ),
            )
            transport.dropLink()

            withTimeout(3.seconds) { while (transport.connectCalls < 2) delay(10) }
            peripheral.awaitState { it is State.Connected.Ready }
            peripheral.disconnect()
            peripheral.close()
        }

    @Test
    fun servicesChangedRediscoversAndInvalidatesOldHandles() =
        runBlocking<Unit> {
            val (peripheral, transport) = peripheral()
            peripheral.connect(options)
            val stale = peripheral.heartRate()

            transport.emit(PeripheralEvent.ServicesChanged)
            withTimeout(2.seconds) { while (transport.discoverCalls < 2) delay(5) }
            peripheral.awaitState { it is State.Connected.Ready }
            withTimeout(2.seconds) {
                peripheral.services.first { services ->
                    services?.single()?.characteristics?.none {
                        it ===
                            stale
                    } ==
                        true
                }
            }

            val error = assertFailsWith<BleException> { peripheral.read(stale) }.error
            assertIs<StaleGattHandle>(error)
            assertContentEquals(byteArrayOf(0x4B), peripheral.read(peripheral.heartRate()))
            peripheral.close()
        }

    @Test
    fun servicesChangedDuringConnectIsResolvedBeforeReady() =
        runBlocking<Unit> {
            val transport = FakePeripheralTransport()
            transport.onDiscover = {
                if (transport.discoverCalls == 1) {
                    transport.emit(PeripheralEvent.ServicesChanged)
                    delay(50)
                }
            }
            val (peripheral, _) = peripheral(transport)
            val states = mutableListOf<State>()
            val recorder = launch { peripheral.state.collect { states += it } }

            peripheral.connect(options)
            val heartRate = peripheral.heartRate()
            delay(100)

            assertEquals(2, transport.discoverCalls)
            assertIs<State.Connected.Ready>(peripheral.state.value)
            assertTrue(states.none { it is State.Connected.ServiceChanged })
            assertContentEquals(byteArrayOf(0x4B), peripheral.read(heartRate))
            recorder.cancel()
            peripheral.close()
        }

    @Test
    fun disconnectRightAfterObservingStillDisablesNotifications() =
        runBlocking<Unit> {
            val (peripheral, transport) = peripheral()
            transport.setNotifyLatency = 50.milliseconds
            peripheral.connect(options)
            val observer = launch { peripheral.observeValues(peripheral.heartRate()).collect {} }
            awaitCollector(peripheral)
            withTimeout(2.seconds) { while (transport.notifyCalls.isEmpty()) delay(5) }

            observer.cancelAndJoin()
            peripheral.disconnect()

            assertEquals(FakePeripheralTransport.HEART_RATE_HANDLE to false, transport.notifyCalls.last())
            assertIs<State.Disconnected.ByRequest>(peripheral.state.value)
            peripheral.close()
        }

    @Test
    fun mtuChangedEventUpdatesMaximumWriteLength() =
        runBlocking<Unit> {
            val (peripheral, transport) = peripheral()
            peripheral.connect(options)
            transport.emit(PeripheralEvent.MtuChanged(247))

            withTimeout(2.seconds) { peripheral.mtu.first { it == 247 } }
            assertEquals(244, peripheral.maximumWriteValueLength.value)
            peripheral.close()
        }

    @Test
    fun reliableWriteRoutesToTransportOrThrowsWhenUnsupported() =
        runBlocking<Unit> {
            val (peripheral, transport) = peripheral()
            peripheral.connect(options)
            peripheral.writeReliable(peripheral.control(), byteArrayOf(9))
            assertEquals(FakePeripheralTransport.CONTROL_HANDLE, transport.reliableWrites.single().first)
            peripheral.close()

            val (plain, _) = peripheral(FakePeripheralTransport(features = PeripheralFeatures()))
            plain.connect(options)
            assertFailsWith<UnsupportedOperationException> { plain.writeReliable(plain.control(), byteArrayOf(9)) }
            plain.close()
        }

    @Test
    fun pairingHandlerIsForwardedToTransport() =
        runBlocking<Unit> {
            val (peripheral, transport) = peripheral()
            val handler = PairingHandler { PairingResponse.Confirm(true) }
            peripheral.connect(options.copy(pairingHandler = handler))
            assertSame(handler, transport.lastPairingHandler)
            peripheral.close()
        }

    @Test
    fun closedPeripheralRejectsOperationsWithTypedError() =
        runBlocking<Unit> {
            val (peripheral, transport) = peripheral()
            peripheral.connect(options)
            val heartRate = peripheral.heartRate()
            peripheral.close()

            assertEquals(1, transport.closeCalls)
            assertIs<PeripheralClosed>(assertFailsWith<BleException> { peripheral.read(heartRate) }.error)
            assertIs<PeripheralClosed>(assertFailsWith<BleException> { peripheral.connect(options) }.error)
        }

    @Test
    fun l2capChannelCarriesDataAndClosesOnRemoteEnd() =
        runBlocking<Unit> {
            val (peripheral, transport) = peripheral()
            peripheral.connect(options)

            val channel = peripheral.openL2capChannel(psm = 0x81)
            assertTrue(channel.isOpen)
            channel.write(byteArrayOf(1, 2, 3))
            val stream = transport.l2capStreams.single()
            assertContentEquals(byteArrayOf(1, 2, 3), stream.written.single())

            channel.incoming.test {
                stream.streamListener?.onData(byteArrayOf(7))
                assertContentEquals(byteArrayOf(7), awaitItem())
                stream.streamListener?.onClosed(null)
                awaitComplete()
            }
            assertIs<L2capChannelError.RemoteDisconnected>(withTimeout(2.seconds) { channel.errors.first() })
            assertEquals(L2capChannelState.Closed, channel.state.value)
            assertTrue(stream.closed)
            assertFailsWith<L2capException.OpenFailed> {
                peripheral.openL2capChannel(
                    psm = FakePeripheralTransport.REJECTED_PSM,
                )
            }
            peripheral.close()
        }

    @Test
    fun linkLossClosesOpenL2capChannels() =
        runBlocking<Unit> {
            val (peripheral, transport) = peripheral()
            peripheral.connect(options)
            val channel = peripheral.openL2capChannel(psm = 0x81)

            transport.dropLink()
            withTimeout(2.seconds) { channel.state.first { it == L2capChannelState.Closed } }
            assertTrue(transport.l2capStreams.single().closed)
            assertFailsWith<L2capException.NotConnected> { peripheral.openL2capChannel(psm = 0x81) }
            peripheral.close()
        }

    @Test
    fun backendPeripheralFactoryIsSharedPerIdentifier() {
        val backend = FakeBackend()
        val first = backend.peripheral(identifier, "handle-1")
        val second = backend.peripheral(identifier, "handle-2")
        assertSame(first, second)
        assertEquals(listOf(identifier to "handle-1"), backend.createdPeripherals.toList())
        first.close()
    }
}

private fun CoroutineScope.launchCatching(
    failure: CompletableDeferred<Throwable>,
    block: suspend () -> Unit,
): Job =
    launch {
        try {
            block()
        } catch (e: Throwable) {
            failure.complete(e)
        }
    }
