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
import com.atruedev.kmpble.connection.ConnectionPriority
import com.atruedev.kmpble.connection.TransportType
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import kotlinx.atomicfu.atomic

internal class AndroidGattBridge(
    private val device: BluetoothDevice,
    private val context: Context,
) {
    private var callbackThread: HandlerThread? = null
    private var callbackHandler: Handler? = null

    private val _gatt = atomic<BluetoothGatt?>(null)

    private val _onEvent = atomic<((GattCallbackEvent) -> Unit)?>(null)
    internal var onEvent: ((GattCallbackEvent) -> Unit)?
        get() = _onEvent.value
        set(value) {
            _onEvent.value = value
        }

    private val gattCallback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                onEvent?.invoke(GattCallbackEvent.ConnectionStateChanged(status, newState))
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int,
            ) {
                onEvent?.invoke(GattCallbackEvent.ServicesDiscovered(status, gatt.services.orEmpty()))
            }

            override fun onMtuChanged(
                gatt: BluetoothGatt,
                mtu: Int,
                status: Int,
            ) {
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

            override fun onReadRemoteRssi(
                gatt: BluetoothGatt,
                rssi: Int,
                status: Int,
            ) {
                onEvent?.invoke(GattCallbackEvent.ReadRemoteRssi(rssi, status))
            }

            override fun onPhyUpdate(
                gatt: BluetoothGatt,
                txPhy: Int,
                rxPhy: Int,
                status: Int,
            ) {
                onEvent?.invoke(GattCallbackEvent.PhyUpdated(txPhy, rxPhy, status))
            }

            override fun onPhyRead(
                gatt: BluetoothGatt,
                txPhy: Int,
                rxPhy: Int,
                status: Int,
            ) {
                onEvent?.invoke(GattCallbackEvent.PhyRead(txPhy, rxPhy, status))
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

        _gatt.value =
            device.connectGatt(
                context,
                options.autoConnect,
                gattCallback,
                transport,
                options.phyMask.value,
                callbackHandler,
            )
        // Request connection priority immediately so the initial link-layer
        // negotiation uses the caller's preferred interval, not the platform
        // default (~30-50ms). This reduces time-to-connected when callers
        // opt into ConnectionPriority.High for scan-then-connect flows.
        val androidPriority =
            when (options.connectionPriority) {
                ConnectionPriority.Balanced -> BluetoothGatt.CONNECTION_PRIORITY_BALANCED
                ConnectionPriority.High -> BluetoothGatt.CONNECTION_PRIORITY_HIGH
                ConnectionPriority.LowPower -> BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER
            }
        val priorityApplied = _gatt.value?.requestConnectionPriority(androidPriority) ?: false
        if (!priorityApplied) {
            logEvent(
                BleLogEvent.Warning(
                    identifier = null,
                    message =
                        "requestConnectionPriority(${options.connectionPriority}) " +
                            "returned false - priority not applied",
                ),
            )
        }
        return _gatt.value
    }

    internal fun discoverServices(): Boolean = _gatt.value?.discoverServices() ?: false

    internal fun requestMtu(mtu: Int): Boolean = _gatt.value?.requestMtu(mtu) ?: false

    internal fun requestConnectionPriority(priority: Int): Boolean =
        _gatt.value?.requestConnectionPriority(priority) ?: false

    internal fun setPreferredPhy(
        txPhyMask: Int,
        rxPhyMask: Int,
        phyOptions: Int,
    ): Boolean {
        val g = _gatt.value ?: return false
        g.setPreferredPhy(txPhyMask, rxPhyMask, phyOptions)
        return true
    }

    internal fun readPhy(): Boolean {
        val g = _gatt.value ?: return false
        g.readPhy()
        return true
    }

    internal fun readCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean =
        _gatt.value?.readCharacteristic(characteristic) ?: false

    internal fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int,
    ): Boolean {
        val g = _gatt.value ?: return false
        val result = g.writeCharacteristic(characteristic, value, writeType)
        return result == BluetoothGatt.GATT_SUCCESS
    }

    internal fun readDescriptor(descriptor: BluetoothGattDescriptor): Boolean =
        _gatt.value?.readDescriptor(descriptor) ?: false

    internal fun writeDescriptor(
        descriptor: BluetoothGattDescriptor,
        value: ByteArray,
    ): Boolean {
        val g = _gatt.value ?: return false
        val result = g.writeDescriptor(descriptor, value)
        return result == BluetoothGatt.GATT_SUCCESS
    }

    internal fun setCharacteristicNotification(
        characteristic: BluetoothGattCharacteristic,
        enable: Boolean,
    ): Boolean = _gatt.value?.setCharacteristicNotification(characteristic, enable) ?: false

    internal fun readRemoteRssi(): Boolean = _gatt.value?.readRemoteRssi() ?: false

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
        val g = _gatt.value ?: return false
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
        _gatt.value?.disconnect()
    }

    /** Release the BluetoothGatt handle but keep the bridge reusable for reconnection. */
    internal fun releaseGatt() {
        _gatt.value?.close()
        _gatt.value = null
    }

    /** Terminal - release all resources including the callback thread. */
    internal fun close() {
        _gatt.value?.close()
        _gatt.value = null
        onEvent = null
        callbackThread?.quitSafely()
        callbackThread = null
        callbackHandler = null
    }
}
