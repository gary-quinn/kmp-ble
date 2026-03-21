@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.peripheral

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.TransportType
import kotlin.concurrent.Volatile
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent

internal sealed interface GattCallbackEvent {
    data class ConnectionStateChanged(val status: Int, val newState: Int) : GattCallbackEvent
    data class ServicesDiscovered(val status: Int, val services: List<BluetoothGattService>) : GattCallbackEvent
    data class MtuChanged(val mtu: Int, val status: Int) : GattCallbackEvent
    data class CharacteristicRead(val characteristic: BluetoothGattCharacteristic, val value: ByteArray, val status: Int) : GattCallbackEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as CharacteristicRead

            if (status != other.status) return false
            if (characteristic != other.characteristic) return false
            if (!value.contentEquals(other.value)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = status
            result = 31 * result + characteristic.hashCode()
            result = 31 * result + value.contentHashCode()
            return result
        }
    }

    data class CharacteristicWrite(val characteristic: BluetoothGattCharacteristic, val status: Int) : GattCallbackEvent
    data class CharacteristicChanged(val characteristic: BluetoothGattCharacteristic, val value: ByteArray) : GattCallbackEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as CharacteristicChanged

            if (characteristic != other.characteristic) return false
            if (!value.contentEquals(other.value)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = characteristic.hashCode()
            result = 31 * result + value.contentHashCode()
            return result
        }
    }

    data class DescriptorRead(val descriptor: BluetoothGattDescriptor, val value: ByteArray, val status: Int) : GattCallbackEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as DescriptorRead

            if (status != other.status) return false
            if (descriptor != other.descriptor) return false
            if (!value.contentEquals(other.value)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = status
            result = 31 * result + descriptor.hashCode()
            result = 31 * result + value.contentHashCode()
            return result
        }
    }

    data class DescriptorWrite(val descriptor: BluetoothGattDescriptor, val status: Int) : GattCallbackEvent
    data class ReadRemoteRssi(val rssi: Int, val status: Int) : GattCallbackEvent
}

internal class AndroidGattBridge(
    private val device: BluetoothDevice,
    private val context: Context,
) {
    private var callbackThread: HandlerThread? = null
    private var callbackHandler: Handler? = null

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile internal var onEvent: ((GattCallbackEvent) -> Unit)? = null

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            onEvent?.invoke(GattCallbackEvent.ConnectionStateChanged(status, newState))
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            onEvent?.invoke(GattCallbackEvent.ServicesDiscovered(status, gatt.services.orEmpty()))
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            onEvent?.invoke(GattCallbackEvent.MtuChanged(mtu, status))
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            onEvent?.invoke(GattCallbackEvent.CharacteristicRead(characteristic, value, status))
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            onEvent?.invoke(GattCallbackEvent.CharacteristicWrite(characteristic, status))
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            onEvent?.invoke(GattCallbackEvent.CharacteristicChanged(characteristic, value))
        }

        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
            value: ByteArray,
        ) {
            onEvent?.invoke(GattCallbackEvent.DescriptorRead(descriptor, value, status))
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            onEvent?.invoke(GattCallbackEvent.DescriptorWrite(descriptor, status))
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            onEvent?.invoke(GattCallbackEvent.ReadRemoteRssi(rssi, status))
        }
    }

    internal fun connect(options: ConnectionOptions): BluetoothGatt? {
        val transport = when (options.transportType) {
            TransportType.Auto -> BluetoothDevice.TRANSPORT_AUTO
            TransportType.LE -> BluetoothDevice.TRANSPORT_LE
            TransportType.BrEdr -> BluetoothDevice.TRANSPORT_BREDR
        }
        callbackThread?.quitSafely()

        val thread = HandlerThread("kmp-ble-cb/${device.address}").apply { start() }
        callbackThread = thread
        callbackHandler = Handler(thread.looper)

        gatt = device.connectGatt(
            context,
            options.autoConnect,
            gattCallback,
            transport,
            options.phyMask.value,
            callbackHandler,
        )
        return gatt
    }

    internal fun discoverServices(): Boolean {
        return gatt?.discoverServices() ?: false
    }

    internal fun requestMtu(mtu: Int): Boolean {
        return gatt?.requestMtu(mtu) ?: false
    }

    internal fun readCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        return gatt?.readCharacteristic(characteristic) ?: false
    }

    internal fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int,
    ): Boolean {
        val g = gatt ?: return false
        val result = g.writeCharacteristic(characteristic, value, writeType)
        return result == BluetoothGatt.GATT_SUCCESS
    }

    internal fun readDescriptor(descriptor: BluetoothGattDescriptor): Boolean {
        return gatt?.readDescriptor(descriptor) ?: false
    }

    internal fun writeDescriptor(descriptor: BluetoothGattDescriptor, value: ByteArray): Boolean {
        val g = gatt ?: return false
        val result = g.writeDescriptor(descriptor, value)
        return result == BluetoothGatt.GATT_SUCCESS
    }

    internal fun setCharacteristicNotification(
        characteristic: BluetoothGattCharacteristic,
        enable: Boolean,
    ): Boolean {
        return gatt?.setCharacteristicNotification(characteristic, enable) ?: false
    }

    internal fun readRemoteRssi(): Boolean {
        return gatt?.readRemoteRssi() ?: false
    }

    /**
     * Clears the GATT service cache via the internal `BluetoothGatt.refresh()` API.
     *
     * This is an **undocumented Android API** accessed through reflection. It exists on all
     * AOSP builds but is not part of the public SDK contract, so it may break in future
     * Android versions. If using R8/ProGuard, add a keep rule:
     * ```
     * -keepclassmembers class android.bluetooth.BluetoothGatt { boolean refresh(); }
     * ```
     *
     * Used as a workaround for OEMs (OnePlus, Xiaomi) that return stale cached services
     * after bonding. See [BleQuirks.RefreshServicesOnBond].
     */
    internal fun refreshDeviceCache(): Boolean {
        val g = gatt ?: return false
        return try {
            val method = g.javaClass.getMethod("refresh")
            method.invoke(g) as? Boolean ?: false
        } catch (e: Exception) {
            logEvent(BleLogEvent.Error(
                identifier = null,
                message = "BluetoothGatt.refresh() unavailable via reflection",
                cause = e,
            ))
            false
        }
    }

    internal fun disconnect() {
        gatt?.disconnect()
    }

    /** Release the BluetoothGatt handle but keep the bridge reusable for reconnection. */
    internal fun releaseGatt() {
        gatt?.close()
        gatt = null
    }

    /** Terminal — release all resources including the callback thread. */
    internal fun close() {
        gatt?.close()
        gatt = null
        onEvent = null
        callbackThread?.quitSafely()
        callbackThread = null
        callbackHandler = null
    }
}
