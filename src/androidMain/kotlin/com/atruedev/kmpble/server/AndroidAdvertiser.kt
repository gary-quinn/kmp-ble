@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.server

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.toJavaUuid

/**
 * Android implementation of [Advertiser] using [BluetoothLeAdvertiser].
 *
 * ## Key Details
 *
 * - Obtained via BluetoothAdapter.getBluetoothLeAdvertiser()
 * - Returns null if device doesn't support advertising (rare for phones, common for some tablets)
 * - AdvertiseCallback.onStartSuccess/onStartFailure are one-shot
 * - stopAdvertising() is synchronous, no callback
 * - Service UUIDs in AdvertiseData must use either 16-bit or 128-bit format
 * - Total advertisement payload limited to 31 bytes (legacy advertising)
 * - If config exceeds this, Android's onStartFailure fires with ADVERTISE_FAILED_DATA_TOO_LARGE
 *
 * ## Permissions
 *
 * - BLUETOOTH_ADVERTISE required on Android 12+
 */
internal class AndroidAdvertiser(private val context: Context) : Advertiser {

    private val _isAdvertising = MutableStateFlow(false)
    override val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private var advertiser: BluetoothLeAdvertiser? = null

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            _isAdvertising.value = true
        }

        override fun onStartFailure(errorCode: Int) {
            _isAdvertising.value = false
            // Error is reported synchronously via startAdvertising flow,
            // but if we get here asynchronously, update state.
        }
    }

    override fun startAdvertising(config: AdvertiseConfig) {
        if (_isAdvertising.value) {
            throw AdvertiserException.AlreadyAdvertising()
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: throw AdvertiserException.NotSupported("BluetoothManager not available")

        val adapter = bluetoothManager.adapter
            ?: throw AdvertiserException.NotSupported("Bluetooth not available")

        if (!adapter.isEnabled) {
            throw AdvertiserException.StartFailed("Bluetooth is not enabled")
        }

        val bleAdvertiser = adapter.bluetoothLeAdvertiser
            ?: throw AdvertiserException.NotSupported("BLE advertising not supported on this device")

        advertiser = bleAdvertiser

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(config.connectable)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setTimeout(0) // Advertise indefinitely
            .build()

        val dataBuilder = AdvertiseData.Builder()
            .setIncludeDeviceName(config.name != null)
            .setIncludeTxPowerLevel(config.includeTxPower)

        for (uuid in config.serviceUuids) {
            dataBuilder.addServiceUuid(ParcelUuid(uuid.toJavaUuid()))
        }

        for ((companyId, data) in config.manufacturerData) {
            dataBuilder.addManufacturerData(companyId, data)
        }

        // Set device name if provided
        if (config.name != null) {
            try {
                adapter.name = config.name
            } catch (_: SecurityException) {
                // Non-critical: device name may not be settable
            }
        }

        try {
            bleAdvertiser.startAdvertising(settings, dataBuilder.build(), advertiseCallback)
        } catch (e: SecurityException) {
            throw AdvertiserException.StartFailed("Missing BLUETOOTH_ADVERTISE permission", e)
        }
    }

    override fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (_: SecurityException) {
            // Ignore permission errors on stop
        }
        _isAdvertising.value = false
    }

    override fun close() {
        stopAdvertising()
        advertiser = null
    }
}
