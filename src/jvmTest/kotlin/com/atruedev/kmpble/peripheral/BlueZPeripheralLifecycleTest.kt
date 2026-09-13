package com.atruedev.kmpble.peripheral

import app.cash.turbine.test
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.OperationTimeouts
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.peripheral.internal.PeripheralRegistry
import com.atruedev.kmpble.peripheral.state.State
import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.scanner.BlueZ
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class BlueZPeripheralLifecycleTest {
    private val testOptions =
        ConnectionOptions(
            timeouts =
                OperationTimeouts(
                    connect = 5.seconds,
                    serviceDiscovery = 5.seconds,
                ),
        )

    @AfterTest
    fun tearDown() {
        PeripheralRegistry.clear()
    }

    @Test
    fun connectDiscoversServicesAndReadsCharacteristic() =
        runBlocking {
            val session = FakeBlueZDeviceSession()
            val factory = FakeBlueZDeviceSessionFactory(session)
            val peripheral =
                BlueZPeripheral(
                    devicePath = session.devicePath,
                    address = session.address,
                    sessionFactory = factory,
                )

            peripheral.connect(testOptions)
            assertIs<State.Connected.Ready>(peripheral.state.value)
            assertEquals(1, session.connectCalls)
            assertEquals(1, session.registerHandlersCalls)
            assertEquals(1, session.refreshGattServicesCalls)
            assertEquals(1, factory.openCalls)

            val heartRate =
                peripheral.findCharacteristic(
                    Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb"),
                    Uuid.parse("00002a37-0000-1000-8000-00805f9b34fb"),
                )
            assertTrue(heartRate != null)

            val value = peripheral.read(heartRate)
            assertEquals(0x4B.toByte(), value.single())
            assertEquals(1, session.readCalls.size)

            peripheral.disconnect()
            session.awaitDisconnect()
            assertTrue(peripheral.state.value is State.Disconnected)
            assertEquals(1, session.disconnectCalls)
            assertEquals(1, session.unregisterHandlersCalls)

            peripheral.close()
            assertEquals(1, session.closeConnectionCalls)
        }

    @Test
    fun writeCharacteristicUsesBlueZWriteType() =
        runBlocking {
            val session = FakeBlueZDeviceSession()
            val peripheral =
                BlueZPeripheral(
                    devicePath = session.devicePath,
                    address = session.address,
                    sessionFactory = FakeBlueZDeviceSessionFactory(session),
                )
            peripheral.connect(testOptions)

            val characteristic =
                checkNotNull(
                    peripheral.findCharacteristic(
                        Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb"),
                        Uuid.parse("00002a37-0000-1000-8000-00805f9b34fb"),
                    ),
                )
            peripheral.write(characteristic, byteArrayOf(0x01, 0x02), WriteType.WithResponse)
            assertEquals(
                byteArrayOf(0x01, 0x02).toList(),
                session.writeCalls
                    .single()
                    .second
                    .toList(),
            )

            peripheral.close()
        }

    @Test
    fun observeReceivesSimulatedNotifyValues() =
        runBlocking {
            val session = FakeBlueZDeviceSession()
            val peripheral =
                BlueZPeripheral(
                    devicePath = session.devicePath,
                    address = session.address,
                    sessionFactory = FakeBlueZDeviceSessionFactory(session),
                )
            peripheral.connect(testOptions)

            val characteristic =
                checkNotNull(
                    peripheral.findCharacteristic(
                        Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb"),
                        Uuid.parse("00002a37-0000-1000-8000-00805f9b34fb"),
                    ),
                )

            peripheral.observeValues(characteristic).test {
                delay(100)
                assertEquals(1, session.notifyCalls.size)
                session.simulateCharacteristicValue(
                    charPath = session.notifyCalls.single(),
                    value = byteArrayOf(0x55),
                )
                assertEquals(byteArrayOf(0x55).toList(), awaitItem().toList())
                cancelAndIgnoreRemainingEvents()
            }

            peripheral.close()
        }

    @Test
    fun connectFailureSurfacesDisconnectedState() =
        runBlocking {
            val session =
                FakeBlueZDeviceSession(connectResult = Result.failure(IllegalStateException("connect rejected")))
            val peripheral =
                BlueZPeripheral(
                    devicePath = session.devicePath,
                    address = session.address,
                    sessionFactory = FakeBlueZDeviceSessionFactory(session),
                )

            assertFailsWith<com.atruedev.kmpble.error.BleException> {
                peripheral.connect(testOptions)
            }
            assertTrue(peripheral.state.value is State.Disconnected)
            peripheral.close()
        }

    @Test
    fun remoteDisconnectCleansUpSession() =
        runBlocking {
            val session = FakeBlueZDeviceSession()
            val peripheral =
                BlueZPeripheral(
                    devicePath = session.devicePath,
                    address = session.address,
                    sessionFactory = FakeBlueZDeviceSessionFactory(session),
                )
            peripheral.connect(testOptions)
            session.simulateDisconnected()
            delay(200)
            assertTrue(peripheral.state.value is State.Disconnected)
            peripheral.close()
        }

    @Test
    fun toPeripheralRemainsDisabledByDefault() {
        val ad = sampleAdvertisement()
        assertFailsWith<UnsupportedOperationException> { ad.toPeripheral() }
    }

    @Test
    fun toBlueZPeripheralRequiresScannerPlatformContext() {
        val ad = sampleAdvertisement()
        ad.platformContext = null
        assertFailsWith<IllegalStateException> { ad.toBlueZPeripheral() }
    }

    @Test
    fun factoryOptInUsesBlueZPeripheralWhenPropertySet() {
        val previous = System.getProperty(BlueZ.ENABLE_PROPERTY)
        try {
            System.setProperty(BlueZ.ENABLE_PROPERTY, "true")
            val peripheral = sampleAdvertisement().toPeripheral()
            assertIs<BlueZPeripheral>(peripheral)
            peripheral.close()
        } finally {
            if (previous == null) {
                System.clearProperty(BlueZ.ENABLE_PROPERTY)
            } else {
                System.setProperty(BlueZ.ENABLE_PROPERTY, previous)
            }
        }
    }

    private fun sampleAdvertisement(): Advertisement =
        Advertisement(
            identifier = com.atruedev.kmpble.Identifier("AA:BB:CC:DD:EE:FF"),
            name = "test",
            rssi = -50,
            txPower = null,
            isConnectable = true,
            serviceUuids = emptyList(),
            manufacturerData = emptyMap(),
            serviceData = emptyMap(),
            timestampNanos = 0,
            isLegacy = true,
            rawAdvertising = null,
        ).also { it.platformContext = "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF" }
}
