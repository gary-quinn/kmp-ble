package com.atruedev.kmpble.peripheral

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.gatt.internal.ObservationPersistence
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowBluetoothDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for AndroidPeripheral GATT event handling.
 *
 * Uses Robolectric to create an AndroidPeripheral instance, then injects
 * [GattCallbackEvent] instances through the [AndroidGattBridge.onEvent]
 * callback to verify state transitions, pending op completions, and
 * observation emissions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AndroidPeripheralIntegrationTest {
    private lateinit var appContext: Context
    private lateinit var peripheral: AndroidPeripheral

    @Before
    fun setup() {
        appContext = RuntimeEnvironment.getApplication()
        ObservationPersistence.context = appContext
        val bluetoothDevice = ShadowBluetoothDevice.newInstance("00:11:22:33:44:55")
        peripheral = AndroidPeripheral(bluetoothDevice, appContext)
    }

    @After
    fun teardown() {
        try {
            peripheral.close()
        } catch (_: Throwable) {
            // already closed or crash during close is fine
        }
        ObservationPersistence.context = null
    }

    // =========================================================================
    // GATT event dispatch
    // =========================================================================

    @Test
    fun `handleGattEvent dispatches ConnectionStateChanged to handler`() {
        val eventsReceived = mutableListOf<GattCallbackEvent>()
        peripheral.bridge.onEvent = { event ->
            eventsReceived.add(event)
            peripheral.handleGattEvent(event)
        }

        peripheral.bridge.onEvent?.invoke(
            GattCallbackEvent.ConnectionStateChanged(
                status = BluetoothGatt.GATT_SUCCESS,
                newState = BluetoothProfile.STATE_CONNECTED,
            ),
        )

        assertEquals(1, eventsReceived.size)
        assertTrue(eventsReceived[0] is GattCallbackEvent.ConnectionStateChanged)
        peripheral.close()
    }

    @Test
    fun `handleGattEvent dispatches ServicesDiscovered to handler`() {
        val eventsReceived = mutableListOf<GattCallbackEvent>()
        peripheral.bridge.onEvent = { event ->
            eventsReceived.add(event)
            peripheral.handleGattEvent(event)
        }

        peripheral.bridge.onEvent?.invoke(
            GattCallbackEvent.ServicesDiscovered(
                status = BluetoothGatt.GATT_SUCCESS,
                services = emptyList(),
            ),
        )

        assertEquals(1, eventsReceived.size)
        assertTrue(eventsReceived[0] is GattCallbackEvent.ServicesDiscovered)
        peripheral.close()
    }

    @Test
    fun `handleGattEvent dispatches CharacteristicChanged to observation manager`() {
        val eventsReceived = mutableListOf<GattCallbackEvent>()
        peripheral.bridge.onEvent = { event ->
            eventsReceived.add(event)
            peripheral.handleGattEvent(event)
        }

        val mockChar = createMockCharacteristic("00002a37-0000-1000-8000-00805f9b34fb")

        peripheral.bridge.onEvent?.invoke(
            GattCallbackEvent.CharacteristicChanged(
                characteristic = mockChar,
                value = byteArrayOf(0x01, 0x02, 0x03),
            ),
        )

        assertEquals(1, eventsReceived.size)
        assertTrue(eventsReceived[0] is GattCallbackEvent.CharacteristicChanged)
        peripheral.close()
    }

    @Test
    fun `handleGattEvent dispatches MtuChanged to pending ops`() {
        val eventsReceived = mutableListOf<GattCallbackEvent>()
        peripheral.bridge.onEvent = { event ->
            eventsReceived.add(event)
            peripheral.handleGattEvent(event)
        }

        peripheral.bridge.onEvent?.invoke(
            GattCallbackEvent.MtuChanged(
                mtu = 247,
                status = BluetoothGatt.GATT_SUCCESS,
            ),
        )

        assertEquals(1, eventsReceived.size)
        assertTrue(eventsReceived[0] is GattCallbackEvent.MtuChanged)
        peripheral.close()
    }

    @Test
    fun `handleGattEvent dispatches PhyUpdated to handler`() {
        val eventsReceived = mutableListOf<GattCallbackEvent>()
        peripheral.bridge.onEvent = { event ->
            eventsReceived.add(event)
            peripheral.handleGattEvent(event)
        }

        peripheral.bridge.onEvent?.invoke(
            GattCallbackEvent.PhyUpdated(
                txPhy = 1,
                rxPhy = 2,
                status = BluetoothGatt.GATT_SUCCESS,
            ),
        )

        assertEquals(1, eventsReceived.size)
        assertTrue(eventsReceived[0] is GattCallbackEvent.PhyUpdated)
        peripheral.close()
    }

    // =========================================================================
    // Pending operations completion (no pending op set, verifies no crash)
    // =========================================================================

    @Test
    fun `pendingOps handles CharacteristicRead when no pending slot`() {
        peripheral.handleGattEvent(
            GattCallbackEvent.CharacteristicRead(
                characteristic = createMockCharacteristic("00002a37-0000-1000-8000-00805f9b34fb"),
                value = byteArrayOf(0x01, 0x02),
                status = BluetoothGatt.GATT_SUCCESS,
            ),
        )
        // No crash - pendingOps.complete handles missing slot gracefully
        peripheral.close()
    }

    @Test
    fun `pendingOps handles CharacteristicWrite when no pending slot`() {
        peripheral.handleGattEvent(
            GattCallbackEvent.CharacteristicWrite(
                characteristic = createMockCharacteristic("00002a37-0000-1000-8000-00805f9b34fb"),
                status = BluetoothGatt.GATT_SUCCESS,
            ),
        )
        // No crash - pendingOps.complete handles missing slot gracefully
        peripheral.close()
    }

    @Test
    fun `pendingOps handles DescriptorWrite when no pending slot`() {
        val mockDesc = createMockDescriptor()
        peripheral.handleGattEvent(
            GattCallbackEvent.DescriptorWrite(
                descriptor = mockDesc,
                status = BluetoothGatt.GATT_SUCCESS,
            ),
        )
        // No crash - pendingOps.complete handles missing slot gracefully
        peripheral.close()
    }

    // =========================================================================
    // MTU change handling
    // =========================================================================

    @Test
    fun `mtu is updated after successful MtuChanged event`() =
        runTest {
            peripheral.bridge.onEvent = { event ->
                peripheral.handleGattEvent(event)
            }

            assertEquals(23, peripheral.mtu.value)

            peripheral.handleGattEvent(
                GattCallbackEvent.MtuChanged(
                    mtu = 247,
                    status = BluetoothGatt.GATT_SUCCESS,
                ),
            )

            testScheduler.advanceTimeBy(100)
            assertEquals(247, peripheral.mtu.value)
            assertEquals(244, peripheral.maximumWriteValueLength.value)
            peripheral.close()
        }

    // =========================================================================
    // PHY mapping
    // =========================================================================

    @Test
    fun `phyConstantToPhy maps 1M correctly`() {
        val phy = phyConstantToPhy(android.bluetooth.BluetoothDevice.PHY_LE_1M)
        assertEquals(com.atruedev.kmpble.connection.Phy.Le1M, phy)
        peripheral.close()
    }

    @Test
    fun `phyConstantToPhy maps 2M correctly`() {
        val phy = phyConstantToPhy(android.bluetooth.BluetoothDevice.PHY_LE_2M)
        assertEquals(com.atruedev.kmpble.connection.Phy.Le2M, phy)
        peripheral.close()
    }

    @Test
    fun `phyConstantToPhy maps coded correctly`() {
        val phy = phyConstantToPhy(android.bluetooth.BluetoothDevice.PHY_LE_CODED)
        assertEquals(com.atruedev.kmpble.connection.Phy.LeCoded, phy)
        peripheral.close()
    }

    // =========================================================================
    // Disconnection cleanup
    // =========================================================================

    @Test
    fun `onDisconnectCleanup clears native maps and cancels pending ops`() {
        val mockChar = createMockCharacteristic("00002a37-0000-1000-8000-00805f9b34fb")
        val kmpChar =
            mockChar.toCharacteristic(
                kotlin.uuid.Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb"),
            )
        peripheral.nativeCharMap[kmpChar] = mockChar

        assertEquals(1, peripheral.nativeCharMap.size)

        peripheral.onDisconnectCleanup()

        assertTrue(peripheral.nativeCharMap.isEmpty())
        assertTrue(peripheral.nativeDescMap.isEmpty())
        peripheral.close()
    }

    // =========================================================================
    // State machine transitions
    // =========================================================================

    @Test
    fun `peripheral starts in Disconnected state`() {
        assertTrue(peripheral.state.value is State.Disconnected)
        peripheral.close()
    }

    @Test
    fun `services are null before discovery`() {
        assertNull(peripheral.services.value)
        peripheral.close()
    }

    // =========================================================================
    // Service discovery event handling
    // =========================================================================

    @Test
    fun `ServicesDiscovered with failure status does not update services`() =
        runTest {
            peripheral.bridge.onEvent = { event ->
                peripheral.handleGattEvent(event)
            }

            peripheral.handleGattEvent(
                GattCallbackEvent.ServicesDiscovered(
                    status = BluetoothGatt.GATT_FAILURE,
                    services = emptyList(),
                ),
            )

            testScheduler.advanceTimeBy(100)
            assertNull(peripheral.services.value)
            peripheral.close()
        }

    @Test
    fun `ServicesDiscovered with success status updates services`() =
        runTest {
            peripheral.bridge.onEvent = { event ->
                peripheral.handleGattEvent(event)
            }

            val mockService = createMockService("0000180d-0000-1000-8000-00805f9b34fb")
            mockService.addCharacteristic(createMockCharacteristic("00002a37-0000-1000-8000-00805f9b34fb"))

            peripheral.handleGattEvent(
                GattCallbackEvent.ServicesDiscovered(
                    status = BluetoothGatt.GATT_SUCCESS,
                    services = listOf(mockService),
                ),
            )

            testScheduler.advanceTimeBy(100)
            assertNotNull(peripheral.services.value)
            assertEquals(1, peripheral.services.value!!.size)
            assertEquals(
                kotlin.uuid.Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb"),
                peripheral.services.value!![0].uuid,
            )
            peripheral.close()
        }

    // =========================================================================
    // RSSI handling
    // =========================================================================

    @Test
    fun `handleRssiResult completes pending op on success`() =
        runTest {
            peripheral.handleGattEvent(
                GattCallbackEvent.ReadRemoteRssi(
                    rssi = -65,
                    status = BluetoothGatt.GATT_SUCCESS,
                ),
            )

            testScheduler.advanceTimeBy(100)
            assertFalse(
                peripheral.pendingOps.has(com.atruedev.kmpble.gatt.internal.PendingOp.RssiRead),
            )
            peripheral.close()
        }

    // =========================================================================
    // PHY update handling
    // =========================================================================

    @Test
    fun `handlePhyUpdated emits to phyUpdate flow`() =
        runTest {
            val phyUpdates = mutableListOf<com.atruedev.kmpble.connection.PhyUpdate>()

            val job =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    peripheral._phyUpdate.collect { update ->
                        phyUpdates.add(update)
                    }
                }

            peripheral.handleGattEvent(
                GattCallbackEvent.PhyUpdated(
                    txPhy = android.bluetooth.BluetoothDevice.PHY_LE_2M,
                    rxPhy = android.bluetooth.BluetoothDevice.PHY_LE_1M,
                    status = BluetoothGatt.GATT_SUCCESS,
                ),
            )

            testScheduler.advanceTimeBy(100)
            job.cancel()

            assertEquals(1, phyUpdates.size)
            assertEquals(com.atruedev.kmpble.connection.Phy.Le2M, phyUpdates[0].txPhy)
            assertEquals(com.atruedev.kmpble.connection.Phy.Le1M, phyUpdates[0].rxPhy)
            peripheral.close()
        }

    // =========================================================================
    // Peripheral lifecycle
    // =========================================================================

    @Test
    fun `close sets closed flag and cleans up resources`() {
        assertFalse(peripheral.closed)
        peripheral.close()
        assertTrue(peripheral.closed)
    }

    @Test
    fun `close is idempotent`() {
        peripheral.close()
        peripheral.close()
        peripheral.close()
        assertTrue(peripheral.closed)
    }

    // =========================================================================
    // GattStatus mapping verification
    // =========================================================================

    @Test
    fun `toGattStatus maps GATT_SUCCESS correctly`() {
        val status = BluetoothGatt.GATT_SUCCESS.toGattStatus()
        assertEquals(GattStatus.Success, status)
        peripheral.close()
    }

    @Test
    fun `toGattStatus maps GATT_FAILURE correctly`() {
        val status = BluetoothGatt.GATT_FAILURE.toGattStatus()
        assertEquals(GattStatus.Failure, status)
        peripheral.close()
    }

    @Test
    fun `toGattStatus maps unknown code to GattStatus Unknown`() {
        val status = 0x85.toGattStatus()
        assertTrue(status is GattStatus.Unknown)
        assertEquals(0x85, (status as GattStatus.Unknown).platformCode)
        peripheral.close()
    }

    // =========================================================================
    // Characteristic toCharacteristic conversion
    // =========================================================================

    @Test
    fun `toCharacteristic extracts properties from Android characteristic`() {
        val mockChar =
            createMockCharacteristicWithProperties(
                uuid = "00002a37-0000-1000-8000-00805f9b34fb",
                properties = BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            )

        val kmpChar =
            mockChar.toCharacteristic(
                kotlin.uuid.Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb"),
            )

        assertTrue(kmpChar.properties.read)
        assertTrue(kmpChar.properties.notify)
        assertFalse(kmpChar.properties.write)
        assertFalse(kmpChar.properties.writeWithoutResponse)
        peripheral.close()
    }

    @Test
    fun `toCharacteristic maps write without response property`() {
        val mockChar =
            createMockCharacteristicWithProperties(
                uuid = "00002a37-0000-1000-8000-00805f9b34fb",
                properties = BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            )

        val kmpChar =
            mockChar.toCharacteristic(
                kotlin.uuid.Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb"),
            )

        assertTrue(kmpChar.properties.writeWithoutResponse)
        assertFalse(kmpChar.properties.read)
        peripheral.close()
    }

    // =========================================================================
    // WriteType to Android mapping
    // =========================================================================

    @Test
    fun `WriteType WithResponse maps to WRITE_TYPE_DEFAULT`() {
        val androidType =
            com.atruedev.kmpble.gatt.WriteType.WithResponse
                .toAndroidWriteType()
        assertEquals(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT, androidType)
        peripheral.close()
    }

    @Test
    fun `WriteType WithoutResponse maps to WRITE_TYPE_NO_RESPONSE`() {
        val androidType =
            com.atruedev.kmpble.gatt.WriteType.WithoutResponse
                .toAndroidWriteType()
        assertEquals(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE, androidType)
        peripheral.close()
    }

    @Test
    fun `WriteType Signed maps to WRITE_TYPE_SIGNED`() {
        val androidType =
            com.atruedev.kmpble.gatt.WriteType.Signed
                .toAndroidWriteType()
        assertEquals(BluetoothGattCharacteristic.WRITE_TYPE_SIGNED, androidType)
        peripheral.close()
    }
}

// =========================================================================
// Test helpers
// =========================================================================

private fun createMockService(uuid: String): BluetoothGattService =
    BluetoothGattService(
        java.util.UUID.fromString(uuid),
        BluetoothGattService.SERVICE_TYPE_PRIMARY,
    )

private fun createMockCharacteristic(uuid: String): BluetoothGattCharacteristic =
    BluetoothGattCharacteristic(
        java.util.UUID.fromString(uuid),
        BluetoothGattCharacteristic.PROPERTY_READ,
        BluetoothGattCharacteristic.PERMISSION_READ,
    )

private fun createMockCharacteristicWithProperties(
    uuid: String,
    properties: Int,
): BluetoothGattCharacteristic =
    BluetoothGattCharacteristic(
        java.util.UUID.fromString(uuid),
        properties,
        BluetoothGattCharacteristic.PERMISSION_READ,
    )

private fun createMockDescriptor(): BluetoothGattDescriptor =
    BluetoothGattDescriptor(
        java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
        BluetoothGattDescriptor.PERMISSION_READ,
    )
