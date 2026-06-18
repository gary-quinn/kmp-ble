@file:SuppressLint("MissingPermission")

package com.atruedev.kmpble.server

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic

/** Maps [ServerCharacteristic.Properties] to Android property flags. */
internal fun ServerCharacteristic.Properties.toAndroidProperties(): Int {
    var flags = 0
    if (read) flags = flags or BluetoothGattCharacteristic.PROPERTY_READ
    if (write) flags = flags or BluetoothGattCharacteristic.PROPERTY_WRITE
    if (writeWithoutResponse) flags = flags or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
    if (notify) flags = flags or BluetoothGattCharacteristic.PROPERTY_NOTIFY
    if (indicate) flags = flags or BluetoothGattCharacteristic.PROPERTY_INDICATE
    return flags
}

/** Maps [ServerCharacteristic.Permissions] to Android permission flags. */
internal fun ServerCharacteristic.Permissions.toAndroidPermissions(): Int {
    var flags = 0
    if (read) flags = flags or BluetoothGattCharacteristic.PERMISSION_READ
    if (readEncrypted) flags = flags or BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
    if (write) flags = flags or BluetoothGattCharacteristic.PERMISSION_WRITE
    if (writeEncrypted) flags = flags or BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
    return flags
}
