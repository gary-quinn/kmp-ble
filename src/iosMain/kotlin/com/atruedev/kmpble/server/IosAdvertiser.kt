package com.atruedev.kmpble.server

import com.atruedev.kmpble.internal.PeripheralManagerProvider
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreBluetooth.CBAdvertisementDataLocalNameKey
import platform.CoreBluetooth.CBAdvertisementDataServiceUUIDsKey
import platform.CoreBluetooth.CBPeripheralManagerStatePoweredOn
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSError

/**
 * iOS implementation of [Advertiser] using [CBPeripheralManager].
 *
 * ## iOS advertising limitations
 *
 * iOS advertising is more restricted than Android:
 * - Only [CBAdvertisementDataLocalNameKey] and [CBAdvertisementDataServiceUUIDsKey] supported
 * - No manufacturer data in foreground advertising
 * - No control over advertising interval or TX power
 * - System controls advertising parameters
 * - Advertising is automatically stopped when app goes to background
 *   (unless bluetooth-peripheral background mode is enabled in Info.plist)
 *
 * [AdvertiseConfig] fields that don't map to iOS are silently ignored
 * with a log warning:
 * - [AdvertiseConfig.manufacturerData] — ignored (iOS foreground limitation)
 * - [AdvertiseConfig.includeTxPower] — ignored (iOS doesn't expose)
 * - [AdvertiseConfig.mode] — ignored (iOS controls interval)
 * - [AdvertiseConfig.txPower] — ignored (iOS controls power)
 */
internal class IosAdvertiser : Advertiser {

    private val manager get() = PeripheralManagerProvider.manager
    private val delegate get() = PeripheralManagerProvider.delegate

    private val _isAdvertising = MutableStateFlow(false)
    override val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private var isClosed = false

    override fun startAdvertising(config: AdvertiseConfig) {
        if (isClosed) {
            throw AdvertiserException.StartFailed("Advertiser has been closed")
        }
        if (_isAdvertising.value) {
            throw AdvertiserException.AlreadyAdvertising()
        }

        // Trigger lazy init
        manager

        if (manager.state != CBPeripheralManagerStatePoweredOn) {
            throw AdvertiserException.StartFailed("Bluetooth is not powered on")
        }

        // Log warnings for unsupported iOS fields
        if (config.manufacturerData.isNotEmpty()) {
            logEvent(
                BleLogEvent.Error(
                    identifier = null,
                    message = "AdvertiseConfig.manufacturerData is not supported on iOS (ignored)",
                    cause = null,
                ),
            )
        }
        if (config.includeTxPower) {
            logEvent(
                BleLogEvent.Error(
                    identifier = null,
                    message = "AdvertiseConfig.includeTxPower is not supported on iOS (ignored)",
                    cause = null,
                ),
            )
        }
        if (config.mode != DEFAULTS.mode) {
            logEvent(
                BleLogEvent.Error(
                    identifier = null,
                    message = "AdvertiseConfig.mode is not supported on iOS — system controls interval (ignored)",
                    cause = null,
                ),
            )
        }
        if (config.txPower != DEFAULTS.txPower) {
            logEvent(
                BleLogEvent.Error(
                    identifier = null,
                    message = "AdvertiseConfig.txPower is not supported on iOS — system controls power (ignored)",
                    cause = null,
                ),
            )
        }

        val advertisementData = mutableMapOf<String, Any>()

        if (config.name != null) {
            advertisementData[CBAdvertisementDataLocalNameKey] = config.name
        }

        if (config.serviceUuids.isNotEmpty()) {
            advertisementData[CBAdvertisementDataServiceUUIDsKey] = config.serviceUuids.map {
                CBUUID.UUIDWithString(it.toString())
            }
        }

        // Register callback to receive the advertising result
        delegate.onStartAdvertising = { error -> handleDidStartAdvertising(error) }

        @Suppress("UNCHECKED_CAST")
        manager.startAdvertising(advertisementData as Map<Any?, *>)
    }

    override fun stopAdvertising() {
        if (!_isAdvertising.value) return
        manager.stopAdvertising()
        _isAdvertising.value = false
        logEvent(BleLogEvent.ServerLifecycle("advertising stopped"))
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        if (_isAdvertising.value) {
            manager.stopAdvertising()
            _isAdvertising.value = false
        }
        delegate.onStartAdvertising = null
    }

    private companion object {
        val DEFAULTS = AdvertiseConfig()
    }

    private fun handleDidStartAdvertising(error: NSError?) {
        if (error != null) {
            _isAdvertising.value = false
            logEvent(
                BleLogEvent.Error(
                    identifier = null,
                    message = "Advertising failed: ${error.localizedDescription}",
                    cause = null,
                ),
            )
        } else {
            _isAdvertising.value = true
            logEvent(BleLogEvent.ServerLifecycle("advertising started"))
        }
    }
}
