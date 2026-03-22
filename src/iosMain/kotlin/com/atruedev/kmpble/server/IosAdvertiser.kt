package com.atruedev.kmpble.server

import com.atruedev.kmpble.internal.IosPeripheralManagerDelegate
import com.atruedev.kmpble.internal.PeripheralManagerProvider
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import kotlin.concurrent.AtomicInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreBluetooth.CBAdvertisementDataLocalNameKey
import platform.CoreBluetooth.CBAdvertisementDataServiceUUIDsKey
import platform.CoreBluetooth.CBPeripheralManager
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
internal class IosAdvertiser(
    private val manager: CBPeripheralManager = PeripheralManagerProvider.manager,
    private val delegate: IosPeripheralManagerDelegate = PeripheralManagerProvider.delegate,
) : Advertiser {

    private val _isAdvertising = MutableStateFlow(false)
    override val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val isClosed = AtomicInt(0)

    override suspend fun startAdvertising(config: AdvertiseConfig) {
        if (isClosed.value != 0) {
            throw AdvertiserException.StartFailed("Advertiser has been closed")
        }
        if (_isAdvertising.value) {
            throw AdvertiserException.AlreadyAdvertising()
        }

        if (manager.state != CBPeripheralManagerStatePoweredOn) {
            throw AdvertiserException.StartFailed("Bluetooth is not powered on")
        }

        if (config.manufacturerData.isNotEmpty()) warnUnsupported("manufacturerData")
        if (config.includeTxPower) warnUnsupported("includeTxPower")
        if (config.mode != DEFAULTS.mode) warnUnsupported("mode", "system controls interval")
        if (config.txPower != DEFAULTS.txPower) warnUnsupported("txPower", "system controls power")

        val advertisementData = mutableMapOf<Any?, Any>()

        if (config.name != null) {
            advertisementData[CBAdvertisementDataLocalNameKey] = config.name
        }

        if (config.serviceUuids.isNotEmpty()) {
            advertisementData[CBAdvertisementDataServiceUUIDsKey] = config.serviceUuids.map {
                CBUUID.UUIDWithString(it.toString())
            }
        }

        delegate.onStartAdvertising = { error -> handleDidStartAdvertising(error) }

        manager.startAdvertising(advertisementData)
    }

    override suspend fun stopAdvertising() {
        if (!_isAdvertising.value) return
        manager.stopAdvertising()
        _isAdvertising.value = false
        logEvent(BleLogEvent.ServerLifecycle("advertising stopped"))
    }

    override fun close() {
        if (isClosed.compareAndSet(0, 1).not()) return
        if (_isAdvertising.value) {
            manager.stopAdvertising()
            _isAdvertising.value = false
        }
        delegate.onStartAdvertising = null
    }

    private fun warnUnsupported(field: String, reason: String = "ignored") {
        logEvent(
            BleLogEvent.Error(
                identifier = null,
                message = "AdvertiseConfig.$field is not supported on iOS ($reason)",
                cause = null,
            ),
        )
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

    private companion object {
        val DEFAULTS = AdvertiseConfig()
    }
}
