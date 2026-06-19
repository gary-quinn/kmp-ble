package com.atruedev.kmpble.scanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.scanner.internal.toScanEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.uuid.ExperimentalUuidApi

public class AndroidScanner(
    private val context: Context,
    configure: ScannerConfig.() -> Unit = {},
) : Scanner {
    private val config = ScannerConfig().apply(configure)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val scanEvents: Flow<ScanEvent> = createRawScanFlow().toScanEvents(config, scope)

    override fun close() {
        scope.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun createRawScanFlow(): Flow<Advertisement> =
        callbackFlow {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val leScanner =
                bluetoothManager.adapter?.bluetoothLeScanner
                    ?: throw IllegalStateException("BluetoothLeScanner not available. Is Bluetooth enabled?")

            val callback =
                object : ScanCallback() {
                    override fun onScanResult(
                        callbackType: Int,
                        result: ScanResult,
                    ) {
                        trySend(result.toAdvertisement())
                    }

                    override fun onScanFailed(errorCode: Int) {
                        close(ScanFailedException(errorCode))
                    }
                }

            val osFilters = buildOsFilters(config.filterGroups)
            val settings =
                ScanSettings
                    .Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setLegacy(config.legacyOnly)
                    .setPhy(scanPhyToAndroid(config.scanPhy))
                    .build()

            leScanner.startScan(osFilters, settings, callback)

            awaitClose {
                leScanner.stopScan(callback)
            }
        }

    public companion object {
        @OptIn(ExperimentalUuidApi::class)
        internal fun buildOsFilters(filterGroups: List<List<ScanPredicate>>): List<ScanFilter>? {
            if (filterGroups.isEmpty()) return null

            return filterGroups
                .mapNotNull { andGroup ->
                    val builder = ScanFilter.Builder()
                    var hasOsPredicate = false

                    for (predicate in andGroup) {
                        when (predicate) {
                            is ScanPredicate.ServiceUuid -> {
                                builder.setServiceUuid(ParcelUuid(java.util.UUID.fromString(predicate.uuid.toString())))
                                hasOsPredicate = true
                            }
                            is ScanPredicate.Name -> {
                                builder.setDeviceName(predicate.exact)
                                hasOsPredicate = true
                            }
                            is ScanPredicate.Address -> {
                                builder.setDeviceAddress(predicate.mac)
                                hasOsPredicate = true
                            }
                            is ScanPredicate.ManufacturerData -> {
                                if (predicate.data != null) {
                                    if (predicate.mask != null) {
                                        builder.setManufacturerData(predicate.companyId, predicate.data, predicate.mask)
                                    } else {
                                        builder.setManufacturerData(predicate.companyId, predicate.data)
                                    }
                                }
                                hasOsPredicate = true
                            }
                            is ScanPredicate.ServiceData -> {
                                val uuid = ParcelUuid(java.util.UUID.fromString(predicate.uuid.toString()))
                                if (predicate.data != null) {
                                    if (predicate.mask != null) {
                                        builder.setServiceData(uuid, predicate.data, predicate.mask)
                                    } else {
                                        builder.setServiceData(uuid, predicate.data)
                                    }
                                }
                                hasOsPredicate = true
                            }
                            is ScanPredicate.NamePrefix,
                            is ScanPredicate.MinRssi,
                            -> { /* post-filter only */ }
                        }
                    }

                    if (hasOsPredicate) builder.build() else null
                }.ifEmpty { null }
        }

        internal fun scanPhyToAndroid(phySet: Set<Phy>): Int {
            if (phySet.isEmpty()) return ScanSettings.PHY_LE_ALL_SUPPORTED
            if (phySet.size == 1) {
                return when (phySet.single()) {
                    Phy.Le1M -> ScanSettings.PHY_LE_1M
                    Phy.Le2M -> ScanSettings.PHY_LE_2M
                    Phy.LeCoded -> ScanSettings.PHY_LE_CODED
                }
            }
            return ScanSettings.PHY_LE_ALL_SUPPORTED
        }
    }
}
