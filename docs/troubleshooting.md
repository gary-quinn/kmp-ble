# Troubleshooting

Common BLE setup and runtime errors, and how to fix them.

## Scan produces no results

### Android

1. **Bluetooth is off.** Check `BluetoothAdapter().state`. Prompt user to
   enable Bluetooth in system settings.
2. **Permissions not granted.** Call `checkBlePermissions()`. On API 31+,
   require `BLUETOOTH_SCAN`. On API 30 and below, require
   `ACCESS_FINE_LOCATION`.
3. **Location is disabled (API 30 and below).** On older Android versions,
   the Location toggle must be ON in system settings. kmp-ble attempts
   a `NeverForLocation` scan automatically to work around this on most
   devices.
4. **Scan timeout too short.** Increase the timeout in the `Scanner` builder:
   ```kotlin
   val scanner = Scanner { timeout = 60.seconds }
   ```
5. **Wi-Fi scanning throttling (Android 8+).** Android limits background
   scans to one every 30 minutes. Use a foreground service for
   long-running scans.

### iOS

1. **Simulator.** BLE is not available on the iOS simulator. Test on a
   physical device.
2. **Bluetooth permission denied.** Check Settings > Privacy > Bluetooth.
   Your app must be listed and enabled.
3. **Background scan throttling.** iOS rate-limits background scan results.
   Move the app to the foreground for faster discovery.
4. **Bluetooth is off.** Toggle Bluetooth off/on in Control Center or
   Settings.

## Connection fails with timeout

1. **Device is out of range.** Move closer. BLE range is typically 10-30
   meters but varies by environment.
2. **Device is already connected to another central.** Many BLE peripherals
   accept only one connection. Disconnect from other apps first.
3. **Bluetooth stack is in a bad state.** Toggle Bluetooth off/on on the
   phone, or restart the device.
4. **Incorrect connection parameters.** Try `ConnectionRecipe.IOT` for
   devices that are picky about connection intervals.
5. **Android: `BluetoothDevice.connectGatt()` returns true but never
   connects.** Use `ConnectionOptions(connectionTimeout = 10.seconds)`.

## GATT error 133 / 0x85 (Android)

GATT error 133 is Android's generic "something went wrong" status. Common
causes:

1. **Peripheral disconnected during an operation.** Handle the disconnect and
   retry. kmp-ble's reconnection strategy handles this automatically if
   configured: `ConnectionOptions(reconnectionStrategy = ...)`.
2. **Too many concurrent GATT operations.** Each `Peripheral` serializes
   operations internally. Do not share a `Peripheral` across heavy
   concurrent workloads.
3. **Bluetooth stack overflow.** Samsung devices are particularly prone.
   Add a small delay between operations (e.g., 50ms) or create a fresh
   `Peripheral` instance.
4. **Bonding state mismatch.** If bonds were removed from system settings
   but the peripheral still expects a bond, clear the bond on both sides.

## GATT error 257 (iOS)

On iOS, error 257 means the connection timed out. Common causes:

1. **Peripheral went out of range.**
2. **Peripheral stopped advertising.**
3. **Peripheral refused the connection** (e.g., already connected to
   another device).

kmp-ble surfaces this as `ConnectionLost(cause = ConnectionError.Timeout)`.

## `BluetoothAdapterState.Unauthorized`

### Android

The user denied the `BLUETOOTH_CONNECT` permission. Call
`checkBlePermissions()` and direct them to system settings if
`PermanentlyDenied`.

### iOS

The user denied Bluetooth access in the system dialog. They must go to
**Settings > Privacy > Bluetooth** and enable your app manually.

## `SecurityException: Need BLUETOOTH_CONNECT permission`

Your app is missing the `BLUETOOTH_CONNECT` permission in the manifest
or at runtime (Android 12+).

**Fix:** Add to manifest:
```xml
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```
And request at runtime before calling `peripheral.connect()`.

## `CBCentralManager.state == .unsupported` (iOS)

The device does not support BLE. This happens on:
- iOS Simulator
- iPad 2 and older
- iPod touch 4th gen and older

Check `BluetoothAdapter().state` and handle `BluetoothAdapterState.Unsupported`
gracefully.

## Services discovery returns empty

1. **Peripheral needs bond before service discovery.** Some peripherals hide
   services until bonded. Use `ConnectionOptions(bondingPreference =
   BondingPreference.Required)`.
2. **iOS caching stale services.** CoreBluetooth caches discovered services.
   Toggle Bluetooth off/on on the iOS device to clear the cache.
3. **Android: Samsung cached services.** Samsung devices aggressively cache
   GATT services. kmp-ble's quirks subsystem detects Samsung devices and
   calls `BluetoothGatt.refresh()` automatically. If services are still
   missing, try disconnecting and reconnecting.

## `CancellationException` is silently swallowed

kmp-ble follows structured concurrency rules. CancellationException is
always rethrown. If you see behavior that suggests it is not, ensure your
coroutine scope is not catching it:

```kotlin
// WRONG -- swallows cancellation
try {
    peripheral.observeValues(char).collect { ... }
} catch (e: Exception) {
    // CancellationException extends Exception in Kotlin
    // This catch block swallows it
}

// CORRECT
try {
    peripheral.observeValues(char).collect { ... }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    // Handle real errors
}
```

## Observe stops emitting

1. **Peripheral disconnected.** Check `peripheral.connectionState`.
   Observations are scoped to the connection. Re-subscribe after
   reconnecting.
2. **CCCD was not written.** Verify the characteristic has `notify` or
   `indicate` in its properties. kmp-ble writes the CCCD automatically
   on `observe()`.
3. **Android: background service killed.** Android may kill background
   processes. Use a foreground service for long-running observations.

## Build error: `Unresolved reference: KmpBle`

The kmp-ble framework is not linked. For iOS via SPM, ensure the package
is added in Xcode and `KmpBle` is listed under "Frameworks, Libraries, and
Embedded Content."

## Build error: `minSdk 33 expected`

kmp-ble requires Android API 33+. Set `minSdk = 33` in your module's
`build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        minSdk = 33
    }
}
```

## `IllegalStateException: BT Adapter not present`

The device does not have Bluetooth hardware. Check
`BluetoothAdapter().state` for `BluetoothAdapterState.Unsupported` before
attempting BLE operations.
