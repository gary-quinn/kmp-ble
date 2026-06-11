# Getting Started in 5 Minutes

This guide walks you through adding kmp-ble to a new project, scanning for a device, and blinking an LED over BLE — all in ~5 minutes.

## Prerequisites

- **Kotlin 2.3.0+** (for KMP 2.4+ features)
- **Android** minSdk 33+, compileSdk 36
- **iOS** 15+ (SPM)
- Physical BLE device (e.g., Nordic Thingy:53, ESP32, Arduino Nano 33 BLE, or any BLE LED peripheral)

---

## Option A: Android / KMP (Gradle)

### 1. Add Dependency

In `settings.gradle.kts` (if not already present):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

In your **common/shared module** `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation("com.atruedev:kmp-ble:0.8.1")
            // Optional: for type-safe standard profiles (Heart Rate, Battery, etc.)
            implementation("com.atruedev:kmp-ble-profiles:0.8.1")
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }
    }
}
```

> **Note**: If you're in a single-module Android app (no KMP), add the dependency to your app module's `build.gradle.kts` under `dependencies { implementation("com.atruedev:kmp-ble:0.8.1") }`.

### 2. Initialize (Android Only)

In your `Application` subclass:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.atruedev.kmpble.KmpBle.init(this)
    }
}
```

Don't forget to register it in `AndroidManifest.xml`:

```xml
<application
    android:name=".MyApp"
    ... >
```

### 3. Permissions (Android 12+)

Add to `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" /> <!-- legacy, for scan results on older APIs -->
```

Request at runtime:

```kotlin
val result = com.atruedev.kmpble.permissions.checkBlePermissions()
when (result) {
    is com.atruedev.kmpble.permissions.PermissionResult.Granted -> { /* ready */ }
    is com.atruedev.kmpble.permissions.PermissionResult.Denied -> {
        com.atruedev.kmpble.permissions.requestBlePermissions(this)
    }
    is com.atruedev.kmpble.permissions.PermissionResult.PermanentlyDenied -> {
        // open app settings
    }
}
```

### 4. First Scan (Common Code)

```kotlin
import com.atruedev.kmpble.scanner.Scanner
import com.atruedev.kmpble.scanner.ScanEvent
import com.atruedev.kmpble.scanner.EmissionPolicy
import com.atruedev.kmpble.ServiceUuid
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val scanner = Scanner {
        timeout = 10.seconds
        emission = EmissionPolicy.FirstThenChanges(rssiThreshold = 10)
        filters {
            // Optional: filter by service UUID (e.g., Heart Rate = 0x180D)
            // match { serviceUuid(ServiceUuid.HEART_RATE) }
        }
    }

    // Collect scan events
    launch {
        scanner.scanEvents.collect { event ->
            when (event) {
                is ScanEvent.Found -> {
                    val ad = event.advertisement
                    println("Found: ${ad.name ?: "Unknown"} (${ad.identifier}) RSSI=${ad.rssi}")
                    println("  Services: ${ad.serviceUuids.joinToString()}")
                }
                is ScanEvent.Failed -> println("Scan failed: ${event.error.message}")
            }
        }
    }

    // Keep main alive for scan duration
    kotlinx.coroutines.delay(11.seconds)
    scanner.close()
}
```

---

## Option B: iOS (Swift Package Manager)

### 1. Add Package

In Xcode: **File → Add Package Dependencies** → enter:

```
https://github.com/gary-quinn/kmp-ble
```

Select **Up to Next Major Version** → `0.8.1` → add `KmpBle` to your target.

### 2. Info.plist Permissions

Add to `Info.plist`:

```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>This app needs Bluetooth to scan and connect to BLE devices.</string>
<key>NSBluetoothPeripheralUsageDescription</key>
<string>This app uses Bluetooth to communicate with BLE peripherals.</string>
<!-- Optional: for background state restoration -->
<key>UIBackgroundModes</key>
<array>
    <string>bluetooth-central</string>
