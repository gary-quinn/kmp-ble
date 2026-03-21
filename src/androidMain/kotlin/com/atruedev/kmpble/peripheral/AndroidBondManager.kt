@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.peripheral

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.bonding.BondRemovalResult
import com.atruedev.kmpble.bonding.BondState
import com.atruedev.kmpble.connection.internal.ConnectionEvent
import com.atruedev.kmpble.peripheral.internal.PeripheralContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Tracks bond state for a single [BluetoothDevice] via broadcast receiver.
 *
 * All mutable state is confined to [peripheralContext.scope] which uses
 * `limitedParallelism(1)` — no synchronization primitives needed.
 */
internal class AndroidBondManager(
    private val device: BluetoothDevice,
    private val context: Context,
    private val peripheralContext: PeripheralContext,
) {
    private var receiver: BroadcastReceiver? = null
    private var bondComplete: CompletableDeferred<Boolean>? = null

    internal val bondState: StateFlow<BondState> get() = peripheralContext.bondState

    internal fun start() {
        updateFromDevice()
        registerReceiver()
    }

    internal fun stop() {
        unregisterReceiver()
        bondComplete?.cancel()
        bondComplete = null
    }

    internal suspend fun createBond(): Boolean {
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            peripheralContext.updateBondState(BondState.Bonded)
            return true
        }

        bondComplete = CompletableDeferred()
        val initiated = device.createBond()
        if (!initiated) {
            bondComplete = null
            return false
        }

        return try {
            bondComplete!!.await()
        } finally {
            bondComplete = null
        }
    }

    @ExperimentalBleApi
    internal fun removeBond(): BondRemovalResult {
        return try {
            val method = device.javaClass.getMethod("removeBond")
            val result = method.invoke(device) as? Boolean ?: false
            if (result) BondRemovalResult.Success
            else BondRemovalResult.Failed("removeBond() returned false")
        } catch (e: NoSuchMethodException) {
            BondRemovalResult.NotSupported(
                "removeBond() not available on this device. Remove bond from system Bluetooth settings."
            )
        } catch (e: Exception) {
            BondRemovalResult.Failed(e.message ?: "Unknown error")
        }
    }

    private fun updateFromDevice() {
        val state = when (device.bondState) {
            BluetoothDevice.BOND_BONDED -> BondState.Bonded
            BluetoothDevice.BOND_BONDING -> BondState.Bonding
            BluetoothDevice.BOND_NONE -> BondState.NotBonded
            else -> BondState.Unknown
        }
        peripheralContext.scope.launch {
            peripheralContext.updateBondState(state)
        }
    }

    private fun registerReceiver() {
        if (receiver != null) return

        val bondReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return

                val bondDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                if (bondDevice?.address != device.address) return

                val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                val previousState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)

                peripheralContext.scope.launch {
                    when (state) {
                        BluetoothDevice.BOND_BONDED -> {
                            peripheralContext.updateBondState(BondState.Bonded)
                            peripheralContext.processEvent(ConnectionEvent.BondSucceeded)
                            bondComplete?.complete(true)
                        }
                        BluetoothDevice.BOND_BONDING -> {
                            peripheralContext.updateBondState(BondState.Bonding)
                        }
                        BluetoothDevice.BOND_NONE -> {
                            peripheralContext.updateBondState(BondState.NotBonded)
                            if (previousState == BluetoothDevice.BOND_BONDING) {
                                peripheralContext.processEvent(
                                    ConnectionEvent.BondFailed(
                                        com.atruedev.kmpble.error.ConnectionFailed(reason = "Bonding failed")
                                    )
                                )
                                bondComplete?.complete(false)
                            } else {
                                peripheralContext.processEvent(ConnectionEvent.BondStateChanged)
                            }
                        }
                    }
                }
            }
        }

        receiver = bondReceiver
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        androidx.core.content.ContextCompat.registerReceiver(
            context, bondReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun unregisterReceiver() {
        receiver?.let {
            context.unregisterReceiver(it)
            receiver = null
        }
    }
}
