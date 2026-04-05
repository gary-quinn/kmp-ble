package com.atruedev.kmpble.adapter

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Validates [AndroidBluetoothAdapter] on a real Android runtime.
 *
 * Tests framework integration (getSystemService, hasSystemFeature, BroadcastReceiver)
 * without requiring real BLE hardware.
 */
@RunWith(AndroidJUnit4::class)
class BluetoothAdapterStateTest {
    private lateinit var adapter: AndroidBluetoothAdapter
    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @After
    fun teardown() {
        if (::adapter.isInitialized) {
            adapter.close()
        }
    }

    @Test
    fun bluetoothManager_isAvailable() {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        assertNotNull(manager, "BluetoothManager should be available on the emulator")
    }

    @Test
    fun stateFlow_emitsInitialState() {
        adapter = AndroidBluetoothAdapter(context)
        val state = adapter.state.value
        assertNotNull(state, "Initial state should be emitted immediately")
    }

    @Test
    fun stateFlow_initialState_isValid() {
        adapter = AndroidBluetoothAdapter(context)
        val state = adapter.state.value
        val validStates =
            setOf(
                BluetoothAdapterState.On,
                BluetoothAdapterState.Off,
                BluetoothAdapterState.Unavailable,
                BluetoothAdapterState.Unsupported,
                BluetoothAdapterState.Unauthorized,
            )
        assertTrue(state in validStates, "State $state should be one of the known states")
    }

    @Test
    fun close_doesNotThrow() {
        adapter = AndroidBluetoothAdapter(context)
        // Access state to force lazy initialization of the stateIn flow
        adapter.state.value
        adapter.close()
    }

    @Test
    fun hasSystemFeature_bleLe_isAvailable() {
        assertTrue(
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE),
            "API 34 emulator should report BLE support",
        )
    }
}
