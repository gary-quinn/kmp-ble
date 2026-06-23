package com.atruedev.kmpble.peripheral

import android.content.Context
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowBluetoothDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Smoke test that verifies AndroidPeripheral exposes [Peripheral.dataLengthParameters]
 * through the same flow instance as its internal PeripheralContext.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidPeripheralDataLengthSmokeTest {
    private lateinit var appContext: Context
    private lateinit var shadowDevice: ShadowBluetoothDevice

    @Before
    fun setup() {
        appContext = RuntimeEnvironment.getApplication()
        shadowDevice = ShadowBluetoothDevice.newInstance("00:11:22:33:44:55")
    }

    @Test
    fun androidPeripheral_exposes_dataLengthParameters_as_StateFlow() {
        val peripheral = AndroidPeripheral(shadowDevice.device, appContext)
        val flow = peripheral.dataLengthParameters

        assertNotNull(flow, "AndroidPeripheral.dataLengthParameters must not be null")
        assertEquals(null, flow.value, "Default data length parameters should be null")
        peripheral.close()
    }

    @Test
    fun androidPeripheral_dataLengthParameters_backed_by_PeripheralContext() {
        val peripheral = AndroidPeripheral(shadowDevice.device, appContext)
        val contextFlow = peripheral.peripheralContext.dataLengthParameters
        val peripheralFlow = peripheral.dataLengthParameters

        assertTrue(
            contextFlow === peripheralFlow,
            "AndroidPeripheral.dataLengthParameters must be the same flow instance as PeripheralContext.dataLengthParameters",
        )
        peripheral.close()
    }
}
