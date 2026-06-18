package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.bleDataFromNSData
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.StaleGattHandle
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.gatt.Descriptor
import com.atruedev.kmpble.gatt.internal.NotConnectedException
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBDescriptor
import platform.Foundation.NSData
import kotlin.time.Duration.Companion.seconds

/**
 * Internal helpers for [IosPeripheral] extracted to keep the facade under 300 lines.
 */

internal fun IosPeripheral.checkNotClosed() {
    check(!closed) { "Peripheral is closed" }
}

internal fun IosPeripheral.requireNativeCbChar(c: Characteristic): CBCharacteristic =
    nativeCharMap[c]
        ?: throw BleException(StaleGattHandle("characteristic", c.uuid.toString()))

internal fun IosPeripheral.requireNativeCbDesc(d: Descriptor): CBDescriptor =
    nativeDescMap[d] ?: throw BleException(StaleGattHandle("descriptor", d.uuid.toString()))

internal fun IosPeripheral.onDisconnectCleanup() {
    nativeCharMap.clear()
    nativeDescMap.clear()
    closeL2capChannels()
    observationManager.onDisconnect()
    pendingOps.cancelAll(NotConnectedException())
}

internal fun ByteArray.toNSData(): NSData = BleData(this).nsData

internal fun NSData.toByteArray(): ByteArray = bleDataFromNSData(this).toByteArray()

internal val L2CAP_OPEN_TIMEOUT = 30.seconds
internal val DISCONNECT_TIMEOUT = 5.seconds
internal val DISCOVERY_TIMEOUT = 10.seconds
internal const val ATT_HEADER_SIZE = 3
