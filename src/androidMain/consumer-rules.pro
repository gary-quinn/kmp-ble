# kmp-ble consumer ProGuard rules

# Keep BLE callback classes (Android GATT callbacks use reflection)
-keep class io.github.garyquinn.kmpble.peripheral.AndroidGattBridge$* { *; }

# BluetoothGatt.refresh() is an internal AOSP API used via reflection to clear
# the GATT service cache after bonding on OnePlus/Xiaomi devices.
# See DeviceQuirks.shouldRefreshServicesOnBond() and AndroidGattBridge.refreshDeviceCache().
-keepclassmembers class android.bluetooth.BluetoothGatt {
    boolean refresh();
}
