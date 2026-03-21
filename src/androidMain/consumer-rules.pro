# kmp-ble consumer ProGuard rules

# Keep BLE callback classes (Android GATT callbacks use reflection)
-keep class io.github.garyquinn.kmpble.peripheral.AndroidGattBridge$* { *; }

# BluetoothGatt.refresh() is an internal AOSP API used via reflection to clear
# the GATT service cache after bonding on OnePlus/Xiaomi devices.
# See BleQuirks.RefreshServicesOnBond and AndroidGattBridge.refreshDeviceCache().
-keepclassmembers class android.bluetooth.BluetoothGatt {
    boolean refresh();
}

# QuirkProvider implementations are discovered via ServiceLoader
-keepnames class * implements com.atruedev.kmpble.quirks.QuirkProvider
-keep class com.atruedev.kmpble.quirks.oem.OemQuirkProvider { *; }
