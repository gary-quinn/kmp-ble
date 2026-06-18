package com.atruedev.kmpble.conformance

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.connection.PhyUpdate
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Observation
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.l2cap.L2capChannel
import com.atruedev.kmpble.l2cap.L2capException
import com.atruedev.kmpble.scanner.ScanEvent
import com.atruedev.kmpble.scanner.uuidFrom
import com.atruedev.kmpble.testing.FakeL2capChannel
import com.atruedev.kmpble.testing.FakeL2capListener
import com.atruedev.kmpble.testing.FakePeripheralBuilder
import com.atruedev.kmpble.testing.FakeScanner
import com.atruedev.kmpble.testing.FakeScannerBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Shared conformance test suite for core BLE flows.
 *
 * Subclass in platform-specific test source sets (jvmTest, iosTest) to verify
 * behavioral consistency across KMP targets. Tests use [FakeScanner] and
 * [FakePeripheralBuilder] for deterministic, virtual-time execution.
 *
 * ## Platform subclasses
 * ```
 * // jvmTest
 * class JvmBleConformanceTest : BleConformanceTest()
 *
 * // iosTest
 * class IosBleConformanceTest : BleConformanceTest()
 * ```
 *
 * Override [buildScanner] or [buildPeripheral] to inject platform-specific
 * behavior variations.
 */
public abstract class BleConformanceTest {
    /** Factory for scanner instances. Override to inject platform behavior. */
    protected open fun buildScanner(block: FakeScannerBuilder.() -> Unit = {}): FakeScanner = FakeScanner(block)

    /** Factory for peripheral builder. Override to inject platform behavior. */
    protected open fun buildPeripheral(block: FakePeripheralBuilder.() -> Unit = {}) =
        FakePeripheralBuilder().apply(block).build()

    // -- Scan conformance --

    @Test
    fun `scan emits found event for advertising peripheral`() =
        runTest {
            val scanner =
                buildScanner {
                    advertisement {
                        identifier("AA:BB:CC:DD:EE:FF")
                        name("ConformanceDevice")
                        rssi(-50)
                        serviceUuids("180d")
                    }
                }

            val event =
                scanner.scanEvents
                    .mapNotNull { it as? ScanEvent.Found }
                    .take(1)
                    .first()

            assertEquals("ConformanceDevice", event.advertisement.name)
            assertTrue(event.advertisement.rssi <= 0)
            scanner.close()
        }

    // -- Connection conformance --

