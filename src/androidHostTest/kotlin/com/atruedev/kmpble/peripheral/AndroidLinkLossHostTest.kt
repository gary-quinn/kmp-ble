package com.atruedev.kmpble.peripheral

import android.bluetooth.BluetoothProfile
import android.content.Context
import com.atruedev.kmpble.KmpBle
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.peripheral.state.ConnectionEvent
import com.atruedev.kmpble.peripheral.state.State
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowBluetoothDevice
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
class AndroidLinkLossHostTest {
    private lateinit var appContext: Context
    private lateinit var peripheral: AndroidPeripheral

    @Before
    fun setup() {
        appContext = RuntimeEnvironment.getApplication()
        KmpBle.init(appContext)
        peripheral = AndroidPeripheral(ShadowBluetoothDevice.newInstance("00:11:22:33:44:55"), appContext)
    }

    @After
    fun tearDown() {
        peripheral.close()
    }

    @Test
    fun linkLossWhileReadyEndsDisconnected() =
        runBlocking<Unit> {
            val context = peripheral.peripheralContext
            context.processEvent(ConnectionEvent.ConnectRequested)
            context.processEvent(ConnectionEvent.LinkEstablished)
            context.processEvent(ConnectionEvent.ServicesDiscovered)
            context.processEvent(ConnectionEvent.ConfigurationComplete)

            peripheral.handleGattEvent(
                GattCallbackEvent.ConnectionStateChanged(GATT_CONN_TIMEOUT, BluetoothProfile.STATE_DISCONNECTED),
            )
            // Stuck in Disconnecting.Error this times out: auto-reconnect waits for Disconnected.
            val settled = withTimeout(5.seconds) { peripheral.state.first { it is State.Disconnected } }

            assertIs<ConnectionLost>(assertIs<State.Disconnected.ByError>(settled).error)
        }

    private companion object {
        /** HCI connection timeout, reported as the status when a peripheral powers off. */
        const val GATT_CONN_TIMEOUT = 8
    }
}
