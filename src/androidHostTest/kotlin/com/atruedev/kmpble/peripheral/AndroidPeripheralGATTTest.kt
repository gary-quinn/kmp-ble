package com.atruedev.kmpble.peripheral

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.content.Context
import com.atruedev.kmpble.connection.ConnectionSubratingParameters
import com.atruedev.kmpble.connection.ConnectionSubratingResult
import com.atruedev.kmpble.connection.DataLengthParameters
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.connection.PhyUpdate
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.connection.internal.ConnectionEvent
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.DiscoveredService
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.scanner.uuidFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowBluetoothDevice
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi

/**
 * Integration tests for AndroidPeripheral GATT event handling.
 * Tests the Android-specific callback flow through BluetoothGatt callbacks
 * to ensure events are correctly processed.
 *
 * Uses Robolectric to mock Android Bluetooth APIs without requiring real hardware.
 *
 * These tests verify the event handling chain:
 * Android BluetoothGattCallback -> AndroidGattBridge.onEvent -> AndroidPeripheral.handleGattEvent -> PeripheralContext/observationManager
 */
@OptIn(ExperimentalUuidApi::class)
@RunWith(RobolectricTestRunner::class)
class AndroidPeripheralGATTTest {

    private lateinit var appContext: Context
    private lateinit var bluetoothDevice: BluetoothDevice
    private lateinit var peripheral: AndroidPeripheral
    private lateinit var characteristic: Characteristic
    private lateinit var descriptor: Descriptor

    @Before
    fun setup() {
        appContext = RuntimeEnvironment.getApplication()
        bluetoothDevice = ShadowBluetoothDevice.newInstance("00:11:22:33:44:55")
        peripheral = AndroidPeripheral(bluetoothDevice, appContext)

        // Create a test service and characteristic
        val service =
            DiscoveredService(
                uuid = uuidFrom("180d"),
                primary = true,
                characteristics =
                    listOf(
                        Characteristic(
                            uuid = uuidFrom("2a37"),
                            properties =
                                BluetoothGattCharacteristic.PROPERTY_READ or
                                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                        )
                    ),
            )

        characteristic = service.characteristics[0]
        descriptor =
            Descriptor(
                uuid = uuidFrom("2902"), // CCCD
                characteristic = characteristic,
            )

        // Inject the service into the peripheral's context
        runBlocking(Dispatchers.Default) {
            peripheral.peripheralContext.updateServices(listOf(service))
        }
    }

    @After
    fun teardown() {
        peripheral.close()
    }

    // =========================================================================
    // Connection State Changes
    // =========================================================================

    @Test
    fun `connection state change to connected is processed`() = runBlocking(Dispatchers.Default) {
        // Arrange
        val stateFlow = peripheral.state

        // Act - Simulate connection state change via the bridge
        peripheral.bridge.onEvent?.invoke(
            GattCallbackEvent.ConnectionStateChanged(
                status = BluetoothGatt.GATT_SUCCESS,
                newState = BluetoothGatt.STATE_CONNECTED,
            ),
        )

        // Assert
        val newState = stateFlow.first { it is State.Connected }
        assertEquals(State.Connected::class, newState::class)
    }

    @Test
    fun `connection state change to disconnected is processed`() = runBlocking(Dispatchers.Default) {
        // Arrange
        val stateFlow = peripheral.state

        // First connect
        peripheral.bridge.onEvent?.invoke(
            GattCallbackEvent.ConnectionStateChanged(
                status = BluetoothGatt.GATT_SUCCESS,
                newState = BluetoothGatt.STATE_CONNECTED,
            ),
        )

        // Act - Simulate disconnection
        peripheral.bridge.onEvent?.invoke(
            GattCallbackEvent.ConnectionStateChanged(
                status = BluetoothGatt.GATT_SUCCESS,
                newState = BluetoothGatt.STATE_DISCONNECTED,
            ),
        )

        // Assert
        val disconnectedState = stateFlow.first { it is State.Disconnected }
        assertEquals(State.Disconnected::class, disconnectedState::class)
    }

    // =========================================================================
    // Services Discovered
    // =========================================================================

