@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.server

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.uuid.toJavaUuid

/**
 * Android implementation of [ExtendedAdvertiser] using the BLE 5.0 `AdvertisingSet` API.
 *
 * All mutable state ([advertisingSets], [nextSetId]) is confined to [serialDispatcher]
 * via `limitedParallelism(1)`. No locks or synchronized blocks.
 */
@ExperimentalBleApi
internal class AndroidExtendedAdvertiser(private val context: Context) : ExtendedAdvertiser {

    private val serialDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + serialDispatcher)

    private val _activeSets = MutableStateFlow<Set<Int>>(emptySet())
    override val activeSets: StateFlow<Set<Int>> = _activeSets.asStateFlow()

    private var nextSetId = 0
    private val advertisingSets = mutableMapOf<Int, AdvertisingSetHandle>()

    override suspend fun startAdvertisingSet(config: ExtendedAdvertiseConfig): Int {
        val advertiser = getLeAdvertiser()
        val deferred = CompletableDeferred<Int>()

        val params = config.toParameters()
        val data = config.toAdvertiseData()

        val setId = withContext(serialDispatcher) { ++nextSetId }
        var callbackRef: AdvertisingSetCallback? = null

        val callback = object : AdvertisingSetCallback() {
            override fun onAdvertisingSetStarted(
                advertisingSet: AdvertisingSet?,
                txPower: Int,
                status: Int,
            ) {
                scope.launch {
                    if (status == ADVERTISE_SUCCESS && advertisingSet != null) {
                        advertisingSets[setId] = AdvertisingSetHandle(advertisingSet, callbackRef!!)
                        _activeSets.update { it + setId }
                        logEvent(BleLogEvent.ServerLifecycle("extended advertising set $setId started"))
                        deferred.complete(setId)
                    } else {
                        deferred.completeExceptionally(
                            AdvertiserException.StartFailed("Advertising set start failed (status=$status)")
                        )
                    }
                }
            }

            override fun onAdvertisingSetStopped(advertisingSet: AdvertisingSet?) {
                scope.launch {
                    val stoppedId = advertisingSets.entries
                        .firstOrNull { it.value.set === advertisingSet }?.key
                    if (stoppedId != null) {
                        advertisingSets.remove(stoppedId)
                        _activeSets.update { it - stoppedId }
                    }
                }
            }
        }
        callbackRef = callback

        try {
            advertiser.startAdvertisingSet(params, data, null, null, null, callback)
        } catch (e: SecurityException) {
            throw AdvertiserException.StartFailed("Missing BLUETOOTH_ADVERTISE permission", e)
        }

        return deferred.await()
    }

    override suspend fun stopAdvertisingSet(setId: Int): Unit = withContext(serialDispatcher) {
        val handle = advertisingSets.remove(setId) ?: return@withContext
        val advertiser = getLeAdvertiser()
        try {
            advertiser.stopAdvertisingSet(handle.callback)
        } catch (_: SecurityException) {
            // Best-effort stop
        }
        _activeSets.update { it - setId }
        logEvent(BleLogEvent.ServerLifecycle("extended advertising set $setId stopped"))
    }

    override fun close() {
        runBlocking(serialDispatcher) {
            val advertiser = try { getLeAdvertiser() } catch (_: Exception) { null }
            for ((_, handle) in advertisingSets) {
                try {
                    advertiser?.stopAdvertisingSet(handle.callback)
                } catch (_: SecurityException) { }
            }
            advertisingSets.clear()
            _activeSets.value = emptySet()
        }
        scope.cancel()
    }

    private fun getLeAdvertiser(): BluetoothLeAdvertiser {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: throw AdvertiserException.NotSupported("BluetoothManager not available")
        val adapter = manager.adapter
            ?: throw AdvertiserException.NotSupported("Bluetooth not available")
        return adapter.bluetoothLeAdvertiser
            ?: throw AdvertiserException.NotSupported("BLE advertising not supported on this device")
    }

    private data class AdvertisingSetHandle(
        val set: AdvertisingSet,
        val callback: AdvertisingSetCallback,
    )
}

@ExperimentalBleApi
private fun ExtendedAdvertiseConfig.toParameters(): AdvertisingSetParameters =
    AdvertisingSetParameters.Builder()
        .setConnectable(connectable)
        .setScannable(scannable)
        .setLegacyMode(false)
        .setPrimaryPhy(primaryPhy.toAndroidPhy())
        .setSecondaryPhy(secondaryPhy.toAndroidPhy())
        .setInterval(interval.toAndroidInterval())
        .setTxPowerLevel(txPower.toAndroidTxPower())
        .setIncludeTxPower(includeTxPower)
        .build()

@ExperimentalBleApi
private fun ExtendedAdvertiseConfig.toAdvertiseData(): AdvertiseData {
    val builder = AdvertiseData.Builder()
        .setIncludeDeviceName(name != null)

    for (uuid in serviceUuids) {
        builder.addServiceUuid(ParcelUuid(uuid.toJavaUuid()))
    }
    for ((companyId, data) in manufacturerData) {
        builder.addManufacturerData(companyId, data)
    }
    for ((uuid, data) in serviceData) {
        builder.addServiceData(ParcelUuid(uuid.toJavaUuid()), data)
    }

    return builder.build()
}

private fun Phy.toAndroidPhy(): Int = when (this) {
    Phy.Le1M -> android.bluetooth.BluetoothDevice.PHY_LE_1M
    Phy.Le2M -> android.bluetooth.BluetoothDevice.PHY_LE_2M
    Phy.LeCoded -> android.bluetooth.BluetoothDevice.PHY_LE_CODED
}

@ExperimentalBleApi
private fun AdvertiseInterval.toAndroidInterval(): Int = when (this) {
    AdvertiseInterval.LowPower -> AdvertisingSetParameters.INTERVAL_HIGH
    AdvertiseInterval.Balanced -> AdvertisingSetParameters.INTERVAL_MEDIUM
    AdvertiseInterval.LowLatency -> AdvertisingSetParameters.INTERVAL_LOW
}

private fun AdvertiseTxPower.toAndroidTxPower(): Int = when (this) {
    AdvertiseTxPower.UltraLow -> AdvertisingSetParameters.TX_POWER_ULTRA_LOW
    AdvertiseTxPower.Low -> AdvertisingSetParameters.TX_POWER_LOW
    AdvertiseTxPower.Medium -> AdvertisingSetParameters.TX_POWER_MEDIUM
    AdvertiseTxPower.High -> AdvertisingSetParameters.TX_POWER_HIGH
}