</array>
```

### 3. Initialize & Scan (Swift)

```swift
import KmpBle
import Foundation

@main
struct QuickstartApp {
    static func main() async {
        // Optional: configure logger
        BleLogConfigKt.logger = PrintBleLogger()
        
        let scanner = Scanner(
            config: ScannerConfig(
                timeout: KotlinLong(10_000_000_000), // 10 seconds in nanoseconds
                emission: EmissionPolicy.FirstThenChanges(rssiThreshold: 10),
                filters: [] // or add ServiceFilter(serviceUuid: ServiceUuid.HEART_RATE.uuid)
            )
        )

        // Collect scan events using Swift async sequence
        for await event in scanner.scanEvents {
            switch event {
            case let found as ScanEvent.Found:
                let ad = found.advertisement
                print("Found: \(ad.name ?? "Unknown") (\(ad.identifier)) RSSI=\(ad.rssi)")
                print("  Services: \(ad.serviceUuids)")
            case let failed as ScanEvent.Failed:
                print("Scan failed: \(failed.error.message)")
            default:
                break
            }
        }
        
        scanner.close()
    }
}
```

---

## Minimal "Blink an LED" Example

This complete example scans for a device with a **Generic Attribute (GATT) LED service**, connects, and toggles an LED. Works with common BLE LED peripherals (ESP32, Nordic, Arduino) that expose a writable characteristic.

### Common BLE LED Service UUIDs

| Platform | Service UUID | Characteristic UUID (Write) |
|----------|--------------|------------------------------|
| Nordic LED Button Service | `00001523-1212-efde-1523-785feabcd123` | `00001525-1212-efde-1523-785feabcd123` |
| ESP32 Custom (example) | `abcd1234-5678-90ab-cdef-1234567890ab` | `abcd1234-5678-90ab-cdef-1234567890ac` |
| Generic (some devices) | `0xFFE0` | `0xFFE1` |

> **Find yours**: Run the scanner above first, note the service/characteristic UUIDs advertised by your device, then plug them in below.

### Shared Kotlin Code (`commonMain`)

```kotlin
// LedController.kt
package com.example.blequickstart

import com.atruedev.kmpble.Peripheral
import com.atruedev.kmpble.ConnectionOptions
import com.atruedev.kmpble.ConnectionRecipe
import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.write
import com.atruedev.kmpble.WriteType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlin.uuid.Uuid
import kotlin.uuid.uuidFrom