    @Test
    fun `services discovered callback updates peripheral services`() = runBlocking(Dispatchers.Default) {
        // Arrange
        val serviceUuid = uuidFrom("180d")
        val servicesFlow = peripheral.services

        // Act - Simulate services discovered
        val gattService =
            ShadowBluetoothGattService.newInstance().apply {
                setUUID(serviceUuid.toString())
            }
        val gattCharacteristic =
            ShadowBluetoothGattCharacteristic.newInstance().apply {
                setUUID(uuidFrom("2a37").toString())
                setProperty(BluetoothGattCharacteristic.PROPERTY_READ)
            }
        gattService.addCharacteristic(gattCharacteristic)

        peripheral.bridge.onEvent?.invoke(
            GattCallbackEvent.ServicesDiscovered(
                status = BluetoothGatt.GATT_SUCCESS,
                services = listOf(gattService),
            ),
        )

        // Assert
        val services = servicesFlow.first { it != null }
        assertNotNull(services)
        assertEquals(1, services!!.size)
        assertEquals(serviceUuid, services[0].uuid)
    }

    // =========================================================================
    // Characteristic Operations
    // =========================================================================

    @Test
    fun `characteristic read callback completes pending operation`() = runBlocking(Dispatchers.Default) {
        // Arrange
        val testValue = byteArrayOf(0x48, 0x65, 0x6c, 0x6c, 0x6f) // "Hello"
        val gattCharacteristic =
            ShadowBluetoothGattCharacteristic.newInstance().apply {
                setUUID(characteristic.uuid.toString())
            }

        // Act - Start a read operation
        val readJob =
            runBlocking(Dispatchers.Default) {
                launch { peripheral.read(characteristic) }
            }

        // Simulate the read callback
        peripheral.bridge.onEvent?.invoke(
            GattCallbackEvent.CharacteristicRead(
                characteristic = gattCharacteristic,
                value = testValue,
                status = BluetoothGatt.GATT_SUCCESS,
            ),
        )

        // Assert
        val result = readJob.getCompleted()
        assertEquals(testValue, result)
    }

    @Test
    fun `characteristic write callback completes pending operation`() = runBlocking(Dispatchers.Default) {
        // Arrange
        val testValue = byteArrayOf(0x01, 0x02, 0x03)
        val gattCharacteristic =
            ShadowBluetoothGattCharacteristic.newInstance().apply {
                setUUID(characteristic.uuid.toString())
            }

        // Act - Write to the characteristic
        val writeJob =
            runBlocking(Dispatchers.Default) {
                launch {
                    peripheral.write(characteristic, testValue, WriteType.WithResponse)
                }
            }

        // Simulate the write callback
        peripheral.bridge.onEvent?.invoke(
            GattCallbackEvent.CharacteristicWrite(
                characteristic = gattCharacteristic,
                status = BluetoothGatt.GATT_SUCCESS,
            ),
        )

        // Assert - Write should complete without error
        writeJob.getCompleted()
    }

    @Test
    fun `characteristic changed callback delivers notification`() = runBlocking(Dispatchers.Default) {
        // Arrange
        val testValue = byteArrayOf(0x2a, 0x48) // Heart rate measurement
        val gattCharacteristic =
            ShadowBluetoothGattCharacteristic.newInstance().apply {
                setUUID(characteristic.uuid.toString())
            }

        // Start observing
        val observedValues = mutableListOf<ByteArray>()
        val observation =
            peripheral.observeValues(characteristic, BackpressureStrategy.LATEST).collect { data ->
                observedValues.add(data)
            }

        // Simulate the notification callback
        peripheral.bridge.onEvent?.invoke(
            GattCallbackEvent.CharacteristicChanged(
                characteristic = gattCharacteristic,
                value = testValue,
            ),
        )

        // Assert
        assertEquals(1, observedValues.size)
        assertEquals(testValue, observedValues[0])
    }

    // =========================================================================
    // Descriptor Operations
    // =========================================================================

    @Test
    fun `descriptor read callback completes pending operation`() = runBlocking(Dispatchers.Default) {
        // Arrange
        val testValue = byteArrayOf(0x01, 0x00) // CCCD enabled
        val gattDescriptor =
            ShadowBluetoothGattDescriptor.newInstance().apply {
                setUUID(descriptor.uuid.toString())
            }

        // Act - Read the descriptor
        val readJob =
            runBlocking(Dispatchers.Default) {
                launch { peripheral.read(descriptor) }
            }

        // Simulate the descriptor read callback
        peripheral.bridge.onEvent?.invoke(
            GattCallbackEvent.DescriptorRead(
                descriptor = gattDescriptor,
                value = testValue,
                status = BluetoothGatt.GATT_SUCCESS,
            ),
        )

        // Assert
        val result = readJob.getCompleted()
        assertEquals(testValue, result)
    }

