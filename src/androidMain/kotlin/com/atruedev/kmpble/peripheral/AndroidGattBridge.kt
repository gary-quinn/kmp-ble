@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.peripheral

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.TransportType
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update

internal class AndroidGattBridge(
    private val device: BluetoothDevice,
    private val context: Context,
) {
    private var callbackThread: HandlerThread? = null
    private var callbackHandler: Handler? = null

    /**
     * Atomic reference to the connected GATT handle. Null when no GATT connection
     * is active. Used across callback thread and main thread.
     */
    private val gatt = atomic<BluetoothGatt?>(null)

    /**
     * Atomic reference to the event callback handler. Updated when a new GATT
     * connection is established. Set to null during close() to prevent events
     * after bridge shutdown.
     */
    val onEvent = atomic<((GattCallbackEvent) -> Unit)?>(null)

    private val gattCallback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                onEvent.value?.invoke(GattCallbackEvent.ConnectionStateChanged(status, newState))
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int,
            ) {
                onEvent.value?.invoke(GattCallbackEvent.ServicesDiscovered(status, gatt.services.orEmpty()))
            }

            override fun onMtuChanged(
                gatt: BluetoothGatt,
                mtu: Int,
                status: Int,
            ) {
                onEvent.value?.invoke(GattCallbackEvent.MtuChanged(mtu, status))
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) {
                onEvent.value?.invoke(GattCallbackEvent.CharacteristicRead(characteristic, value, status))
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                onEvent.value?.invoke(GattCallbackEvent.CharacteristicWrite(characteristic, status))
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                onEvent.value?.invoke(GattCallbackEvent.CharacteristicChanged(characteristic, value))
            }

            override fun onDescriptorRead(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
                value: ByteArray,
            ) {
                onEvent.value?.invoke(GattCallbackEvent.DescriptorRead(descriptor, value, status))
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
            ) {
                onEvent.value?.invoke(GattCallbackEvent.DescriptorWrite(descriptor, status))
            }

            override fun onReadRemoteRssi(
                gatt: BluetoothGatt,
                rssi: Int,
                status: Int,
            ) {
                onEvent.value?.invoke(GattCallbackEvent.ReadRemoteRssi(rssi, status))
            }

            override fun onPhyUpdate(
                gatt: BluetoothGatt,
                txPhy: Int,
                rxPhy: Int,
                status: Int,
            ) {
                onEvent.value?.invoke(GattCallbackEvent.PhyUpdated(txPhy, rxPhy, status))
            }

            override fun onPhyRead(
                gatt: BluetoothGatt,
                txPhy: Int,
                rxPhy: Int,
                status: Int,
            ) {
                onEvent.value?.invoke(GattCallbackEvent.PhyRead(txPhy, rxPhy, status))
            }
        }

    internal fun connect(options: ConnectionOptions): BluetoothGatt? {
        val transport =
            when (options.transportType) {
                TransportType.Auto -> BluetoothDevice.TRANSPORT_AUTO
                TransportType.LE -> BluetoothDevice.TRANSPORT_LE
                TransportType.BrEdr -> BluetoothDevice.TRANSPORT_BREDR
            }
        callbackThread?.quitSafely()

        val thread = HandlerThread("kmp-ble-cb/${device.address}").apply { start() }
        callbackThread = thread
        callbackHandler = Handler(thread.looper)

        gatt.update {
            device.connectGatt(
                context,
                options.autoConnect,
                gattCallback,
                transport,
                options.phyMask.value,
                callbackHandler,
            )
        }
        return gatt.value
    }

    internal fun discoverServices(): Boolean = gatt.value?.discoverServices() ?: false

    internal fun requestMtu(mtu: Int): Boolean = gatt.value?.requestMtu(mtu) ?: false

    internal fun requestConnectionPriority(priority: Int): Boolean = gatt.value?.requestConnectionPriority(priority) ?: false

    internal fun setPreferredPhy(
        txPhyMask: Int,
        rxPhyMask: Int,
        phyOptions: Int,
    ): Boolean {
        val g = gatt.value ?: return false
        g.setPreferredPhy(txPhyMask, rxPhyMask, phyOptions)
        return true
    }

    internal fun readPhy(): Boolean {
        val g = gatt.value ?: return false
        g.readPhy()
        return true
    }

    internal fun readCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean =
        gatt.value?.readCharacteristic(characteristic) ?: false

    internal fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int,
    ): Boolean {
        val g = gatt.value ?: return false
        val result = g.writeCharacteristic(characteristic, value, writeType)
        return result == BluetoothGatt.GATT_SUCCESS
    }

    internal fun readDescriptor(descriptor: BluetoothGattDescriptor): Boolean =
        gatt.value?.readDescriptor(descriptor) ?: false

    internal fun writeDescriptor(
        descriptor: BluetoothGattDescriptor,
        value: ByteArray,
    ): Boolean {
        val g = gatt.value ?: return false
        val result = g.writeDescriptor(descriptor, value)
        return result == BluetoothGatt.GATT_SUCCESS
    }

    internal fun setCharacteristicNotification(
        characteristic: BluetoothGattCharacteristic,
        enable: Boolean,
    ): Boolean = gatt.value?.setCharacteristicNotification(characteristic, enable) ?: false

    internal fun readRemoteRssi(): Boolean = gatt.value?.readRemoteRssi() ?: false

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
     * after bonding. See [com.atruedev.kmpble.quirks.BleQuirks.RefreshServicesOnBond].
     */
    internal fun refreshDeviceCache(): Boolean {
        val g = gatt.value ?: return false
        return try {
            val method = g.javaClass.getMethod("refresh")
            method.invoke(g) as? Boolean ?: false
        } catch (e: Exception) {
            logEvent(
                BleLogEvent.Error(
                    identifier = null,
                    message = "BluetoothGatt.refresh() unavailable via reflection",
                    cause = e,
                ),
            )
            false
        }
    }

    internal fun disconnect() {
        gatt.value?.disconnect()
    }

    /** Release the BluetoothGatt handle but keep the bridge reusable for reconnection. */
    internal fun releaseGatt() {
        gatt.value?.close()
        gatt.update { null }
    }

    /** Terminal - release all resources including the callback thread. */
    internal fun close() {
        gatt.value?.close()
        gatt.update { null }
        onEvent.update { null }
        callbackThread?.quitSafely()
        callbackThread = null
        callbackHandler = null
    }
}