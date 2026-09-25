package com.atruedev.kmpble.backend

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.adapter.BluetoothAdapter
import com.atruedev.kmpble.adapter.BluetoothAdapterState
import com.atruedev.kmpble.backend.internal.BackendScanner
import com.atruedev.kmpble.l2cap.L2capException
import com.atruedev.kmpble.l2cap.L2capListener
import com.atruedev.kmpble.peripheral.internal.PeripheralRegistry
import com.atruedev.kmpble.peripheral.toPeripheral
import com.atruedev.kmpble.permissions.PermissionResult
import com.atruedev.kmpble.permissions.checkBlePermissions
import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.scanner.Scanner
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(KmpBleBackendApi::class)
class BleBackendsTest {
    @AfterTest
    fun tearDown() {
        BleBackends.install(null)
        System.clearProperty(BleBackends.BACKEND_PROPERTY)
        PeripheralRegistry.clear()
    }

    @Test
    fun noBackendOnClasspathMeansUnsupported() {
        assertNull(BleBackends.current())
        assertTrue(BleBackends.available().isEmpty())
        val error = assertFailsWith<UnsupportedOperationException> { Scanner {} }
        assertTrue(error.message!!.contains("kmp-ble-bluez"))
        assertIs<PermissionResult.Denied>(checkBlePermissions())
        assertFailsWith<L2capException.NotSupported> { L2capListener() }
        assertFailsWith<UnsupportedOperationException> { advertisement().toPeripheral() }
    }

    @Test
    fun installedBackendDrivesPortableFactories() {
        val backend = FakeBackend().apply { permissions = PermissionResult.PermanentlyDenied(listOf("bluetooth")) }
        BleBackends.install(backend)

        assertSame(backend, BleBackends.current())
        assertIs<BackendScanner>(Scanner {})
        assertEquals(PermissionResult.PermanentlyDenied(listOf("bluetooth")), checkBlePermissions())
        val adapter = BluetoothAdapter()
        assertEquals(BluetoothAdapterState.On, adapter.state.value)
        assertTrue(adapter.capabilities.supportsExtendedAdvertising)
        assertEquals(listOf(Identifier("AA:BB:CC:DD:EE:FF")), adapter.getBondedDevices())
        adapter.close()
        assertTrue(backend.adapter.closed)
    }

    @Test
    fun advertisementWithoutBackendContextUsesCurrentBackend() {
        val backend = FakeBackend()
        BleBackends.install(backend)

        val peripheral = advertisement().toPeripheral()
        assertEquals(listOf(Identifier("AA:BB:CC:DD:EE:FF") to null), backend.createdPeripherals.toList())
        peripheral.close()
    }

    @Test
    fun unsupportedBackendIsNotSelected() {
        val backend = FakeBackend(supported = false)
        BleBackends.install(null)
        assertNull(listOf(backend).firstOrNull { it.isSupported() })
        assertTrue(BleBackends.unavailableMessage("Scanner").startsWith("Scanner is not supported on this JVM target"))
    }

    @Test
    fun requestedBackendPropertyNamesMissingBackend() {
        System.setProperty(BleBackends.BACKEND_PROPERTY, "windows")
        assertNull(BleBackends.current())
        val error = assertFailsWith<UnsupportedOperationException> { Scanner {} }
        assertTrue(error.message!!.contains("backend 'windows'"))
    }

    private fun advertisement(): Advertisement =
        Advertisement(
            identifier = Identifier("AA:BB:CC:DD:EE:FF"),
            name = null,
            rssi = -60,
            txPower = null,
            isConnectable = true,
            serviceUuids = emptyList(),
            manufacturerData = emptyMap(),
            serviceData = emptyMap(),
            timestampNanos = 0,
        )
}