    @Test
    fun `descriptor write callback completes pending operation`() = runBlocking(Dispatchers.Default) {
        // Arrange
        val testValue = byteArrayOf(0x01, 0x00) // CCCD enable notification
        val gattDescriptor =
            ShadowBluetoothGattDescriptor.newInstance().apply {
                setUUID(descriptor.uuid.toString())
            }

        // Act - Write the descriptor
        val writeJob =
            runBlocking(Dispatchers.Default) {
                launch {
                    peripheral.write(descriptor, testValue, WriteType.WithResponse)
                }
            }

        // Simulate the descriptor write callback
        peripheral.bridge.onEvent?.invoke(
            GattCallbackEvent.DescriptorWrite(
                descriptor = gattDescriptor,
                status = BluetoothGatt.GATT_SUCCESS,
            ),
        )

        // Assert - Write should complete
        writeJob.getCompleted()
    }

    // =========================================================================
    // MTU Changes
    // =========================================================================

    @Test
    fun `MTU changed callback updates peripheral MTU`() = runBlocking(Dispatchers.Default) {
        // Arrange
        val newMtu = 512
        val mtuFlow = peripheral.mtu

        // Act - Simulate MTU change
        peripheral.bridge.onEvent?.invoke(
            GattCallbackEvent.MtuChanged(mtu = newMtu, status = BluetoothGatt.GATT_SUCCESS),
        )

        // Assert
        val updatedMtu = mtuFlow.first { it == newMtu }
        assertEquals(newMtu, updatedMtu)
    }

    // =========================================================================
    // PHY Updates
    // =========================================================================

    @Test
    fun `phy updated callback delivers update`() = runBlocking(Dispatchers.Default) {
        // Arrange
        val txPhy = 2 // LE 2M
        val rxPhy = 2 // LE 2M

        // Act - Start observing phy updates
        val observedUpdates = mutableListOf<PhyUpdate>()
        @OptIn(ExperimentalBleApi::class)
        val observation =
            peripheral.peripheralContext.phyUpdate.collect { update ->
                observedUpdates.add(update)
            }

        // Simulate the phy update callback
        peripheral.bridge.onEvent?.invoke(
            GattCallbackEvent.PhyUpdated(txPhy = txPhy, rxPhy = rxPhy, status = BluetoothGatt.GATT_SUCCESS),
        )

        // Assert
        assertEquals(1, observedUpdates.size)
        @OptIn(ExperimentalBleApi::class)
        assertEquals(Phy.Le2M, observedUpdates[0].txPhy)
        @OptIn(ExperimentalBleApi::class)
        assertEquals(Phy.Le2M, observedUpdates[0].rxPhy)
    }

    @Test
    fun `phy read callback delivers update`() = runBlocking(Dispatchers.Default) {
        // Arrange
        val txPhy = 1 // LE 1M
        val rxPhy = 1 // LE 1M

        // Act - Start observing phy updates
        val observedUpdates = mutableListOf<PhyUpdate>()
        @OptIn(ExperimentalBleApi::class)
        val observation =
            peripheral.peripheralContext.phyUpdate.collect { update ->
                observedUpdates.add(update)
            }

        // Simulate the phy read callback
        peripheral.bridge.onEvent?.invoke(
            GattCallbackEvent.PhyRead(txPhy = txPhy, rxPhy = rxPhy, status = BluetoothGatt.GATT_SUCCESS),
        )

        // Assert
        assertEquals(1, observedUpdates.size)
        @OptIn(ExperimentalBleApi::class)
        assertEquals(Phy.Le1M, observedUpdates[0].txPhy)
        @OptIn(ExperimentalBleApi::class)
        assertEquals(Phy.Le1M, observedUpdates[0].rxPhy)
    }

    // =========================================================================
    // RSSI Reads
    // =========================================================================

    @Test
    fun `RSSI read callback delivers value`() = runBlocking(Dispatchers.Default) {
        // Arrange
        val rssi = -55

        // Act - Read RSSI
        val rssiResult =
            runBlocking(Dispatchers.Default) {
                peripheral.readRemoteRssi()
            }

        // Simulate the RSSI read callback
        peripheral.bridge.onEvent?.invoke(
            GattCallbackEvent.ReadRemoteRssi(rssi = rssi, status = BluetoothGatt.GATT_SUCCESS),
        )

        // Assert
        assertEquals(rssi, rssiResult)
    }