class LedController(
    private val advertisement: Advertisement,
    // Replace with your device's UUIDs:
    private val serviceUuid: Uuid = uuidFrom("00001523-1212-efde-1523-785feabcd123"),
    private val ledCharacteristicUuid: Uuid = uuidFrom("00001525-1212-efde-1523-785feabcd123"),
) {
    private val peripheral = advertisement.toPeripheral()

    suspend fun connect() {
        peripheral.connect(ConnectionRecipe.IOT) // auto-reconnect, no bonding required
    }

    suspend fun blink(times: Int = 5, intervalMs: Long = 500) {
        val characteristic = peripheral.findCharacteristic(
            serviceUuid = serviceUuid,
            characteristicUuid = ledCharacteristicUuid
        ) ?: throw IllegalStateException("LED characteristic not found")

        repeat(times) {
            // ON (typically 0x01)
            peripheral.write(characteristic, byteArrayOf(0x01), WriteType.WithResponse)
            delay(intervalMs)
            // OFF (typically 0x00)
            peripheral.write(characteristic, byteArrayOf(0x00), WriteType.WithResponse)
            delay(intervalMs)
        }
    }

    fun observeConnection(): Flow<com.atruedev.kmpble.Peripheral.State> = peripheral.state

    suspend fun disconnect() {
        peripheral.disconnect()
    }

    fun close() {
        peripheral.close()
    }
}
```

### Android Usage (Compose)

```kotlin
// MainActivity.kt
@Composable
fun LedScreen(
    controller: LedController = remember { /* create from scanned advertisement */ }
) {
    var connected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Idle") }

    LaunchedEffect(Unit) {
        controller.observeConnection().collect { state ->
            connected = (state == com.atruedev.kmpble.Peripheral.State.Connected)
            status = state.name
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("LED Controller", style = MaterialTheme.typography.headlineMedium)
        Text("Status: $status")
        Text("Connected: ${if (connected) "Yes" else "No"}")

        Button(
            onClick = {
                if (!connected) return@Button
                scope.launch {
                    status = "Blinking..."
                    controller.blink(times = 3)
                    status = "Done"
                }
            },
            enabled = connected,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Blink LED 3×", style = MaterialTheme.typography.titleMedium)
        }
    }
}
```

### iOS Usage (SwiftUI)

```swift
// LedView.swift
import SwiftUI
import KmpBle

struct LedView: View {
    let controller: LedController
    @State private var status = "Idle"
    @State private var connected = false

    var body: some View {
        VStack(spacing: 24) {
            Text("LED Controller").font(.title)
            Text("Status: \(status)")
            Text("Connected: \(connected ? "Yes" : "No")")

            Button("Blink LED 3×") {
                guard connected else { return }
                status = "Blinking..."
                Task {
                    await controller.blink(times: 3, intervalMs: 500)
                    await MainActor.run { status = "Done" }
                }
            }
            .disabled(!connected)
            .buttonStyle(.borderedProminent)
        }
        .padding()
        .task {
            for await state in controller.observeConnection() {
                await MainActor.run {
                    connected = (state == PeripheralState.connected)
                    status = state.name
                }
            }
        }
    }
}
```

### Run It

1. **Power on your BLE LED device** (make sure it's advertising)
2. **Run the scanner** first to verify it's discovered and note its UUIDs
3. **Update the UUID constants** in `LedController` to match your device
4. **Run the app** on Android (device/emulator with Bluetooth) or iOS (physical device only)
5. **Tap "Blink LED 3×"** — the LED should toggle on/off

---

## Troubleshooting Checklist

| Symptom | Likely Cause | Fix |
|---------|--------------|-----|
| No devices found | Permissions missing | Verify `BLUETOOTH_SCAN`/`CONNECT` granted (Android) or Info.plist (iOS) |
| Scan finds device but connect fails | Wrong UUIDs / device not connectable | Check advertisement `connectable` flag; verify service/char UUIDs |
| Write succeeds but LED doesn't blink | Wrong characteristic / write type | Try `WriteType.WithoutResponse`; verify characteristic supports write |
| `ConnectionRecipe.IOT` reconnects too aggressively | Device doesn't support auto-connect | Use `ConnectionOptions(reconnectionStrategy = null)` |
| iOS simulator shows no devices | Simulator lacks Bluetooth | Test on physical iOS device only |

---

## Next Steps

- **Profiles**: Use `kmp-ble-profiles` for type-safe Heart Rate, Battery, Device Info — no manual UUIDs
- **Codec**: Use `kmp-ble-codec` for typed read/write with composable decoders
- **DFU**: Use `kmp-ble-dfu` for Nordic/MCUboot/ESP firmware updates
- **L2CAP**: High-throughput streaming via `peripheral.openL2capChannel(psm)`
- **GATT Server**: Build a peripheral with `GattServer` + `Advertiser`/`ExtendedAdvertiser`
- **Testing**: Use `FakeScanner` + `FakePeripheral` for hardware-free CI tests

---

## Links

- **API Reference**: [Dokka](https://gary-quinn.github.io/kmp-ble/)
- **Architecture**: [ARCHITECTURE.md](ARCHITECTURE.md)
- **Typed Streams (L2CAP + Codec)**: [STREAMS.md](STREAMS.md)
- **Sample App**: `sample/` (Compose Multiplatform, ~1500 lines)
- **Issues**: [GitHub Issues](https://github.com/gary-quinn/kmp-ble/issues)