# kmp-ble

[![CI](https://github.com/gary-quinn/kmp-ble/actions/workflows/ci.yml/badge.svg)](https://github.com/gary-quinn/kmp-ble/actions/workflows/ci.yml)
[![Publish](https://github.com/gary-quinn/kmp-ble/actions/workflows/publish.yml/badge.svg)](https://github.com/gary-quinn/kmp-ble/actions/workflows/publish.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.atruedev/kmp-ble)](https://central.sonatype.com/artifact/com.atruedev/kmp-ble)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-purple.svg)](https://kotlinlang.org)

Kotlin Multiplatform BLE library for Android and iOS.

## Modules

| Module | Artifact | Description |
|--------|----------|-------------|
| **kmp-ble** | `com.atruedev:kmp-ble` | Core BLE — scanning, connecting, GATT read/write/observe, server, advertising |
| **kmp-ble-profiles** | `com.atruedev:kmp-ble-profiles` | Type-safe GATT profile parsing (Heart Rate, Battery, Device Info, Blood Pressure, Glucose, CSC) |
| **kmp-ble-dfu** | `com.atruedev:kmp-ble-dfu` | Firmware updates — Nordic Secure DFU, MCUboot SMP, Espressif ESP OTA — with auto-detection and progress tracking |
| **kmp-ble-codec** | `com.atruedev:kmp-ble-codec` | Format-agnostic typed read/write via composable `BleEncoder`/`BleDecoder` |

## Setup

### Android / KMP (Gradle)

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.atruedev:kmp-ble:0.3.16")

            // Optional modules
            implementation("com.atruedev:kmp-ble-profiles:0.3.16")
            implementation("com.atruedev:kmp-ble-dfu:0.3.16")
            implementation("com.atruedev:kmp-ble-codec:0.3.16")
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
https://github.com/gary-quinn/kmp-ble
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

### Profiles (kmp-ble-profiles)

Type-safe GATT profile parsing via Peripheral extension functions:

```kotlin
// Heart Rate
peripheral.heartRateMeasurements().collect { measurement ->
    println("BPM: ${measurement.heartRate}")
    println("RR intervals: ${measurement.rrIntervals}")
    println("Contact: ${measurement.sensorContactDetected}")
}
val location = peripheral.readBodySensorLocation() // Chest, Wrist, etc.

// Battery
val level = peripheral.readBatteryLevel() // 0..100 or null
peripheral.batteryLevelNotifications().collect { println("Battery: $it%") }

// Device Information
val info = peripheral.readDeviceInformation()
println("${info.manufacturerName} ${info.modelNumber} fw:${info.firmwareRevision}")
```

Supported profiles: Heart Rate, Battery, Device Information, Blood Pressure, Glucose, Cycling Speed and Cadence.

### Codec (kmp-ble-codec)

Typed read/write with composable decoders:

```kotlin
val TemperatureDecoder = BleDecoder<Float> { data ->
    // IEEE 11073 FLOAT parsing
    val mantissa = (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
    val exponent = data[3].toInt()
    mantissa * 10f.pow(exponent)
}

// Typed read
val temp: Float = peripheral.read(characteristic, TemperatureDecoder)

// Typed observe
peripheral.observeValues(characteristic, TemperatureDecoder).collect { celsius ->
    println("Temperature: $celsius°C")
}

// Decoder composition
val FormattedTemp = TemperatureDecoder.map { "%.1f°C".format(it) }
```

### DFU (kmp-ble-dfu)

Firmware updates supporting Nordic Secure DFU, MCUboot SMP (Zephyr/Mynewt), and Espressif ESP OTA:

```kotlin
// Auto-detect protocol from peripheral's GATT services
val controller = DfuController.create(peripheral)

// Or specify explicitly
val controller = DfuController(peripheral, McuBootDfuProtocol())

controller.performDfu(firmware).collect { progress ->
    when (progress) {
        is DfuProgress.Transferring -> println("${(progress.fraction * 100).toInt()}%")
        is DfuProgress.Completed -> println("Done")
        is DfuProgress.Failed -> println("Error: ${progress.error}")
        else -> {}
    }
}

// Abort mid-transfer
controller.abort()
```

Each protocol has its own firmware parser:

```kotlin
// Nordic Secure DFU (.zip)
val nordic = FirmwarePackage.Nordic.fromZipBytes(zipData)

// MCUboot SMP (.bin)
val mcuboot = FirmwarePackage.McuBoot.fromBinBytes(binData)

// Espressif ESP OTA (.bin)
val esp = FirmwarePackage.EspOta.fromBinBytes(binData)
```

ESP OTA supports custom service UUIDs for vendor flexibility:

```kotlin
val controller = DfuController(peripheral, EspOtaDfuProtocol())
controller.performDfu(firmware, DfuOptions(
    transport = DfuTransportConfig.EspOta(
        serviceUuid = uuidFrom("your-custom-service-uuid"),
    )
))
```

### GATT Server and Advertising

```kotlin
val server = GattServer {
    service(uuidFrom("180D")) {
        characteristic(uuidFrom("2A37")) {
            properties { read = true; notify = true }
            permissions { read = true }
            onRead { device -> BleData(byteArrayOf(0x00, 72)) }
        }
    }
}
server.open()

// Legacy advertising
val advertiser = Advertiser()
advertiser.startAdvertising(AdvertiseConfig(
    serviceUuids = listOf(uuidFrom("180D")),
    connectable = true,
))

// Notify connected clients
server.notify(uuidFrom("2A37"), device = null, BleData(byteArrayOf(0x00, 80)))

// Extended advertising (BLE 5.0)
val extAdvertiser = ExtendedAdvertiser()
extAdvertiser.startAdvertisingSet(ExtendedAdvertiseConfig(
    serviceUuids = listOf(uuidFrom("180D")),
    primaryPhy = Phy.Le1M,
    secondaryPhy = Phy.Le2M,
))
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

### Connection Recipes

```kotlin
// Pre-configured connection options for common use cases
peripheral.connect(ConnectionRecipe.MEDICAL)  // strict bonding, no auto-connect
peripheral.connect(ConnectionRecipe.FITNESS)  // reconnection, if-required bonding
peripheral.connect(ConnectionRecipe.IOT)      // auto-connect, no bonding
peripheral.connect(ConnectionRecipe.CONSUMER) // balanced defaults
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
            properties(notify = true, read = true, write = true)
            onRead { byteArrayOf(0x00, 72) }
            onWrite { data, writeType -> println("Wrote ${data.size} bytes") }
            onObserve {
                flow {
                    emit(byteArrayOf(0x00, 72))
                    delay(1000)
                    emit(byteArrayOf(0x00, 80))
                }
            }
            // Simulate slow BLE responses
            respondAfter(500.milliseconds)
        }
        characteristic("2a39") {
            properties(write = true)
            // Simulate GATT errors
            failWith(GattError("write", GattStatus.WriteNotPermitted))
        }
    }
}
```

## Sample Apps

### BLE Toolkit (`sample/`)

Production-grade BLE utility app (Android + iOS) with tab-based navigation, composed operation classes, and polished UX:

- **Scanner** with RSSI/name filtering, sorting, device categorization, manufacturer name resolution
- **Device Detail** with GATT service browser, value display in HEX/UTF-8/Decimal/Binary, connection recipes, bonding
- **Multi-protocol DFU** with auto-detection (Nordic Secure DFU, MCUboot SMP, Espressif ESP OTA)
- **Profile monitoring** — Heart Rate, Battery, Device Information with auto-detection
- **L2CAP channels** with message history and directional logging
- **Codec** typed read/write with decoder composition
- **GATT Server** with service builder presets and advanced advertising controls

### Quickstart (`sample-quickstart/`)

Minimal ~150-line single-screen app: scan, tap, connect, read first characteristic, display value. No ViewModel, no navigation — the "Getting Started in 5 Minutes" reference.

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

[Apache 2.0](LICENSE) — Copyright (C) 2026 Gary Quinn
