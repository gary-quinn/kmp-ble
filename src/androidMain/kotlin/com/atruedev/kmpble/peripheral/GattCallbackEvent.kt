package com.atruedev.kmpble.peripheral

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService

/**
 * Events dispatched from Android's [android.bluetooth.BluetoothGattCallback]
 * through the [AndroidGattBridge] to platform-specific handlers.
 */
internal sealed interface GattCallbackEvent {
    data class StateChanged(
        val status: Int,
        val newState: Int,
    ) : GattCallbackEvent

    data class ServicesDiscovered(
        val status: Int,
        val services: List<BluetoothGattService>,
    ) : GattCallbackEvent

    data class MtuChanged(
        val mtu: Int,
        val status: Int,
    ) : GattCallbackEvent

    class CharacteristicRead(
        val characteristic: BluetoothGattCharacteristic,
        val value: ByteArray,
        val status: Int,
    ) : GattCallbackEvent

    data class CharacteristicWrite(
        val characteristic: BluetoothGattCharacteristic,
        val status: Int,
    ) : GattCallbackEvent

    class CharacteristicChanged(
        val characteristic: BluetoothGattCharacteristic,
        val value: ByteArray,
    ) : GattCallbackEvent

    class DescriptorRead(
        val descriptor: BluetoothGattDescriptor,
        val value: ByteArray,
        val status: Int,
    ) : GattCallbackEvent

    data class DescriptorWrite(
        val descriptor: BluetoothGattDescriptor,
        val status: Int,
    ) : GattCallbackEvent

    data class ReadRemoteRssi(
        val rssi: Int,
        val status: Int,
    ) : GattCallbackEvent

    data class PhyUpdated(
        val txPhy: Int,
        val rxPhy: Int,
        val status: Int,
    ) : GattCallbackEvent

    data class PhyRead(
        val txPhy: Int,
        val rxPhy: Int,
        val status: Int,
    ) : GattCallbackEvent

    data class SubrateChanged(
        val subrateFactor: Int,
        val subrateLatency: Int,
        val continuationNumber: Int,
        val supervisionTimeout: Int,
        val status: Int,
    ) : GattCallbackEvent
}
