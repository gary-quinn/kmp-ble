package com.atruedev.kmpble.server

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.internal.IosPeripheralManagerDelegate
import com.atruedev.kmpble.internal.PeripheralManagerProvider
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.CoreBluetooth.CBAdvertisementDataLocalNameKey
import platform.CoreBluetooth.CBAdvertisementDataServiceUUIDsKey
import platform.CoreBluetooth.CBPeripheralManager
import platform.CoreBluetooth.CBPeripheralManagerStatePoweredOn
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSError

/**
 * iOS implementation of [ExtendedAdvertiser].
 *
 * CoreBluetooth does not expose BLE 5.0 extended advertising parameters.
 * This implementation provides the [ExtendedAdvertiser] contract using
 * legacy `CBPeripheralManager` advertising. PHY, interval, and scannable
 * fields from [ExtendedAdvertiseConfig] are silently ignored with a log warning.
 *
 * Only one advertising set is supported at a time (iOS limitation).
 *
 * All mutable state is confined to [serialDispatcher] via `limitedParallelism(1)`.
 */
@ExperimentalBleApi
internal class IosExtendedAdvertiser(
    private val manager: CBPeripheralManager = PeripheralManagerProvider.manager,
    private val delegate: IosPeripheralManagerDelegate = PeripheralManagerProvider.delegate,
) : ExtendedAdvertiser {

    private val serialDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + serialDispatcher)

    private val _activeSets = MutableStateFlow<Set<Int>>(emptySet())
    override val activeSets: StateFlow<Set<Int>> = _activeSets.asStateFlow()

    private var nextSetId = 0
    private var isClosed = false

    override suspend fun startAdvertisingSet(config: ExtendedAdvertiseConfig): Int =
        withContext(serialDispatcher) {
            if (isClosed) {
                throw AdvertiserException.StartFailed("Extended advertiser has been closed")
            }
            if (_activeSets.value.isNotEmpty()) {
                throw AdvertiserException.StartFailed(
                    "iOS supports only one advertising set at a time"
                )
            }
            if (manager.state != CBPeripheralManagerStatePoweredOn) {
                throw AdvertiserException.StartFailed("Bluetooth is not powered on")
            }

            logIosLimitations(config)

            val setId = ++nextSetId
            val advertisementData = mutableMapOf<String, Any>()

            if (config.name != null) {
                advertisementData[CBAdvertisementDataLocalNameKey] = config.name
            }
            if (config.serviceUuids.isNotEmpty()) {
                advertisementData[CBAdvertisementDataServiceUUIDsKey] = config.serviceUuids.map {
                    CBUUID.UUIDWithString(it.toString())
                }
            }

            delegate.onStartAdvertising = { error -> handleDidStartAdvertising(setId, error) }

            @Suppress("UNCHECKED_CAST")
            manager.startAdvertising(advertisementData as Map<Any?, *>)
            _activeSets.update { it + setId }
            setId
        }

    override suspend fun stopAdvertisingSet(setId: Int): Unit = withContext(serialDispatcher) {
        if (setId !in _activeSets.value) return@withContext
        manager.stopAdvertising()
        _activeSets.update { it - setId }
        logEvent(BleLogEvent.ServerLifecycle("extended advertising set $setId stopped"))
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        if (_activeSets.value.isNotEmpty()) {
            manager.stopAdvertising()
            _activeSets.value = emptySet()
        }
        delegate.onStartAdvertising = null
        scope.cancel()
    }

    private fun handleDidStartAdvertising(setId: Int, error: NSError?) {
        scope.launch {
            if (error != null) {
                _activeSets.update { it - setId }
                logEvent(BleLogEvent.Error(null, "Extended advertising failed: ${error.localizedDescription}", null))
            } else {
                logEvent(BleLogEvent.ServerLifecycle("extended advertising set $setId started"))
            }
        }
    }

    private fun logIosLimitations(config: ExtendedAdvertiseConfig) {
        if (config.manufacturerData.isNotEmpty()) warnUnsupported("manufacturerData")
        if (config.serviceData.isNotEmpty()) warnUnsupported("serviceData")
        if (config.scannable) warnUnsupported("scannable")
    }

    private fun warnUnsupported(field: String) {
        logEvent(
            BleLogEvent.Error(
                identifier = null,
                message = "ExtendedAdvertiseConfig.$field is not supported on iOS (ignored)",
                cause = null,
            ),
        )
    }
}
