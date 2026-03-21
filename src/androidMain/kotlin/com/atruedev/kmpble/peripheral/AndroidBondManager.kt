@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.peripheral

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.bonding.BondRemovalResult
import com.atruedev.kmpble.bonding.BondState
import com.atruedev.kmpble.bonding.PairingEvent
import com.atruedev.kmpble.bonding.PairingHandler
import com.atruedev.kmpble.bonding.PairingResponse
import com.atruedev.kmpble.connection.internal.ConnectionEvent
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import com.atruedev.kmpble.peripheral.internal.PeripheralContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal class AndroidBondManager(
    private val device: BluetoothDevice,
    private val context: Context,
    private val peripheralContext: PeripheralContext,
) {
    private var bondReceiver: BroadcastReceiver? = null
    private var pairingReceiver: BroadcastReceiver? = null
    private var bondComplete: CompletableDeferred<Boolean>? = null
    internal var pairingHandler: PairingHandler? = null

    internal val bondState: StateFlow<BondState> get() = peripheralContext.bondState

    internal fun start() {
        updateFromDevice()
        registerBondReceiver()
        registerPairingReceiver()
    }

    internal fun stop() {
        unregisterBondReceiver()
        unregisterPairingReceiver()
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

    private fun registerBondReceiver() {
        if (bondReceiver != null) return

        val receiver = object : BroadcastReceiver() {
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

        bondReceiver = receiver
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        androidx.core.content.ContextCompat.registerReceiver(
            context, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    @OptIn(ExperimentalBleApi::class)
    private fun registerPairingReceiver() {
        if (pairingReceiver != null) return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_PAIRING_REQUEST) return

                val pairingDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                if (pairingDevice?.address != device.address) return

                val handler = pairingHandler ?: return
                val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1)
                val key = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, -1)
                val identifier = Identifier(device.address)

                val event = when (variant) {
                    BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION ->
                        PairingEvent.NumericComparison(identifier, key)
                    BluetoothDevice.PAIRING_VARIANT_PIN ->
                        PairingEvent.PasskeyRequest(identifier)
                    PAIRING_VARIANT_OOB_CONSENT ->
                        PairingEvent.OutOfBandDataRequest(identifier)
                    PAIRING_VARIANT_CONSENT ->
                        PairingEvent.JustWorksConfirmation(identifier)
                    PAIRING_VARIANT_DISPLAY_PASSKEY ->
                        PairingEvent.PasskeyNotification(identifier, key)
                    else -> {
                        logEvent(BleLogEvent.Error(identifier, "Unknown pairing variant: $variant", null))
                        return
                    }
                }

                peripheralContext.scope.launch {
                    try {
                        val response = handler.onPairingEvent(event)
                        applyPairingResponse(pairingDevice, variant, response)
                    } catch (e: Exception) {
                        logEvent(BleLogEvent.Error(identifier, "Pairing handler threw: ${e.message}", e))
                    }
                }

                abortBroadcast()
            }
        }

        pairingReceiver = receiver
        val filter = IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST).apply {
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    @OptIn(ExperimentalBleApi::class)
    private fun applyPairingResponse(
        pairingDevice: BluetoothDevice,
        variant: Int,
        response: PairingResponse,
    ) {
        when (response) {
            is PairingResponse.Confirm -> {
                if (response.accepted) {
                    pairingDevice.setPairingConfirmation(true)
                } else {
                    pairingDevice.setPairingConfirmation(false)
                }
            }
            is PairingResponse.ProvidePin -> {
                pairingDevice.setPin(response.pin.toString().padStart(6, '0').toByteArray())
            }
            is PairingResponse.ProvideOobData -> {
                // OOB data delivery is handled at a lower level via BluetoothAdapter.
                // The pairing confirmation accepts the OOB exchange.
                pairingDevice.setPairingConfirmation(true)
            }
        }
    }

    private fun unregisterBondReceiver() {
        bondReceiver?.let {
            context.unregisterReceiver(it)
            bondReceiver = null
        }
    }

    private fun unregisterPairingReceiver() {
        pairingReceiver?.let {
            context.unregisterReceiver(it)
            pairingReceiver = null
        }
    }

    private companion object {
        // These constants are @hide in the Android SDK but stable since API 19.
        const val PAIRING_VARIANT_CONSENT = 3
        const val PAIRING_VARIANT_DISPLAY_PASSKEY = 4
        const val PAIRING_VARIANT_OOB_CONSENT = 6
    }
}
