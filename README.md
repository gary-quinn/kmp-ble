# kmp-ble

[![CI](https://github.com/atruedeveloper/kmp-ble/actions/workflows/ci.yml/badge.svg)](https://github.com/atruedeveloper/kmp-ble/actions/workflows/ci.yml)
[![Publish](https://github.com/atruedeveloper/kmp-ble/actions/workflows/publish.yml/badge.svg)](https://github.com/atruedeveloper/kmp-ble/actions/workflows/publish.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.atruedev/kmp-ble)](https://central.sonatype.com/artifact/com.atruedev/kmp-ble)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-purple.svg)](https://kotlinlang.org)

Kotlin Multiplatform BLE library for Android and iOS.

## Setup

### Android / KMP (Gradle)

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.atruedev:kmp-ble:0.1.5")
        }
    }
}
```

Initialize in your `Application.onCreate()` (Android only):

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        KmpBle.init(this)
    }
}
```

### iOS (Swift Package Manager)

In Xcode: **File > Add Package Dependencies** and enter:

```
https://github.com/atruedeveloper/kmp-ble
```

Select the version and add `KmpBle` to your target.

```swift
import KmpBle
```

## Usage

### Scan for devices

Standard service UUIDs are provided as constants in `ServiceUuid`. For custom/proprietary
services, use the UUID from your device's documentation or GATT profile.

See: [Bluetooth SIG Service UUIDs](https://bitbucket.org/bluetooth-SIG/public/src/main/assigned_numbers/uuids/service_uuids.yaml)

```kotlin
// Common code — works on both Android and iOS
val scanner = Scanner {
    timeout = 30.seconds
    emission = EmissionPolicy.FirstThenChanges(rssiThreshold = 10)
    filters {
        match { serviceUuid(ServiceUuid.HEART_RATE) }
    }
}

scanner.advertisements.collect { ad ->
    println("Found: ${ad.name} (${ad.identifier}) rssi=${ad.rssi}")
}

scanner.close()
```

### Monitor adapter state

```kotlin
val adapter = BluetoothAdapter()

adapter.state.collect { state ->
    when (state) {
        BluetoothAdapterState.On -> println("Bluetooth ready")
        BluetoothAdapterState.Off -> println("Bluetooth off")
        BluetoothAdapterState.Unauthorized -> println("Permission denied")
        else -> {}
    }
}
```

### Connect and read/write

```kotlin
val peripheral = advertisement.toPeripheral()

peripheral.connect()

val hrChar = peripheral.findCharacteristic(
    serviceUuid = uuidFrom("180d"),
    characteristicUuid = uuidFrom("2a37"),
)!!

// Read
val value = peripheral.read(hrChar)

// Write (explicit WriteType required)
peripheral.write(hrChar, byteArrayOf(0x01), WriteType.WithResponse)

// Observe notifications
peripheral.observeValues(hrChar).collect { data ->
    println("Heart rate: ${data[1]}")
}

peripheral.disconnect()
peripheral.close() // or use peripheral.use { ... }
```

### Bonding

```kotlin
// Proactive bonding
peripheral.connect(ConnectionOptions(bondingPreference = BondingPreference.Required))

// Observe bond state
peripheral.bondState.collect { state ->
    println("Bond: $state") // NotBonded, Bonding, Bonded, Unknown
}

// Remove bond (Android only)
@OptIn(ExperimentalBleApi::class)
val result = peripheral.removeBond()
```

### Reconnection

```kotlin
peripheral.connect(ConnectionOptions(
    reconnectionStrategy = ReconnectionStrategy.ExponentialBackoff(
        initialDelay = 1.seconds,
        maxDelay = 30.seconds,
        maxAttempts = 10,
    )
))
```

### Permissions

```kotlin
when (val result = checkBlePermissions()) {
    is PermissionResult.Granted -> { /* ready to scan */ }
    is PermissionResult.Denied -> { /* request permissions */ }
    is PermissionResult.PermanentlyDenied -> { /* open settings */ }
}
```

### Logging

```kotlin
BleLogConfig.logger = PrintBleLogger() // stdout/logcat
// or
BleLogConfig.logger = BleLogger { event -> Timber.d("BLE: $event") }
```

### Test without hardware

```kotlin
val scanner = FakeScanner {
    advertisement {
        name("HeartSensor")
        rssi(-55)
        serviceUuids("180d")
    }
}

val peripheral = FakePeripheral {
    service("180d") {
        characteristic("2a37") {
            properties(notify = true, read = true)
            onRead { byteArrayOf(0x00, 72) }
            onObserve {
                flow {
                    emit(byteArrayOf(0x00, 72))
                    delay(1000)
                    emit(byteArrayOf(0x00, 80))
                }
            }
        }
    }
}
```

## Architecture

- **State machine:** 14 states with declarative transition table — no invalid states in production
- **Per-peripheral concurrency:** `limitedParallelism(1)` serialization, no locks
- **GATT queue:** FIFO with timeout watchdog
- **Zero-copy:** `BleData` wraps `NSData` on iOS, `ByteArray` on Android
- **Object identity:** `Characteristic` and `Descriptor` use reference equality, matching native API behavior
- **Composable errors:** Sealed interfaces — `AuthError`, `GattOperationError`, `ConnectionError`

## Requirements

- Kotlin 2.3.0+
- Android minSdk 33
- iOS 15+
- kotlinx-coroutines 1.10+

## License

[Apache 2.0](LICENSE) — Copyright (C) 2026 Huynh Thien Thach
