package com.atruedev.kmpble.peripheral

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.Context
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.KmpBle
import com.atruedev.kmpble.gatt.internal.ObservationPersistence
import com.atruedev.kmpble.peripheral.internal.PeripheralRegistry
import com.atruedev.kmpble.scanner.Advertisement
import kotlinx.coroutines.isActive
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowBluetoothDevice
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AndroidPeripheralCloseHostTest {
    private lateinit var appContext: Context
    private lateinit var device: BluetoothDevice

    @Before
    fun setup() {
        appContext = RuntimeEnvironment.getApplication()
        KmpBle.init(appContext)
        device = ShadowBluetoothDevice.newInstance("00:11:22:33:44:55")
        PeripheralRegistry.clear()
    }

    @After
    fun tearDown() {
        ObservationPersistence.context.value = appContext
        PeripheralRegistry.clear()
    }

    @Test
    fun close_unregisters_even_when_a_teardown_step_throws() {
        val first = assertIs<AndroidPeripheral>(advertisement().toPeripheral())
        // Without a context ObservationPersistence.clear() throws, as it did before KmpBle.init() wired it.
        ObservationPersistence.context.value = null

        assertFailsWith<IllegalStateException> { first.close() }
        assertTrue(first.closed)
        assertFalse(device.address in PeripheralRegistry.identifiers())
        assertFalse(first.peripheralContext.scope.isActive, "steps after the failing one must still run")

        ObservationPersistence.context.value = appContext
        val second = assertIs<AndroidPeripheral>(advertisement().toPeripheral())
        assertNotSame(first, second)
        assertFalse(second.closed)
        second.close()
    }

    private fun advertisement(): Advertisement =
        Advertisement(
            identifier = Identifier(device.address),
            name = null,
            rssi = RSSI,
            txPower = null,
            isConnectable = true,
            serviceUuids = emptyList(),
            manufacturerData = emptyMap(),
            serviceData = emptyMap(),
            timestampNanos = 0L,
        ).apply {
            platformContext =
                ScanResult(
                    device,
                    0,
                    BluetoothDevice.PHY_LE_1M,
                    ScanResult.PHY_UNUSED,
                    ScanResult.SID_NOT_PRESENT,
                    ScanResult.TX_POWER_NOT_PRESENT,
                    RSSI,
                    ScanResult.PERIODIC_INTERVAL_NOT_PRESENT,
                    null,
                    0L,
                )
        }

    private companion object {
        const val RSSI = -50
    }
}