    @Test
    fun `connect transitions peripheral to connected state`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }

            peripheral.connect(ConnectionOptions())

            assertTrue(peripheral.state.value is State.Connected)
            peripheral.close()
        }

    @Test
    fun `disconnect transitions peripheral to disconnecting state`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }

            peripheral.connect(ConnectionOptions())
            peripheral.disconnect()

            val state = peripheral.state.value
            assertTrue(
                state is State.Disconnecting || state is State.Disconnected,
                "Expected Disconnecting or Disconnected, got $state",
            )
            peripheral.close()
        }

    // -- GATT conformance --

    @Test
    fun `service discovery returns configured services`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("1800") {
                        characteristic("2a00") { properties(read = true) }
                    }
                    service("180a") {
                        characteristic("2a29") { properties(read = true) }
                    }
                }

            peripheral.connect(ConnectionOptions())
            val services = peripheral.refreshServices()

            assertEquals(2, services.size, "Should discover both configured services")
            assertEquals(
                uuidFrom("1800"),
                services[0].uuid,
            )
            assertEquals(
                uuidFrom("180a"),
                services[1].uuid,
            )
            peripheral.close()
        }

    @Test
    fun `gatt read returns configured value`() =
        runTest {
            val expectedData = byteArrayOf(0x4E, 0x6F, 0x75, 0x73)
            val peripheral =
                buildPeripheral {
                    service("180a") {
                        characteristic("2a29") {
                            properties(read = true)
                            onRead { expectedData }
                        }
                    }
                }

            peripheral.connect(ConnectionOptions())
            peripheral.refreshServices()
            val char =
                peripheral.services.value!!
                    .first { it.uuid == uuidFrom("180a") }
                    .characteristics
                    .first { it.uuid == uuidFrom("2a29") }

            val result = peripheral.read(char)

            assertContentEquals(expectedData, result)
            peripheral.close()
        }

    @Test
    fun `gatt write with response completes`() =
        runTest {
            val writeData = byteArrayOf(0x01, 0x02, 0x03)
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(write = true) }
                    }
                }

            peripheral.connect(ConnectionOptions())
            peripheral.refreshServices()
            val char =
                peripheral.services.value!!
                    .first { it.uuid == uuidFrom("180d") }
                    .characteristics
                    .first { it.uuid == uuidFrom("2a37") }

            peripheral.write(char, writeData, WriteType.WithResponse)
            // Write completes without throw - success
            peripheral.close()
        }

    @Test
    fun `notification observation receives emitted value`() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        runTest(scheduler) {
            val peripheral =
                FakePeripheralBuilder()
                    .observationDispatcher(dispatcher)
                    .apply {
                        service("180d") {
                            characteristic("2a37") { properties(notify = true) }
                        }
                    }.build()

            peripheral.connect(ConnectionOptions())
            scheduler.runCurrent()
            peripheral.refreshServices()
            val char =
                peripheral.services.value!!
                    .first { it.uuid == uuidFrom("180d") }
                    .characteristics
                    .first { it.uuid == uuidFrom("2a37") }

            val observations = mutableListOf<Observation>()
            val job: Job =
                backgroundScope.launch {
                    peripheral
                        .observe(char, BackpressureStrategy.Unbounded)
                        .collect { observations.add(it) }
                }
            scheduler.runCurrent()

            peripheral.emitObservationValue("180d", "2a37", byteArrayOf(0x42))
            scheduler.runCurrent()

            val value = observations.filterIsInstance<Observation.Value>().single()
            assertContentEquals(byteArrayOf(0x42), value.data)

            job.cancel()
            peripheral.close()
        }
    }

    // -- Reconnection conformance --

    @Test
    fun `reconnect after disconnect restores connected state`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }

            peripheral.connect(ConnectionOptions())
            assertTrue(peripheral.state.value is State.Connected)

            peripheral.disconnect()
            peripheral.connect(ConnectionOptions())
            assertTrue(peripheral.state.value is State.Connected)

            peripheral.close()
        }

    // -- State consistency --

    @Test
    fun `services property non-null only after discovery`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("1800") {
                        characteristic("2a00") { properties(read = true) }
                    }
                }

            // Before discovery, services may be null or empty
            peripheral.connect(ConnectionOptions())
            peripheral.refreshServices()

            val services = peripheral.services.value
            assertNotNull(services, "Services should be non-null after discovery")
            assertTrue(services.isNotEmpty(), "Services should not be empty after discovery")
            peripheral.close()
        }

    // -- L2CAP conformance --

    @Test
    fun `l2cap channel open returns channel with correct psm`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    onOpenL2capChannel { psm, _ -> FakeL2capChannel(psm) }
                }

            peripheral.connect(ConnectionOptions())
            val channel = peripheral.openL2capChannel(psm = 0x25)

            assertEquals(0x25, channel.psm)
            assertTrue(channel.isOpen)
            channel.close()
            peripheral.close()
        }

    @Test
    fun `l2cap channel write records data`() =
        runTest {
            val channel = FakeL2capChannel(psm = 0x25)
            val peripheral =
                buildPeripheral {
                    onOpenL2capChannel { _, _ -> channel }
                }

            peripheral.connect(ConnectionOptions())
            val opened = peripheral.openL2capChannel(psm = 0x25)
            val data = byteArrayOf(0x01, 0x02, 0x03)

            opened.write(data)

            val written = (opened as FakeL2capChannel).getWrittenData()
            assertEquals(1, written.size)
            assertContentEquals(data, written[0])
            opened.close()
            peripheral.close()
        }

    @Test
    fun `l2cap channel incoming flow receives emitted data`() =
        runTest {
            val channel = FakeL2capChannel(psm = 0x25)
            val peripheral =
                buildPeripheral {
                    onOpenL2capChannel { _, _ -> channel }
                }

            peripheral.connect(ConnectionOptions())
            val opened = peripheral.openL2capChannel(psm = 0x25)
            val incomingData = mutableListOf<ByteArray>()
            val job = backgroundScope.launch { opened.incoming.collect { incomingData.add(it) } }
            testScheduler.runCurrent()

            val data = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
            channel.emitIncoming(data)
            testScheduler.runCurrent()

            assertEquals(1, incomingData.size, "Should have received emitted data")
            assertContentEquals(data, incomingData[0])
            job.cancel()
            opened.close()
            peripheral.close()
        }

    @Test
    fun `l2cap channel close stops writes`() =
        runTest {
            val channel = FakeL2capChannel(psm = 0x25)
            val peripheral =
                buildPeripheral {
                    onOpenL2capChannel { _, _ -> channel }
                }

            peripheral.connect(ConnectionOptions())
            val opened = peripheral.openL2capChannel(psm = 0x25)
            opened.close()
            assertTrue(!opened.isOpen)

            assertFailsWith<L2capException> {
                opened.write(byteArrayOf(0x01))
            }
            peripheral.close()
        }

    @Test
    fun `l2cap open fails when not connected`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    onOpenL2capChannel { psm, _ -> FakeL2capChannel(psm) }
                }

            assertFailsWith<L2capException> {
                peripheral.openL2capChannel(psm = 0x25)
            }
            peripheral.close()
        }

    @Test
    fun `l2cap open fails with mtu zero`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    onOpenL2capChannel { psm, _ -> FakeL2capChannel(psm) }
                }

            peripheral.connect(ConnectionOptions())

            assertFailsWith<IllegalArgumentException> {
                peripheral.openL2capChannel(psm = 0x25, mtu = 0)
            }
            peripheral.close()
        }

    @Test
    fun `l2cap listener open assigns psm`() =
        runTest {
            val listener = FakeL2capListener(assignedPsm = 0x80)

            listener.open()
            assertEquals(0x80, listener.psm)
            assertTrue(listener.isOpen.value)
            listener.close()
        }

    @Test
    fun `l2cap listener emits accepted channels`() =
        runTest {
            val listener = FakeL2capListener(assignedPsm = 0x81)

            listener.open()
            val accepted = mutableListOf<L2capChannel>()
            val job = backgroundScope.launch { listener.incoming.collect { accepted.add(it) } }
            testScheduler.runCurrent()

            val channel = FakeL2capChannel(psm = 0x81)
            listener.simulateIncoming(channel)
            testScheduler.runCurrent()

            assertEquals(1, accepted.size, "Should have received accepted channel")
            assertEquals(channel, accepted[0])
            job.cancel()
            channel.close()
            listener.close()
        }

    @Test
    fun `l2cap listener close stops acceptance`() =
        runTest {
            val listener = FakeL2capListener(assignedPsm = 0x82)

            listener.open()
            listener.close()
            assertTrue(!listener.isOpen.value)

            assertFailsWith<IllegalStateException> {
                listener.simulateIncoming(FakeL2capChannel(psm = 0x82))
            }
        }

    // -- PHY conformance --

    @Test
    @OptIn(ExperimentalBleApi::class)
    fun `setPreferredPhy returns PhyResult with requested PHY`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }
            peripheral.connect(ConnectionOptions())
            val result = peripheral.setPreferredPhy(Phy.Le2M, Phy.Le2M)
            assertEquals(Phy.Le2M, result?.tx)
            assertEquals(Phy.Le2M, result?.rx)
            peripheral.close()
        }

    @Test
    @OptIn(ExperimentalBleApi::class)
    fun `readPhy returns configured PHY values`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }
            peripheral.configurePhy(Phy.LeCoded, Phy.Le2M)
            peripheral.connect(ConnectionOptions())
            val result = peripheral.readPhy()
            assertEquals(Phy.LeCoded, result?.tx)
            assertEquals(Phy.Le2M, result?.rx)
            peripheral.close()
        }

    @Test
    @OptIn(ExperimentalBleApi::class)
    fun `phyUpdate flow receives emitted updates`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }
            peripheral.connect(ConnectionOptions())
            val updates = mutableListOf<PhyUpdate>()
            val job = backgroundScope.launch { peripheral.phyUpdate.collect { updates.add(it) } }
            testScheduler.runCurrent()
            peripheral.emitPhyUpdate(Phy.Le2M, Phy.LeCoded)
            testScheduler.runCurrent()
            assertEquals(1, updates.size)
            assertEquals(Phy.Le2M, updates[0].txPhy)
            assertEquals(Phy.LeCoded, updates[0].rxPhy)
            job.cancel()
            peripheral.close()
        }

    @Test
    @OptIn(ExperimentalBleApi::class)
    fun `setPreferredPhy with LeCoded returns LeCoded`() =
        runTest {
            val peripheral =
                buildPeripheral {
                    service("180d") {
                        characteristic("2a37") { properties(notify = true) }
                    }
                }
            peripheral.connect(ConnectionOptions())
            val result = peripheral.setPreferredPhy(Phy.LeCoded, Phy.LeCoded)
            assertEquals(Phy.LeCoded, result?.tx)
            assertEquals(Phy.LeCoded, result?.rx)
            peripheral.close()
        }
}
