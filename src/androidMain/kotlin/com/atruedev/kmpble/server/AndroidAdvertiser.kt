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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.uuid.toJavaUuid

/**
 * Android implementation of [Advertiser] using [BluetoothLeAdvertiser].
 *
 * All mutable state is confined to [serialDispatcher] via `limitedParallelism(1)`.
 * [AdvertiseCallback] fires on a Binder thread and dispatches into [scope]
 * to serialize state updates.
 */
internal class AndroidAdvertiser(private val context: Context) : Advertiser {

    private val serialDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + serialDispatcher)

    private val _isAdvertising = MutableStateFlow(false)
    override val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private var advertiser: BluetoothLeAdvertiser? = null
    private var originalAdapterName: String? = null
    private var isStarting = false

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            scope.launch {
                isStarting = false
                _isAdvertising.value = true
                logEvent(BleLogEvent.ServerLifecycle("advertising started"))
            }
        }

        override fun onStartFailure(errorCode: Int) {
            scope.launch {
                isStarting = false
                _isAdvertising.value = false
                logEvent(BleLogEvent.Error(null, "Advertising start failed (error=$errorCode)", null))
            }
        }
    }

    override suspend fun startAdvertising(config: AdvertiseConfig): Unit = withContext(serialDispatcher) {
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
            .setTimeout(0)
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

    override suspend fun stopAdvertising(): Unit = withContext(serialDispatcher) {
        stopInternal()
        logEvent(BleLogEvent.ServerLifecycle("advertising stopped"))
    }

    override fun close() {
        runBlocking(serialDispatcher) {
            stopInternal()
            advertiser = null
        }
        scope.cancel()
    }

    private fun stopInternal() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (_: SecurityException) {
            // Best-effort stop
        }
        isStarting = false
        restoreAdapterName()
        _isAdvertising.value = false
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
