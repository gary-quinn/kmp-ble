package com.atruedev.kmpble.scanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
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
            val leScanner = bluetoothManager.adapter?.bluetoothLeScanner
            if (leScanner == null) {
                close(
                    ScanFailedException(
                        ERROR_SCANNER_NOT_AVAILABLE,
                        "BluetoothLeScanner not available. Is Bluetooth enabled?",
                    ),
                )
                return@callbackFlow
            }

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
            legacyOnlyScanWarning(config.legacyOnly)?.let { logEvent(it) }
            val settings =
                ScanSettings
                    .Builder()
                    .setScanMode(scanModeToAndroid(config.scanMode))
                    .setLegacy(config.legacyOnly)
                    .setPhy(scanPhyToAndroid(config.phy))
                    .build()

            leScanner.startScan(osFilters, settings, callback)
            logEvent(BleLogEvent.ScanStarted(config.filterGroups.size))

            awaitClose {
                leScanner.stopScan(callback)
                logEvent(BleLogEvent.ScanStopped("closed"))
            }
        }

    public companion object {
        /**
         * Error code for [ScanFailedException] when [BluetoothLeScanner] is not available
         * (e.g. Bluetooth is off or adapter is null).
         */
        public const val ERROR_SCANNER_NOT_AVAILABLE: Int = -1

        /**
         * Builds native `ScanFilter`s from the OR-of-AND-groups DSL. [ScanPredicate.NamePrefix]
         * and [ScanPredicate.MinRssi] have no `ScanFilter` equivalent (Android only supports
         * exact device-name matching, and RSSI filtering is applied post-scan) - such a
         * predicate-less group must still contribute an unconditional ("match anything") filter
         * to the returned list, not be dropped. Dropping it would silently turn what the DSL
         * documents as OR-of-AND-groups into an AND against whichever *other* groups happen to
         * have an OS-representable predicate - e.g. `filters { match { namePrefix("Foo") };
         * match { serviceUuid(uuid) } }` would incorrectly require the advertisement to also
         * carry `uuid`, when the intent was "name starts with Foo, OR advertises uuid".
         *
         * When *every* group lacks an OS-representable predicate, there is nothing for the
         * controller to filter on at all, so this returns `null` (matching
         * `BluetoothLeScanner.startScan`'s "report everything" behavior) rather than a list of
         * redundant match-anything filters.
         */
        @OptIn(ExperimentalUuidApi::class)
        internal fun buildOsFilters(filterGroups: List<List<ScanPredicate>>): List<ScanFilter>? {
            if (filterGroups.isEmpty()) return null

            // Only calls builder.build() when the group actually has an OS-representable
            // predicate - deferring the wildcard case avoids invoking ScanFilter.Builder.build()
            // (a real Android SDK call) for groups that may turn out to be unnecessary,
            // per the early-return below when no group has any OS predicate at all.
            val builtOrNull: List<ScanFilter?> =
                filterGroups.map { andGroup ->
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
                }

            if (builtOrNull.all { it == null }) return null
            return builtOrNull.map { it ?: ScanFilter.Builder().build() }
        }

        internal fun scanPhyToAndroid(phy: ScanPhy): Int =
            when (phy) {
                ScanPhy.Le1M -> BluetoothDevice.PHY_LE_1M
                ScanPhy.LeCoded -> BluetoothDevice.PHY_LE_CODED
                ScanPhy.All -> ScanSettings.PHY_LE_ALL_SUPPORTED
            }

        internal fun scanModeToAndroid(mode: ScanMode): Int =
            when (mode) {
                ScanMode.LowPower -> ScanSettings.SCAN_MODE_BALANCED
                ScanMode.Balanced -> ScanSettings.SCAN_MODE_BALANCED
                ScanMode.LowLatency -> ScanSettings.SCAN_MODE_LOW_LATENCY
            }

        /**
         * `legacyOnly` defaults to `true`, which maps to `ScanSettings.Builder.setLegacy(true)`
         * and makes the controller silently drop BLE 5.0 Extended Advertising PDUs before
         * `onScanResult` - no error, no `ScanEvent`. Returns a warning to log once per scan
         * start so that failure mode is at least diagnosable (see #576); returns `null` when
         * `legacyOnly` is `false`, since extended advertisements are received normally then.
         */
        internal fun legacyOnlyScanWarning(legacyOnly: Boolean): BleLogEvent.Warning? {
            if (!legacyOnly) return null
            return BleLogEvent.Warning(
                identifier = null,
                message =
                    "Scanning with legacyOnly=true (default): BLE 5.0 Extended Advertising " +
                        "peripherals are silently dropped before onScanResult, with no error or " +
                        "ScanEvent.Failed. A peripheral advertising a name plus service UUID(s) " +
                        "that exceed the legacy 31-byte payload falls back to Extended " +
                        "Advertising and will appear to simply not be advertising. iOS is " +
                        "unaffected (CoreBluetooth ignores this setting). Set legacyOnly = false " +
                        "to receive extended advertisements. " +
                        "https://github.com/gary-quinn/kmp-ble/issues/576",
            )
        }
    }
}
