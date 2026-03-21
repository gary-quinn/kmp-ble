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
import com.atruedev.kmpble.bonding.PairingEvent
import com.atruedev.kmpble.bonding.PairingHandler
import com.atruedev.kmpble.bonding.PairingResponse
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Intercepts [BluetoothDevice.ACTION_PAIRING_REQUEST] broadcasts and delegates
 * to a [PairingHandler] for programmatic pairing responses.
 *
 * All mutable state is confined to [serialDispatcher] (`limitedParallelism(1)`
 * inherited from [PeripheralContext][com.atruedev.kmpble.peripheral.internal.PeripheralContext]).
 */
@ExperimentalBleApi
internal class AndroidPairingRequestHandler(
    private val device: BluetoothDevice,
    private val context: Context,
    private val scope: CoroutineScope,
    private val serialDispatcher: CoroutineDispatcher,
) {
    private var handler: PairingHandler? = null
    private var receiver: BroadcastReceiver? = null

    suspend fun setHandler(pairingHandler: PairingHandler?): Unit = withContext(serialDispatcher) {
        handler = pairingHandler
    }

    suspend fun start(): Unit = withContext(serialDispatcher) {
        if (receiver != null) return@withContext

        val br = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_PAIRING_REQUEST) return

                val pairingDevice = intent.getParcelableExtra(
                    BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java
                )
                if (pairingDevice?.address != device.address) return

                val currentHandler = handler ?: return
                val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1)
                val key = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, -1)
                val identifier = Identifier(device.address)

                val event = variantToEvent(variant, identifier, key)
                if (event == null) {
                    logEvent(BleLogEvent.Error(identifier, "Unknown pairing variant: $variant", null))
                    return
                }

                scope.launch {
                    try {
                        val response = currentHandler.onPairingEvent(event)
                        applyResponse(pairingDevice, response)
                    } catch (e: Exception) {
                        logEvent(BleLogEvent.Error(identifier, "Pairing handler threw: ${e.message}", e))
                    }
                }

                // ACTION_PAIRING_REQUEST is an ordered broadcast on AOSP. Aborting
                // suppresses the system pairing dialog. On OEM ROMs that send an
                // unordered broadcast this is a safe no-op.
                abortBroadcast()
            }
        }

        receiver = br
        val filter = IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST).apply {
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context, br, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    suspend fun stop(): Unit = withContext(serialDispatcher) {
        unregisterReceiver()
    }

    /**
     * Synchronous teardown for use in [AutoCloseable.close] where
     * the coroutine scope is about to be cancelled.
     */
    fun closeSync() {
        unregisterReceiver()
    }

    private fun unregisterReceiver() {
        receiver?.let {
            context.unregisterReceiver(it)
            receiver = null
        }
    }

    private fun applyResponse(pairingDevice: BluetoothDevice, response: PairingResponse) {
        when (response) {
            is PairingResponse.Confirm ->
                pairingDevice.setPairingConfirmation(response.accepted)
            is PairingResponse.ProvidePin ->
                pairingDevice.setPin(response.pin.toString().padStart(6, '0').toByteArray())
            is PairingResponse.ProvideOobData ->
                // OOB key exchange happens at a lower level via BluetoothAdapter;
                // confirming here accepts the OOB pairing.
                pairingDevice.setPairingConfirmation(true)
        }
    }

    private companion object {
        // @hide in the SDK but stable since API 19.
        // See: frameworks/base/core/java/android/bluetooth/BluetoothDevice.java
        const val PAIRING_VARIANT_CONSENT = 3
        const val PAIRING_VARIANT_DISPLAY_PASSKEY = 4
        const val PAIRING_VARIANT_OOB_CONSENT = 6

        fun variantToEvent(variant: Int, id: Identifier, key: Int): PairingEvent? = when (variant) {
            BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION -> PairingEvent.NumericComparison(id, key)
            BluetoothDevice.PAIRING_VARIANT_PIN -> PairingEvent.PasskeyRequest(id)
            PAIRING_VARIANT_OOB_CONSENT -> PairingEvent.OutOfBandDataRequest(id)
            PAIRING_VARIANT_CONSENT -> PairingEvent.JustWorksConfirmation(id)
            PAIRING_VARIANT_DISPLAY_PASSKEY -> PairingEvent.PasskeyNotification(id, key)
            else -> null
        }
    }
}
