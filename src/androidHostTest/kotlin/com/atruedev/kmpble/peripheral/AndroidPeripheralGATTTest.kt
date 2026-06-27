@file:Suppress("TestFunctionName")

package com.atruedev.kmpble.peripheral

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.quirks.QuirkRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.firstOrNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowBluetoothDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * AndroidPeripheral GATT event handling tests.
 *
 * Covers: connection state changes, service discovery, characteristic read/write,
 * notification observations, MTU negotiation, RSSI reading, descriptor operations.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidPeripheralGATTTest {

    @Test
    fun `test connection state changes`() = runTest {
        val appContext = RuntimeEnvironment.getApplication()
        val device = ShadowBluetoothDevice.newInstance("00:11:22:33:44:55")
        val peripheral = AndroidPeripheral(device, appContext, QuirkRegistry.getInstance())

        assertEquals(State.Disconnected, peripheral.state.value)

        peripheral.connect()
        assertEquals(State.Connected, peripheral.state.value)

        peripheral.disconnect()
        assertEquals(State.Disconnected, peripheral.state.value)

        peripheral.close()
    }

    @Test
    fun `test service discovery`() = runTest {
        val appContext = RuntimeEnvironment.getApplication()
        val device = ShadowBluetoothDevice.newInstance("00:11:22:33:44:55")
        val peripheral = AndroidPeripheral(device, appContext, QuirkRegistry.getInstance())

        peripheral.connect()
        assertNotNull(peripheral.services.value)

        val services = peripheral.refreshServices()
        assertNotNull(services)
        assertEquals(1, services.size)

        peripheral.close()
    }

    @Test
    fun `test characteristic find`() = runTest {
        val appContext = RuntimeEnvironment.getApplication()
        val device = ShadowBluetoothDevice.newInstance("00:11:22:33:44:55")
        val peripheral = AndroidPeripheral(device, appContext, QuirkRegistry.getInstance())

        peripheral.connect()
        peripheral.refreshServices()

        val services = peripheral.services.value!!
        val service = services.first()

        val char = peripheral.findCharacteristic(service.uuid, service.characteristics.first().uuid)
        assertNotNull(char)

        peripheral.close()
    }

    @Test
    fun `test characteristic read`() = runTest {
        val appContext = RuntimeEnvironment.getApplication()
        val device = ShadowBluetoothDevice.newInstance("00:11:22:33:44:55")
        val peripheral = AndroidPeripheral(device, appContext, QuirkRegistry.getInstance())

        peripheral.connect()
        peripheral.refreshServices()

        val services = peripheral.services.value!!
        val service = services.first()
        val char = service.characteristics.first()

        val data = peripheral.read(char)
        assertNotNull(data)
        assertTrue(data.isNotEmpty())

        peripheral.close()
    }

    @Test
    fun `test characteristic write`() = runTest {
        val appContext = RuntimeEnvironment.getApplication()
        val device = ShadowBluetoothDevice.newInstance("00:11:22:33:44:55")
        val peripheral = AndroidPeripheral(device, appContext, QuirkRegistry.getInstance())

        peripheral.connect()
        peripheral.refreshServices()

        val services = peripheral.services.value!!
        val service = services.first()
        val char = service.characteristics.first()

        val data = byteArrayOf(0x01, 0x02, 0x03)
        peripheral.write(char, data, com.atruedev.kmpble.gatt.WriteType.WriteWithResponse)

        peripheral.close()
    }

    @Test
    fun `test observe notifications`() = runTest {
        val appContext = RuntimeEnvironment.getApplication()
        val device = ShadowBluetoothDevice.newInstance("00:11:22:33:44:55")
        val peripheral = AndroidPeripheral(device, appContext, QuirkRegistry.getInstance())

        peripheral.connect()
        peripheral.refreshServices()

        val services = peripheral.services.value!!
        val service = services.first()
        val char = service.characteristics.first()

        val observationFlow = peripheral.observe(char)
        val observation = observationFlow.firstOrNull()

        assertNotNull(observation)

        peripheral.close()
    }

    @Test
    fun `test MTU negotiation`() = runTest {
        val appContext = RuntimeEnvironment.getApplication()
        val device = ShadowBluetoothDevice.newInstance("00:11:22:33:44:55")
        val peripheral = AndroidPeripheral(device, appContext, QuirkRegistry.getInstance())

        peripheral.connect()
        peripheral.refreshServices()

        val newMtu = 512
        val negotiatedMtu = peripheral.requestMtu(newMtu)
        assertEquals(newMtu, negotiatedMtu)

        peripheral.close()
    }

    @Test
    fun `test RSSI read`() = runTest {
        val appContext = RuntimeEnvironment.getApplication()
        val device = ShadowBluetoothDevice.newInstance("00:11:22:33:44:55")
        val peripheral = AndroidPeripheral(device, appContext, QuirkRegistry.getInstance())

        peripheral.connect()
        peripheral.refreshServices()

        val rssi = peripheral.readRssi()
        assertTrue(rssi <= 0)

        peripheral.close()
    }

    @Test
    fun `test descriptor read`() = runTest {
        val appContext = RuntimeEnvironment.getApplication()
        val device = ShadowBluetoothDevice.newInstance("00:11:22:33:44:55")
        val peripheral = AndroidPeripheral(device, appContext, QuirkRegistry.getInstance())

        peripheral.connect()
        peripheral.refreshServices()

        val services = peripheral.services.value!!
        val service = services.first()
        val char = service.characteristics.first()

        val descriptor = char.descriptors.firstOrNull()
        assertNotNull(descriptor)

        val data = peripheral.readDescriptor(descriptor!!)
        assertNotNull(data)

        peripheral.close()
    }

    @Test
    fun `test descriptor write`() = runTest {
        val appContext = RuntimeEnvironment.getApplication()
        val device = ShadowBluetoothDevice.newInstance("00:11:22:33:44:55")
        val peripheral = AndroidPeripheral(device, appContext, QuirkRegistry.getInstance())

        peripheral.connect()
        peripheral.refreshServices()

        val services = peripheral.services.value!!
        val service = services.first()
        val char = service.characteristics.first()

        val descriptor = char.descriptors.firstOrNull()
        assertNotNull(descriptor)

        val data = byteArrayOf(0x01, 0x02)
        peripheral.writeDescriptor(descriptor!!, data)

        peripheral.close()
    }
}
