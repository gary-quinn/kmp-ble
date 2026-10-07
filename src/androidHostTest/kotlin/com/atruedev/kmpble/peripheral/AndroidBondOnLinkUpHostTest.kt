package com.atruedev.kmpble.peripheral

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.os.Looper
import com.atruedev.kmpble.KmpBle
import com.atruedev.kmpble.connection.BondingPreference
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.OperationTimeouts
import com.atruedev.kmpble.error.ConnectionFailed
import com.atruedev.kmpble.error.ConnectionFailureReason
import com.atruedev.kmpble.error.OperationFailed
import com.atruedev.kmpble.peripheral.state.ConnectionEvent
import com.atruedev.kmpble.peripheral.state.State
import com.atruedev.kmpble.quirks.BleQuirks
import com.atruedev.kmpble.quirks.QuirkRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * How a freshly established link moves through bonding. BondRequired is only accepted in
 * Connecting.Transport, so it has to replace LinkEstablished rather than follow it.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidBondOnLinkUpHostTest {
    private lateinit var appContext: Context
    private lateinit var device: BluetoothDevice
    private lateinit var peripheral: AndroidPeripheral

    @Before
    fun setup() {
        appContext = RuntimeEnvironment.getApplication()
        KmpBle.init(appContext)
        device = ShadowBluetoothDevice.newInstance("00:11:22:33:44:55")
        // Two attempts, so a retry after a failed bond would show up as a hung connect().
        val quirks =
            QuirkRegistry(
                mapOf(BleQuirks.GattRetryCount to 2, BleQuirks.GattRetryDelay to 10.milliseconds),
                "test",
            )
        peripheral = AndroidPeripheral(device, appContext, quirks, null)
    }

    @After
    fun tearDown() {
        peripheral.close()
    }

    @Test
    fun rejectedRequiredBondFailsTheConnectWithBondingFailed() =
        runBlocking<Unit> {
            shadowOf(device).setCreatedBond(false)
            val connect = async(Dispatchers.Default) { peripheral.connect(options(BondingPreference.Required)) }
            withTimeout(5.seconds) { peripheral.state.first { it is State.Connecting.Transport } }

            peripheral.handleGattEvent(
                GattCallbackEvent.ConnectionStateChanged(BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED),
            )
            withTimeout(5.seconds) { connect.await() }

            val error = assertIs<State.Disconnected.ByError>(peripheral.state.value).error
            assertEquals(ConnectionFailureReason.BONDING_FAILED, assertIs<ConnectionFailed>(error).failureReason)
        }

    @Test
    fun requiredBondPassesThroughAuthenticatingBeforeDiscovery() =
        runBlocking<Unit> {
            shadowOf(device).setCreatedBond(true)
            val seen = mutableListOf<State>()
            val recorder = launch(Dispatchers.Unconfined) { peripheral.state.toList(seen) }
            startLinkUp(BondingPreference.Required)

            withTimeout(5.seconds) { peripheral.state.first { it is State.Connecting.Authenticating } }
            broadcastBonded()
            // No GATT handle in this test, so discovery cannot start: failing discovery
            // (only accepted in Connecting.Discovering) proves the bond moved the state on.
            val settled = withTimeout(5.seconds) { peripheral.state.first { it is State.Disconnected } }
            recorder.cancel()

            assertIs<OperationFailed>(assertIs<State.Disconnected.ByError>(settled).error)
            assertTrue(seen.indexOf(State.Connecting.Authenticating) < seen.indexOf(State.Connecting.Discovering))
        }

    @Test
    fun ifRequiredGoesStraightToDiscoveryWithoutAuthenticating() =
        runBlocking<Unit> {
            val seen = mutableListOf<State>()
            val recorder = launch(Dispatchers.Unconfined) { peripheral.state.toList(seen) }
            startLinkUp(BondingPreference.IfRequired)

            val settled = withTimeout(5.seconds) { peripheral.state.first { it is State.Disconnected } }
            recorder.cancel()

            assertIs<OperationFailed>(assertIs<State.Disconnected.ByError>(settled).error)
            assertTrue(State.Connecting.Discovering in seen)
            assertFalse(State.Connecting.Authenticating in seen)
        }

    private fun options(bonding: BondingPreference): ConnectionOptions =
        ConnectionOptions(timeouts = OperationTimeouts(connect = 30.seconds), bondingPreference = bonding)

    /** Drives the link-up callback directly, without a connect() in flight. */
    private suspend fun startLinkUp(bonding: BondingPreference) {
        peripheral.bondManager.start()
        peripheral.currentConnectionOptions = options(bonding)
        peripheral.peripheralContext.processEvent(ConnectionEvent.ConnectRequested)
        peripheral.handleGattEvent(
            GattCallbackEvent.ConnectionStateChanged(BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED),
        )
    }

    private fun broadcastBonded() {
        shadowOf(device).setBondState(BluetoothDevice.BOND_BONDED)
        appContext.sendBroadcast(
            Intent(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                .setPackage(appContext.packageName)
                .putExtra(BluetoothDevice.EXTRA_DEVICE, device)
                .putExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_BONDED)
                .putExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_BONDING),
        )
        shadowOf(Looper.getMainLooper()).idle()
    }
}
