package com.atruedev.kmpble.peripheral

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Validates the BroadcastReceiver pattern used by [AndroidBondManager]
 * for monitoring bond state changes.
 *
 * Tests the receiver registration/unregistration lifecycle and intent
 * processing on a real Android runtime. Does NOT test with real Bluetooth
 * devices - validates the framework integration only.
 */
@RunWith(AndroidJUnit4::class)
class BondManagerBroadcastTest {
    private lateinit var context: Context
    private var receiver: BroadcastReceiver? = null

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @After
    fun teardown() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
                // Already unregistered
            }
        }
    }

    @Test
    fun broadcastReceiver_registersWithNotExportedFlag() {
        val latch = CountDownLatch(1)
        receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    ctx: Context,
                    intent: Intent,
                ) {
                    latch.countDown()
                }
            }

        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        // This should not throw - validates RECEIVER_NOT_EXPORTED works
        ContextCompat.registerReceiver(
            context,
            receiver!!,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    @Test
    fun broadcastReceiver_unregisters_cleanly() {
        receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    ctx: Context,
                    intent: Intent,
                ) {}
            }

        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        ContextCompat.registerReceiver(
            context,
            receiver!!,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        // Unregister should not throw
        context.unregisterReceiver(receiver!!)
        receiver = null
    }

    @Test
    fun multipleRegisterUnregisterCycles_doNotLeak() {
        for (i in 1..5) {
            val r =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        ctx: Context,
                        intent: Intent,
                    ) {}
                }

            val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            ContextCompat.registerReceiver(
                context,
                r,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            context.unregisterReceiver(r)
        }
    }

    @Test
    fun doubleUnregister_throwsIllegalArgument() {
        receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    ctx: Context,
                    intent: Intent,
                ) {}
            }

        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        ContextCompat.registerReceiver(
            context,
            receiver!!,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        context.unregisterReceiver(receiver!!)

        assertFailsWith<IllegalArgumentException> {
            context.unregisterReceiver(receiver!!)
        }
        receiver = null
    }

    @Test
    fun bondStateIntent_extraFields_areAccessible() {
        val intent =
            Intent(BluetoothDevice.ACTION_BOND_STATE_CHANGED).apply {
                putExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_BONDED)
                putExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_BONDING)
            }

        val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
        val previousState =
            intent.getIntExtra(
                BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                BluetoothDevice.BOND_NONE,
            )

        assertEquals(BluetoothDevice.BOND_BONDED, state)
        assertEquals(BluetoothDevice.BOND_BONDING, previousState)
    }

    @Test
    fun adapterStateReceiver_registersForActionStateChanged() {
        val latch = CountDownLatch(1)
        receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    ctx: Context,
                    intent: Intent,
                ) {
                    if (intent.action == android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED) {
                        latch.countDown()
                    }
                }
            }

        val filter = IntentFilter(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(
            context,
            receiver!!,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Registration succeeds - the broadcast itself requires real adapter state change
    }
}
