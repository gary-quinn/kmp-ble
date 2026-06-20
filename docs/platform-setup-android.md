# Platform Setup: Android

This guide covers the Gradle and manifest configuration required to use kmp-ble
on Android.

## Requirements

- Android minSdk 33 (Android 13)
- Kotlin 2.3.0+
- kotlinx-coroutines 1.10+

## Gradle Setup

```kotlin
// build.gradle.kts (module)
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.atruedev:kmp-ble:0.8.5")
        }
    }
}
```

## Library Initialization

Initialize the library once, typically in `Application.onCreate()`:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        KmpBle.init(this)
    }
}
```

Register in your manifest:

```xml
<application
    android:name=".MyApp"
    ...>
</application>
```

The library also ships an `InitializationProvider` via AndroidX Startup.
If you use that path, no manual `KmpBle.init()` call is needed.

## Manifest Permissions

### Core BLE Permissions (API 31+)

On Android 12 (API 31) and above, BLE permissions are separate from location:

```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

- **BLUETOOTH_SCAN** -- Required for `Scanner` to discover nearby devices.
- **BLUETOOTH_CONNECT** -- Required for `Peripheral.connect()`, read/write,
  and observe operations.

### Advertising Permission (API 31+)

If your app advertises via `Advertiser` or `ExtendedAdvertiser`:

```xml
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
```

### Location Permissions (API 30 and below)

On Android 11 (API 30) and below, BLE scanning requires location permission:

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

For apps with `minSdk 33`, location permissions are not needed for BLE.
Include `android:maxSdkVersion="30"` to limit them to older API levels:

```xml
<uses-permission
    android:name="android.permission.ACCESS_FINE_LOCATION"
    android:maxSdkVersion="30" />
<uses-permission
    android:name="android.permission.ACCESS_COARSE_LOCATION"
    android:maxSdkVersion="30" />
```

### Location Services Requirement (API 30 and below)

On Android 11 and below, BLE scanning only works when **Location** is enabled
in system settings (GPS does not need to be active -- only the Location toggle).

kmp-ble mitigates this: on API < 31, a `NeverForLocation` scan is attempted
first. If it fails, the scanner automatically falls back to requesting
location-enabled scanning. This means most apps do not need to handle this
manually. However, if your users report scan failures on older devices, tell
them to enable Location in system settings.

### Hardware Feature Declaration

Declare BLE hardware as optional so your app remains installable on devices
without BLE (e.g., Android TV, some tablets):

```xml
<uses-feature
    android:name="android.hardware.bluetooth_le"
    android:required="false" />
```

Use `required="true"` only if your app cannot function without BLE.

### Complete Manifest Snippet

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- BLE (API 31+) -->
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />

    <!-- Location (API 30 and below) -->
    <uses-permission
        android:name="android.permission.ACCESS_FINE_LOCATION"
        android:maxSdkVersion="30" />
    <uses-permission
        android:name="android.permission.ACCESS_COARSE_LOCATION"
        android:maxSdkVersion="30" />

    <uses-feature
        android:name="android.hardware.bluetooth_le"
        android:required="false" />

    <application ...>
        ...
    </application>
</manifest>
```

## Runtime Permission Flow

On Android 12+, BLE permissions are runtime permissions. Use the library's
built-in permission check:

```kotlin
when (val result = checkBlePermissions()) {
    is PermissionResult.Granted -> {
        // Start scanning or connect
    }
    is PermissionResult.Denied -> {
        // Request permissions via your preferred method
        // (ActivityResultContracts, Accompanist, etc.)
    }
    is PermissionResult.PermanentlyDenied -> {
        // Direct user to Settings > Apps > Your App > Permissions
    }
}
```

The specific permissions to request at runtime:

```kotlin
// API 31+
arrayOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT,
)

// API 30 and below
arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
)
```

## Bluetooth Adapter State

Before scanning, check that Bluetooth is enabled:

```kotlin
val adapter = BluetoothAdapter()
adapter.state.collect { state ->
    when (state) {
        BluetoothAdapterState.On -> { /* ready */ }
        BluetoothAdapterState.Off -> { /* prompt user to enable */ }
        BluetoothAdapterState.Unauthorized -> { /* permission denied */ }
        else -> {}
    }
}
```

## Known Android Limitations

- **Samsung devices** may return cached GATT services. Disconnect and
  reconnect, or call `BluetoothGatt.refresh()` to force rediscovery.
  kmp-ble handles this via the quirks subsystem.
- **Xiaomi/Huawei** devices sometimes kill BLE scans after 5 minutes.
  Restart the scan if you need longer discovery windows.
- Background BLE scanning on Android 8+ (API 26+) is throttled by the OS.
  Use a foreground service for long-running scans.
- On some devices, `BluetoothGatt.connect()` returns `true` but the
  connection never completes. Always use a connection timeout.
