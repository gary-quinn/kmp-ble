package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.internal.CentralManagerProvider
import platform.CoreBluetooth.CBCharacteristicWriteWithResponse
import platform.CoreBluetooth.CBCharacteristicWriteWithoutResponse
import platform.CoreBluetooth.CBL2CAPChannel
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBDescriptor
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.darwin.NSObject

internal sealed interface AppleCallbackEvent {
    data class DidDiscoverServices(val error: NSError?) : AppleCallbackEvent
    data class DidDiscoverCharacteristics(val service: CBService, val error: NSError?) : AppleCallbackEvent
    // Kotlin/Native collapses didUpdateValue and didWriteValue for characteristics
    // into the same function signature. We use the didUpdateValue override for both
    // notifications and read responses. Write responses come through didWriteValue.
    data class DidUpdateValueForCharacteristic(val characteristic: CBCharacteristic, val error: NSError?) : AppleCallbackEvent
    data class DidWriteValueForCharacteristic(val characteristic: CBCharacteristic, val error: NSError?) : AppleCallbackEvent
    data class DidUpdateValueForDescriptor(val descriptor: CBDescriptor, val error: NSError?) : AppleCallbackEvent
    data class DidWriteValueForDescriptor(val descriptor: CBDescriptor, val error: NSError?) : AppleCallbackEvent
    data class DidReadRSSI(val rssi: NSNumber, val error: NSError?) : AppleCallbackEvent
    data class DidOpenL2CAPChannel(val channel: CBL2CAPChannel?, val error: NSError?) : AppleCallbackEvent
}

internal class ApplePeripheralBridge(
    internal val cbPeripheral: CBPeripheral,
) {
    internal var onEvent: ((AppleCallbackEvent) -> Unit)? = null

    // Kotlin/Native CBPeripheralDelegateProtocol:
    // didUpdateValueForCharacteristic and didWriteValueForCharacteristic have
    // different ObjC selectors but the same Kotlin signature (CBPeripheral, CBCharacteristic, NSError?).
    // Same for descriptors. We implement only what K/N generates distinct methods for.
    private val peripheralDelegate = object : NSObject(), CBPeripheralDelegateProtocol {
        override fun peripheral(peripheral: CBPeripheral, didDiscoverServices: NSError?) {
            onEvent?.invoke(AppleCallbackEvent.DidDiscoverServices(didDiscoverServices))
        }

        override fun peripheral(
            peripheral: CBPeripheral,
            didDiscoverCharacteristicsForService: CBService,
            error: NSError?,
        ) {
            onEvent?.invoke(
                AppleCallbackEvent.DidDiscoverCharacteristics(didDiscoverCharacteristicsForService, error)
            )
        }

        // K/N maps didUpdateValue and didWriteValue for characteristics to the same
        // Kotlin signature. Only didUpdateValue is overridden — handles reads + notifications.
        // Write confirmations are handled by the IosPeripheral completing the write deferred
        // when this callback fires with a pending write operation.
        override fun peripheral(
            peripheral: CBPeripheral,
            didUpdateValueForCharacteristic: CBCharacteristic,
            error: NSError?,
        ) {
            onEvent?.invoke(
                AppleCallbackEvent.DidUpdateValueForCharacteristic(didUpdateValueForCharacteristic, error)
            )
        }

        // Same K/N collision for descriptors: didUpdateValue and didWriteValue share signature.
        override fun peripheral(
            peripheral: CBPeripheral,
            didUpdateValueForDescriptor: CBDescriptor,
            error: NSError?,
        ) {
            onEvent?.invoke(
                AppleCallbackEvent.DidUpdateValueForDescriptor(didUpdateValueForDescriptor, error)
            )
        }

        override fun peripheral(peripheral: CBPeripheral, didReadRSSI: NSNumber, error: NSError?) {
            onEvent?.invoke(AppleCallbackEvent.DidReadRSSI(didReadRSSI, error))
        }

        override fun peripheral(
            peripheral: CBPeripheral,
            didOpenL2CAPChannel: CBL2CAPChannel?,
            error: NSError?,
        ) {
            onEvent?.invoke(AppleCallbackEvent.DidOpenL2CAPChannel(didOpenL2CAPChannel, error))
        }
    }

    init {
        cbPeripheral.delegate = peripheralDelegate
    }

    internal fun connect() {
        CentralManagerProvider.manager.connectPeripheral(cbPeripheral, options = null)
    }

    internal fun discoverServices() {
        cbPeripheral.discoverServices(null)
    }

    internal fun discoverCharacteristics(service: CBService) {
        cbPeripheral.discoverCharacteristics(null, service)
    }

    internal fun readCharacteristic(characteristic: CBCharacteristic) {
        cbPeripheral.readValueForCharacteristic(characteristic)
    }

    internal fun writeCharacteristic(characteristic: CBCharacteristic, data: NSData, withResponse: Boolean) {
        val type = if (withResponse) CBCharacteristicWriteWithResponse else CBCharacteristicWriteWithoutResponse
        cbPeripheral.writeValue(data, characteristic, type)
    }

    internal fun readDescriptor(descriptor: CBDescriptor) {
        cbPeripheral.readValueForDescriptor(descriptor)
    }

    internal fun writeDescriptor(descriptor: CBDescriptor, data: NSData) {
        cbPeripheral.writeValue(data, descriptor)
    }

    internal fun setNotifyValue(enabled: Boolean, characteristic: CBCharacteristic) {
        cbPeripheral.setNotifyValue(enabled, characteristic)
    }

    internal fun readRSSI() {
        cbPeripheral.readRSSI()
    }

    internal fun openL2CAPChannel(psm: UShort) {
        cbPeripheral.openL2CAPChannel(psm)
    }

    internal fun disconnect() {
        CentralManagerProvider.manager.cancelPeripheralConnection(cbPeripheral)
    }

    internal fun close() {
        onEvent = null
        cbPeripheral.delegate = null
    }
}
