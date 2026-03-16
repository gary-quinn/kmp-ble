# kmp-ble

Kotlin Multiplatform BLE library for Android and iOS.

**Status:** v0.1.0-alpha01 — scanning, connecting, GATT read/write/observe.

## Setup

### Android / KMP (Gradle)

Add the dependency to your module `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.garyquinn:kmp-ble:0.1.0-alpha01")
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

Then import in Swift:

```swift
import KmpBle
```

## Usage

### Scan for devices

Standard service UUIDs are provided as constants in `ServiceUuid`. For custom/proprietary
services, use the UUID from your device's documentation or GATT profile.

See: [Bluetooth SIG Service UUIDs](https://bitbucket.org/bluetooth-SIG/public/src/main/assigned_numbers/uuids/service_uuids.yaml)

```kotlin
// Android
val scanner = AndroidScanner(context) {
    timeout = 30.seconds
    emission = EmissionPolicy.FirstThenChanges(rssiThreshold = 10)
    filters {
        match { serviceUuid(ServiceUuid.HEART_RATE) }
    }
}

// iOS
val scanner = IosScanner {
    filters {
        match { serviceUuid(ServiceUuid.HEART_RATE) }
        // or custom UUID: match { serviceUuid("6e400001-b5a3-f393-e0a9-e50e24dcca9e") }
    }
}

// Collect (both platforms)
scanner.advertisements.collect { ad ->
    println("Found: ${ad.name} (${ad.identifier}) rssi=${ad.rssi}")
}

scanner.close()
```

### Monitor adapter state

```kotlin
// Android
val adapter = AndroidBluetoothAdapter(context)
// iOS
val adapter = IosBluetoothAdapter()

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
// Android
val peripheral = AndroidPeripheral(bluetoothDevice, context)
// iOS
val peripheral = IosPeripheral(cbPeripheral)

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
peripheral.close()
```

### Test without hardware

```kotlin
val fake = FakePeripheral {
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

fake.connect()
val hr = fake.read(fake.findCharacteristic(uuidFrom("180d"), uuidFrom("2a37"))!!)
// hr == [0x00, 72]
```

## Architecture

- **State machine:** 14 states with declarative transition table — no invalid states in production
- **Per-peripheral concurrency:** `limitedParallelism(1)` serialization, no locks
- **GATT queue:** FIFO with timeout watchdog, Immediate priority for disconnect
- **Zero-copy:** `BleData` wraps `NSData` on iOS, `ByteArray` on Android — no memcpy on scan
- **Object identity:** `Characteristic` and `Descriptor` use reference equality, matching native API behavior

## Requirements

- Kotlin 2.3.0+
- Android minSdk 33
- iOS 15+
- kotlinx-coroutines 1.10+

## License

Apache 2.0
