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
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
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
 * ## Device Name
 *
 * If [AdvertiseConfig.name] is set, the system Bluetooth adapter name is changed.
 * The original name is saved and restored when advertising stops.
 *
 * ## Threading
 *
 * All public methods are synchronized via [lock] to prevent concurrent
 * start/stop races. [AdvertiseCallback] fires on a Binder thread; only
 * updates the atomic [_isAdvertising] StateFlow.
 *
 * ## Permissions
 *
 * - BLUETOOTH_ADVERTISE required on Android 12+
 */
internal class AndroidAdvertiser(private val context: Context) : Advertiser {

    private val lock = Any()

    private val _isAdvertising = MutableStateFlow(false)
    override val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private var advertiser: BluetoothLeAdvertiser? = null
    private var originalAdapterName: String? = null

    // Guards against TOCTOU race: two threads pass the _isAdvertising check
    // before onStartSuccess fires. Set inside synchronized block, cleared on
    // onStartSuccess/onStartFailure/stopAdvertising.
    private var isStarting = false

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            synchronized(lock) { isStarting = false }
            _isAdvertising.value = true
            logEvent(BleLogEvent.ServerLifecycle("advertising started"))
        }

        override fun onStartFailure(errorCode: Int) {
            synchronized(lock) { isStarting = false }
            _isAdvertising.value = false
            logEvent(BleLogEvent.Error(null, "Advertising start failed (error=$errorCode)", null))
        }
    }

    override fun startAdvertising(config: AdvertiseConfig) {
        synchronized(lock) {
            if (_isAdvertising.value || isStarting) {
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
                .setAdvertiseMode(config.mode.toAndroidMode())
                .setConnectable(config.connectable)
                .setTxPowerLevel(config.txPower.toAndroidTxPower())
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

            // Set device name if provided, saving original for restore
            if (config.name != null) {
                try {
                    originalAdapterName = adapter.name
                    adapter.name = config.name
                } catch (_: SecurityException) {
                    // Non-critical: device name may not be settable
                }
            }

            isStarting = true
            try {
                bleAdvertiser.startAdvertising(settings, dataBuilder.build(), advertiseCallback)
            } catch (e: SecurityException) {
                isStarting = false
                restoreAdapterName()
                throw AdvertiserException.StartFailed("Missing BLUETOOTH_ADVERTISE permission", e)
            }
        }
    }

    override fun stopAdvertising() {
        synchronized(lock) {
            try {
                advertiser?.stopAdvertising(advertiseCallback)
            } catch (_: SecurityException) {
                // Ignore permission errors on stop
            }
            isStarting = false
            restoreAdapterName()
            _isAdvertising.value = false
            logEvent(BleLogEvent.ServerLifecycle("advertising stopped"))
        }
    }

    override fun close() {
        synchronized(lock) {
            try {
                advertiser?.stopAdvertising(advertiseCallback)
            } catch (_: SecurityException) {
                // Ignore permission errors on stop
            }
            isStarting = false
            restoreAdapterName()
            _isAdvertising.value = false
            advertiser = null
        }
    }

    private fun restoreAdapterName() {
        val name = originalAdapterName ?: return
        originalAdapterName = null
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothManager?.adapter?.let { it.name = name }
        } catch (_: SecurityException) {
            // Best-effort restore
        }
    }
}

private fun AdvertiseMode.toAndroidMode(): Int = when (this) {
    AdvertiseMode.LowPower -> AdvertiseSettings.ADVERTISE_MODE_LOW_POWER
    AdvertiseMode.Balanced -> AdvertiseSettings.ADVERTISE_MODE_BALANCED
    AdvertiseMode.LowLatency -> AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
}

private fun AdvertiseTxPower.toAndroidTxPower(): Int = when (this) {
    AdvertiseTxPower.UltraLow -> AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW
    AdvertiseTxPower.Low -> AdvertiseSettings.ADVERTISE_TX_POWER_LOW
    AdvertiseTxPower.Medium -> AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM
    AdvertiseTxPower.High -> AdvertiseSettings.ADVERTISE_TX_POWER_HIGH
}
