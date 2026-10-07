package com.atruedev.kmpble.peripheral

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Looper
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.bonding.BondState
import com.atruedev.kmpble.peripheral.internal.PeripheralContext
import com.atruedev.kmpble.peripheral.state.ConnectionEvent
import com.atruedev.kmpble.peripheral.state.State
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowBluetoothDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
class AndroidBondManagerHostTest {
    private lateinit var appContext: Context
    private lateinit var device: BluetoothDevice
    private lateinit var peripheralContext: PeripheralContext
    private lateinit var bondManager: AndroidBondManager

    @Before
    fun setup() {
        appContext = RuntimeEnvironment.getApplication()
        device = ShadowBluetoothDevice.newInstance("00:11:22:33:44:55")
        peripheralContext = PeripheralContext(Identifier(device.address))
        bondManager = AndroidBondManager(device, appContext, peripheralContext)
    }

    @After
    fun tearDown() {
        bondManager.stop()
        peripheralContext.close()
    }

    @Test
    fun removedBondWhileReadyReturnsToReady() =
        runBlocking<Unit> {
            connectToReady()
            startBonded()

            removeBond()
            // The receiver handles the change in one coroutine on the peripheral's serial
            // dispatcher; running a block there after it means that coroutine has finished.
            withContext(peripheralContext.dispatcher) {}

            assertIs<State.Connected.Ready>(peripheralContext.state.value)
        }

    @Test
    fun removedBondWhileDisconnectedLeavesStateAlone() =
        runBlocking<Unit> {
            startBonded()

            removeBond()
            withContext(peripheralContext.dispatcher) {}

            assertEquals(State.Disconnected.ByRequest, peripheralContext.state.value)
        }

    private suspend fun connectToReady() {
        peripheralContext.processEvent(ConnectionEvent.ConnectRequested)
        peripheralContext.processEvent(ConnectionEvent.LinkEstablished)
        peripheralContext.processEvent(ConnectionEvent.ServicesDiscovered)
        peripheralContext.processEvent(ConnectionEvent.ConfigurationComplete)
        assertIs<State.Connected.Ready>(peripheralContext.state.value)
    }

    private suspend fun startBonded() {
        shadowOf(device).setBondState(BluetoothDevice.BOND_BONDED)
        bondManager.start()
        withTimeout(5.seconds) { bondManager.bondState.first { it == BondState.Bonded } }
    }

    /** The system broadcast for a bond that was removed, e.g. forgotten in the system settings. */
    private suspend fun removeBond() {
        shadowOf(device).setBondState(BluetoothDevice.BOND_NONE)
        appContext.sendBroadcast(
            Intent(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                .setPackage(appContext.packageName)
                .putExtra(BluetoothDevice.EXTRA_DEVICE, device)
                .putExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                .putExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_BONDED),
        )
        shadowOf(Looper.getMainLooper()).idle()
        withTimeout(5.seconds) { bondManager.bondState.first { it == BondState.NotBonded } }
    }
}
