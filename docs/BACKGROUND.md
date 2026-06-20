# Module BACKGROUND

Background BLE operation patterns for iOS and Android using kmp-ble. Production apps must handle platform-specific background constraints to maintain connections, receive notifications, and scan for devices while the app is not in the foreground.

## Table of Contents

- [Android Background BLE](#android-background-ble)
  - [Foreground Service](#foreground-service)
  - [Permissions and Manifest](#permissions-and-manifest)
  - [Scan Throttling Workarounds](#scan-throttling-workarounds)
  - [Battery Optimization](#battery-optimization)
  - [Companion Device Manager](#companion-device-manager)
- [iOS Background BLE](#ios-background-ble)
  - [Background Modes](#background-modes)
  - [State Preservation and Restoration](#state-preservation-and-restoration)
  - [Wake Budget Management](#wake-budget-management)
  - [Background Scanning Restrictions](#background-scanning-restrictions)
- [kmp-ble Background Patterns](#kmp-ble-background-patterns)
  - [Scanner with Background Configuration](#scanner-with-background-configuration)
  - [ReconnectionHandler in Background Context](#reconnectionhandler-in-background-context)
  - [ConnectionQualityMonitor for Connection Health](#connectionqualitymonitor-for-connection-health)
  - [State Restoration Integration](#state-restoration-integration)
- [Common Pitfalls](#common-pitfalls)

## Android Background BLE

Android imposes increasingly strict limits on background BLE operations. Apps targeting modern Android must use foreground services for long-running BLE work.

### Foreground Service

Any BLE scan or connection that outlasts the activity lifecycle requires a foreground service. Without one, Android 8+ terminates the app's BLE operations within minutes of background entry.

```kotlin
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

class BleForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BLE Service",
                NotificationManager.IMPORTANCE_LOW,
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("BLE Active")
                .setContentText("Maintaining BLE connection...")
                .setSmallIcon(R.drawable.ic_bluetooth)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("BLE Active")
                .setContentText("Maintaining BLE connection...")
                .setSmallIcon(R.drawable.ic_bluetooth)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }
    }

    companion object {
        private const val CHANNEL_ID = "ble_service_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
```

Declare the service and foreground service type in your manifest:

```xml
<service
    android:name=".BleForegroundService"
    android:foregroundServiceType="connectedDevice"
    android:exported="false" />
```

On Android 14+, `connectedDevice` foreground service type requires the `FOREGROUND_SERVICE_CONNECTED_DEVICE` permission:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
```

For scanning-intensive apps, also declare `dataSync`:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<service
    android:name=".BleForegroundService"
    android:foregroundServiceType="connectedDevice|dataSync"
    android:exported="false" />
```

### Permissions and Manifest

The complete manifest for background BLE on Android:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Core BLE (API 31+) -->
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

    <!-- Foreground service (API 34+) -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

    <!-- Hardware -->
    <uses-feature
        android:name="android.hardware.bluetooth_le"
        android:required="false" />

    <application ...>
        <service
            android:name=".BleForegroundService"
            android:foregroundServiceType="connectedDevice|dataSync"
            android:exported="false" />
    </application>
</manifest>
```

Important: `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` are runtime permissions on API 31+. Request them before starting the foreground service:

```kotlin
when (val result = checkBlePermissions()) {
    is PermissionResult.Granted -> startBleService()
    is PermissionResult.Denied -> requestPermissions(result.permissions)
    is PermissionResult.PermanentlyDenied -> showSettingsPrompt()
}
```

### Scan Throttling Workarounds

Android 8+ (API 26+) throttles background BLE scans: no more than ~30 seconds of cumulative scan time per 30-minute window. The throttling applies even with a foreground service if `SCAN_MODE_LOW_POWER` is used.

**Use SCAN_MODE_LOW_LATENCY**: kmp-ble's `AndroidScanner` uses `SCAN_MODE_LOW_LATENCY` by default, which avoids the throttle. This mode consumes more power but is the only way to guarantee continuous scan results in the foreground service context.

```kotlin
// AndroidScanner already uses SCAN_MODE_LOW_LATENCY internally.
// No extra configuration needed -- just keep the foreground service alive.
val scanner = AndroidScanner(context) {
    timeout = null // unbounded scan, stopped manually or by service teardown
    emission = EmissionPolicy.FirstThenChanges(rssiThreshold = 5)
}

// In your foreground service:
coroutineScope.launch {
    scanner.scanEvents.collect { event ->
        when (event) {
            is ScanEvent.Found -> handleDeviceFound(event)
            is ScanEvent.Lost -> handleDeviceLost(event)
            is ScanEvent.Updated -> handleDeviceUpdated(event)
        }
    }
}
```

**Staggered scanning**: If battery consumption is a concern, scan in intervals:

```kotlin
private suspend fun staggeredScan(scanner: Scanner, scanDuration: Duration, pauseDuration: Duration) {
    while (isActive) {
        val scanJob = scope.launch {
            scanner.scanEvents.collect { /* handle */ }
        }
        delay(scanDuration)
        scanJob.cancel()
        delay(pauseDuration)
        // Create a new Scanner or reuse -- AndroidScanner.close() cleans up cleanly
    }
}
```

**Permanent background scanning (Android 12+)**: If your app needs to detect devices at all times (e.g., medical alert systems), request exemption from the background scan throttle via the system settings. Direct users to Settings > Apps > Your App > Battery > Unrestricted. There is no programmatic workaround.

### Battery Optimization

Android's Doze mode (API 23+) and App Standby Buckets (API 28+) further restrict background BLE:

- **Doze**: While the device is stationary and unplugged, network access is deferred and BLE scans are blocked entirely. Foreground services are exempt from Doze while the service is running.
- **App Standby Buckets**: Apps in the "rare" bucket are limited to one BLE scan per day. Active apps (frequently used) get normal scan access.

Request battery optimization exemption for critical BLE use cases:

```kotlin
import android.os.PowerManager
import android.provider.Settings

fun requestBatteryExemption(context: Context) {
    val powerManager = context.getSystemService(PowerManager::class.java)
    if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }
}
```

Only request exemption for medical, accessibility, or safety-critical apps. Google Play may reject apps that request this without a valid use case. For most apps, a foreground service is sufficient.

### Companion Device Manager

Android 12+ offers the Companion Device Manager API for persistent BLE connections that survive app restarts and reboots. The system handles reconnection transparently:

```kotlin
import android.companion.CompanionDeviceManager
import android.companion.BluetoothDeviceFilter
import android.companion.AssociationRequest

fun requestCompanionDevice(activity: Activity) {
    val deviceFilter = BluetoothDeviceFilter.Builder()
        .setNamePattern(Pattern.compile("MySensor.*"))
        .build()

    val pairingRequest = AssociationRequest.Builder()
        .addDeviceFilter(deviceFilter)
        .setSingleDevice(true)
        .build()

    val manager = activity.getSystemService(CompanionDeviceManager::class.java)
    manager.associate(
        pairingRequest,
        { result -> /* companionDevice = result */ },
        null,
    )
}
```

After association, when the system detects the companion device, it sends an `ACTION_FOUND` broadcast that wakes your app. Use kmp-ble's `Peripheral.connect()` once woken -- the system has already established the Bluetooth connection backdrop.

## iOS Background BLE

iOS handles background BLE through background modes and state restoration. The system may terminate your app to free memory but can re-launch it when a BLE event occurs if state restoration is configured.

### Background Modes

Add to your app's Info.plist. For central role (scanning, connecting):

```xml
<key>UIBackgroundModes</key>
<array>
    <string>bluetooth-central</string>
</array>
```

For peripheral role (advertising, GATT server):

```xml
<key>UIBackgroundModes</key>
<array>
    <string>bluetooth-peripheral</string>
</array>
```

For both roles:

```xml
<key>UIBackgroundModes</key>
<array>
    <string>bluetooth-central</string>
    <string>bluetooth-peripheral</string>
</array>
```

Also declare the corresponding entitlement. Xcode usually adds it automatically when you enable background modes in the Signing & Capabilities tab. Verify your entitlements file contains:

```xml
<key>com.apple.developer.bluetooth-central</key>
<true/>
```

Without `bluetooth-central`, CoreBluetooth operations fail silently in the background. Without `bluetooth-peripheral`, advertising and GATT server operations stop when the app is suspended.

### State Preservation and Restoration

iOS may terminate your app to free memory even while BLE operations are in progress. State restoration allows the system to re-launch your app and restore BLE state when a relevant event occurs.

Enable state restoration in kmp-ble:

```kotlin
import com.atruedev.kmpble.KmpBle
import com.atruedev.kmpble.connection.StateRestorationConfig

// Call once, before any BLE operations
KmpBle.enableStateRestoration(
    StateRestorationConfig(identifier = "com.myapp.ble.central")
)
```

The `identifier` string must be consistent across app launches. Use a unique reverse-DNS identifier that iOS uses to associate the restored CBCentralManager with your app.

When the system relaunches your app after a BLE event (e.g., a connected peripheral sends a notification), kmp-ble automatically:

1. Restores the `CBCentralManager` with previously connected peripherals
2. Re-subscribes to observation characteristics
3. Restores pending GATT operations where possible

Your app must re-establish its UI observers:

```kotlin
// In your app's init or didFinishLaunching
peripheral.state.collect { state ->
    when (state) {
        is State.Connected.Ready -> {
            // Re-subscribe to characteristics after restoration
            launch {
                peripheral.observe(char, BackpressureStrategy.Unbounded).collect { ... }
            }
        }
        else -> { /* handle other states */ }
    }
}
```

**What state restoration preserves**:
- Connected peripheral identifiers
- Subscribed characteristics (CCCD state)
- Pending read/write operations (best-effort)

**What state restoration does NOT preserve**:
- Custom application state (save to UserDefaults/DataStore separately)
- Scan state (scans are not restored; restart them in your init)
- Bond/pairing state (iOS manages this independently via the OS keychain)

### Wake Budget Management

iOS allocates a limited "wake budget" (~10 seconds cumulative per event) for background BLE processing. When the budget is exhausted, iOS suspends your app.

**Critical rules**:
- Process BLE events quickly (< 1 second per event)
- Do NOT perform network calls, database writes, or UI updates in the BLE callback thread
- Offload heavy work to a background queue

kmp-ble handles CoreBluetooth delegate callbacks on a dedicated dispatch queue. Heavy processing in your coroutine collectors will not block the delegate, but iOS tracks total CPU time across all threads:

```kotlin
peripheral.observe(char, BackpressureStrategy.Unbounded)
    .collect { observation ->
        // Do minimal work here -- save to local buffer
        buffer.add(observation)
    }

// Deferred processing in a separate coroutine:
launch {
    while (isActive) {
        delay(1.seconds)
        flushBufferToDisk(buffer)
    }
}
```

**Wake budget exhaustion symptoms**:
- App stops receiving BLE events after ~10 seconds in background
- `CBCentralManager` state transitions to `.poweredOff` (system suspends it)
- No crash log -- iOS suspends, not crashes

### Background Scanning Restrictions

iOS imposes significant restrictions on background scanning:

1. **Service UUIDs required**: Wildcard scanning (no service filter) does not work in the background. You must specify at least one service UUID:

```kotlin
val scanner = IosScanner {
    filters {
        match { serviceUuid("180D") } // Heart Rate Service -- required for bg scan
    }
}
```

2. **Reduced scan frequency**: Background scans deliver results at approximately 1/10th the foreground rate. A device that advertises every 100ms may only be discovered every 1-2 seconds in the background.

3. **Duplicate filtering**: iOS automatically deduplicates scan results. Use `EmissionPolicy.Every` in the foreground only -- background scans are always deduplicated by the OS.

4. **Connected peripherals**: iOS auto-connects to bonded peripherals in the background, and `IosScanner` emits these via `retrieveConnectedPeripheralsWithServices`. This means bonded peripherals are always "visible" in the background via their service UUIDs.

## kmp-ble Background Patterns

This section covers kmp-ble-specific patterns for background BLE operations. Combine these with the platform-specific setup above.

### Scanner with Background Configuration

Configure the Scanner for background-appropriate behavior:

```kotlin
// Android: use within a foreground service
val scanner = AndroidScanner(context) {
    timeout = null // managed by service lifecycle
    emission = EmissionPolicy.FirstThenChanges(rssiThreshold = 10)
    legacyOnly = false // allow extended advertisements
    phy = ScanPhy.LeCoded // long-range for better background discovery
    filters {
        match { serviceUuid("180D") } // required for iOS bg, recommended for Android
    }
}

// iOS: service UUID filter is mandatory in background
val scanner = IosScanner {
    emission = EmissionPolicy.FirstThenChanges(rssiThreshold = 10)
    filters {
        match { serviceUuid("180D") }
    }
}
```

**Scanner lifecycle in background**: On both platforms, close the scanner when the background session ends. Android foreground services call `scanner.close()` in `onDestroy()`. iOS apps close the scanner when entering suspended state (though iOS typically terminates background scans automatically).

### ReconnectionHandler in Background Context

The `ReconnectionHandler` (internal, driven by `ReconnectionStrategy` on `ConnectionOptions`) works transparently in the background -- it's scoped to the peripheral's coroutine scope, which outlives the UI:

```kotlin
// Medical device: aggressive reconnection in background
peripheral.connect(
    ConnectionRecipe.MEDICAL.copy(
        reconnectionStrategy = ReconnectionStrategy.ExponentialBackoff(
            initialDelay = 1.seconds,
            maxDelay = 30.seconds,
            maxAttempts = Int.MAX_VALUE, // never stop trying
        ),
    )
)
```

**Android caveat**: If the foreground service is killed (system memory pressure, force-stop), reconnection stops. The foreground service's `START_STICKY` flag restarts the service, but you must re-initiate BLE connections.

**iOS caveat**: If state restoration is enabled, the system wakes your app on disconnect. Re-subscribe to `peripheral.state` in your restoration handler to re-establish reconnection logic.

### ConnectionQualityMonitor for Connection Health

The `ConnectionQualityMonitor` tracks connection state transitions even in the background:

```kotlin
import com.atruedev.kmpble.monitoring.ConnectionQualityMonitor

val monitor = ConnectionQualityMonitor(peripheral, backgroundScope)
monitor.start()

monitor.connectionQuality.collect { quality ->
    when {
        quality.totalDisconnections > 5 -> {
            // Connection is flaky -- notify user or switch to lower power mode
            logWarning("BLE connection unstable: ${quality.totalDisconnections} drops")
        }
        quality.isConnected && quality.lastRssi < -90 -> {
            // Signal is very weak -- device may be moving out of range
        }
    }
}
```

In background contexts, combine with RSSI polling for signal strength awareness. The monitor's `recordRssi()` method feeds RSSI readings into the quality snapshot. On Android, call `peripheral.readRssi()` periodically (every 5-10 seconds) to feed the monitor. On iOS, RSSI is reported automatically with each advertisement/discovery event.

### State Restoration Integration

Combine state restoration with reconnection for resilient background BLE:

```kotlin
// App initialization
KmpBle.enableStateRestoration(
    StateRestorationConfig(identifier = "com.myapp.ble")
)

// After restoration, re-establish monitoring
fun onPeripheralRestored(peripheral: Peripheral) {
    // Check current state -- may already be connected
    when (peripheral.state.value) {
        is State.Connected -> {
            reestablishObservers(peripheral)
        }
        is State.Disconnected -> {
            // Initiate reconnection with backoff
            peripheral.connect(
                ConnectionOptions(
                    reconnectionStrategy = ReconnectionStrategy.ExponentialBackoff(
                        maxAttempts = 5,
                    ),
                )
            )
        }
        else -> { /* Connecting -- wait for Connected state */ }
    }
}

private fun reestablishObservers(peripheral: Peripheral) {
    // kmp-ble auto-restores CCCD subscriptions, but your collectors
    // must be re-attached:
    scope.launch {
        peripheral.state.collect { state ->
            when (state) {
                is State.Connected.Ready -> startObservation(peripheral)
                is State.Disconnected.ByError -> onConnectionLost(peripheral)
                else -> {}
            }
        }
    }
}
```

## Common Pitfalls

### Android

| Pitfall | Symptom | Solution |
|---------|---------|----------|
| No foreground service | BLE stops within 1-2 minutes of background | Wrap BLE operations in a `Service` with `startForeground()` |
| Missing `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Service fails to start on Android 14+ | Add the permission to manifest |
| Background scan throttle | Scan results stop after ~30s | Use `SCAN_MODE_LOW_LATENCY` (kmp-ble default) |
| Doze mode | No BLE events when device is idle | Foreground service exempts from Doze; battery exemption for critical apps |
| `BLUETOOTH_SCAN` not granted at runtime | `AndroidScanner` throws `SecurityException` | Check permissions with `checkBlePermissions()` before scanning |
| Samsung GATT cache | Stale service/characteristic data | kmp-ble's quirks subsystem handles this; test on Samsung hardware |
| Manufacturer-specific process killing | BLE service terminated during background | Xiaomi/Huawei: user must add app to "protected apps" in device settings |

### iOS

| Pitfall | Symptom | Solution |
|---------|---------|----------|
| No `bluetooth-central` background mode | Events stop when app backgrounds | Add to Info.plist UIBackgroundModes |
| Wildcard scan in background | No scan results while backgrounded | Specify service UUIDs in scan filter |
| Wake budget exhaustion | App stops receiving events after ~10s | Defer heavy work; process events in < 1s |
| No state restoration | App not relaunched after BLE event | Call `KmpBle.enableStateRestoration()` with unique identifier |
| Missing entitlement | `bluetooth-central` mode not honored | Verify `com.apple.developer.bluetooth-central` in entitlements file |
| CoreBluetooth cache | Stale GATT services after peripheral firmware update | Toggle Bluetooth off/on on iOS device to clear; no programmatic API |
| Simulator testing | BLE operations silently fail | Always test on physical iOS device; simulator has no BLE hardware |

### Cross-Platform

| Pitfall | Symptom | Solution |
|---------|---------|----------|
| Expecting identical bg behavior | Different scan rates, wake times | Design for lowest common denominator; test on both platforms |
| No timeout on background scans | Battery drain on Android, wake budget waste on iOS | Always set `ScannerConfig.timeout` for finite scans |
| Heavy collectors in observation flow | iOS wake budget exhausted; Android ANR risk | Buffer observations; flush in periodic batched writes |
| Not handling `State.Disconnected.ByRequest` vs `ByError` | Reconnection fires on manual disconnect | `ReconnectionHandler` already handles this -- no action needed |
| Multiple Scanner instances in background | Duplicate scan on Android; undefined iOS behavior | One scanner per background session; close before creating a new one |

## Minimum OS Versions

- **Android**: API 33 (Android 13) for `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` runtime permissions. API 34 (Android 14) for foreground service type declarations. Some background features (Companion Device Manager) require API 31+ (Android 12).
- **iOS**: iOS 15.0+ for CoreBluetooth background execution. State restoration is available on all iOS versions with CoreBluetooth (iOS 5+), but modern background modes require iOS 13+.