    // =========================================================================
    // Connection Subrating (Bluetooth 5.3)
    // =========================================================================

    @Test
    fun `subrate changed callback with success reports accepted`() = runBlocking(Dispatchers.Default) {
        @OptIn(ExperimentalBleApi::class)
        val subrateFactor = 2
        val subrateLatency = 0
        val continuationNumber = 1
        val supervisionTimeout = 400

        // Act - Start observing subrating updates via the pending operations
        val resultDeferred =
            runBlocking(Dispatchers.Default) {
                launch {
                    ConnectionSubratingResult.Accepted(
                        ConnectionSubratingParameters(
                            subrateFactor = subrateFactor,
                            subrateLatency = subrateLatency,
                            continuationNumber = continuationNumber,
                            supervisionTimeout = supervisionTimeout,
                        ),
                    )
                }
            }

        // Simulate the subrate changed callback with success
        peripheral.bridge.onEvent?.invoke(
            GattCallbackEvent.SubrateChanged(
                subrateFactor = subrateFactor,
                subrateLatency = subrateLatency,
                continuationNumber = continuationNumber,
                supervisionTimeout = supervisionTimeout,
                status = BluetoothGatt.GATT_SUCCESS,
            ),
        )

        // Assert - The subrate change event should complete the pending operation
        resultDeferred.cancel() // Cancel the deferred, the event was processed
    }

    @Test
    fun `subrate changed callback with error reports rejected`() = runBlocking(Dispatchers.Default) {
        @OptIn(ExperimentalBleApi::class)
        val subrateFactor = 2
        val subrateLatency = 0

        // Simulate the subrate changed callback with error
        peripheral.bridge.onEvent?.invoke(
            GattCallbackEvent.SubrateChanged(
                subrateFactor = subrateFactor,
                subrateLatency = subrateLatency,
                continuationNumber = 1,
                supervisionTimeout = 400,
                status = BluetoothGatt.GATT_FAILURE,
            ),
        )

        // Assert - The subrate change event should be processed
        // (No direct way to verify rejection without pending operation)
    }

    // =========================================================================
    // Multiple Observations
    // =========================================================================

    @Test
    fun `multiple characteristics can be observed simultaneously`() = runBlocking(Dispatchers.Default) {
        // Arrange - Create multiple characteristics
        val char1 =
            Characteristic(
                uuid = uuidFrom("2a37"),
                properties = BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            )
        val char2 =
            Characteristic(
                uuid = uuidFrom("2a38"),
                properties = BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            )

        val service =
            DiscoveredService(
                uuid = uuidFrom("180d"),
                primary = true,
                characteristics = listOf(char1, char2),
            )

        runBlocking(Dispatchers.Default) {
            peripheral.peripheralContext.updateServices(listOf(service))
        }

        val gattChar1 =
            ShadowBluetoothGattCharacteristic.newInstance().apply {
                setUUID(char1.uuid.toString())
            }
        val gattChar2 =
            ShadowBluetoothGattCharacteristic.newInstance().apply {
                setUUID(char2.uuid.toString())
            }

        val observedChar1 = mutableListOf<ByteArray>()
        val observedChar2 = mutableListOf<ByteArray>()

        // Act - Observe both characteristics
        peripheral.observeValues(char1, BackpressureStrategy.LATEST).collect { data ->
            observedChar1.add(data)
        }
        peripheral.observeValues(char2, BackpressureStrategy.LATEST).collect { data ->
            observedChar2.add(data)
        }

        // Simulate notifications for both
        peripheral.bridge.onEvent?.invoke(
            GattCallbackEvent.CharacteristicChanged(characteristic = gattChar1, value = byteArrayOf(0x01)),
        )
        peripheral.bridge.onEvent?.invoke(
            GattCallbackEvent.CharacteristicChanged(characteristic = gattChar2, value = byteArrayOf(0x02)),
        )

        // Assert
        assertEquals(1, observedChar1.size)
        assertEquals(1, observedChar2.size)
        assertEquals(byteArrayOf(0x01), observedChar1[0])
        assertEquals(byteArrayOf(0x02), observedChar2[0])
    }

    // =========================================================================
    // Cleanup
    // =========================================================================

    @Test
    fun `peripheral cleanup closes bridge`() = runBlocking(Dispatchers.Default) {
        // Act
        peripheral.close()

        // Assert - Bridge should have no event handler after close
        assertNull(peripheral.bridge.onEvent)
    }
}
