# Migration Guide

Migrating to kmp-ble from other Kotlin Multiplatform BLE libraries.

## Setup

### Gradle

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.atruedev:kmp-ble:0.1.0-alpha01")
        }
    }
}
```

### Android initialization

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        KmpBle.init(this)
    }
}
```

### iOS

No initialization needed. Add via SPM or use the XCFramework directly.

## API Mapping

### Scanning

| Concept | kmp-ble |
|---------|---------|
| Create scanner | `Scanner { filters { match { serviceUuid("180d") } } }` |
| Scan results | `scanner.advertisements: Flow<Advertisement>` |
| Stop scanning | Cancel the collecting coroutine or call `scanner.close()` |
| Filter by name | `match { name("HeartSensor") }` |
| Filter by service UUID | `match { serviceUuid(ServiceUuid.HEART_RATE) }` |
| Filter by manufacturer | `match { manufacturerData(CompanyId.APPLE) }` |
| Deduplication | `emission = EmissionPolicy.FirstThenChanges(rssiThreshold = 5)` |
| Scan timeout | `timeout = 30.seconds` |

### Connecting

| Concept | kmp-ble |
|---------|---------|
| Create peripheral | `advertisement.toPeripheral()` |
| Connect | `peripheral.connect()` |
| Connect with options | `peripheral.connect(ConnectionOptions(autoConnect = true, timeout = 15.seconds))` |
| Disconnect | `peripheral.disconnect()` |
| Release resources | `peripheral.close()` (terminal, or use `peripheral.use { }`) |
| Connection state | `peripheral.state: StateFlow<State>` |
| Reconnection | `ConnectionOptions(reconnectionStrategy = ReconnectionStrategy.ExponentialBackoff())` |

### GATT Operations

| Concept | kmp-ble |
|---------|---------|
| Discover services | Automatic on connect. Access via `peripheral.services: StateFlow` |
| Find characteristic | `peripheral.findCharacteristic(serviceUuid, charUuid)` |
| Read | `peripheral.read(characteristic): ByteArray` |
| Write with response | `peripheral.write(char, data, WriteType.WithResponse)` |
| Write without response | `peripheral.write(char, data, WriteType.WithoutResponse)` |
| Observe notifications | `peripheral.observeValues(char): Flow<ByteArray>` |
| Observe with lifecycle | `peripheral.observe(char): Flow<Observation>` |
| Read descriptor | `peripheral.readDescriptor(descriptor)` |
| Write descriptor | `peripheral.writeDescriptor(descriptor, data)` |
| Read RSSI | `peripheral.readRssi(): Int` |
| Request MTU | `peripheral.requestMtu(512): Int` |
| Max write length | `peripheral.maximumWriteValueLength: StateFlow<Int>` |

### Bonding

| Concept | kmp-ble |
|---------|---------|
| Bond state | `peripheral.bondState: StateFlow<BondState>` |
| Proactive bonding | `ConnectionOptions(bondingPreference = BondingPreference.Required)` |
| Implicit bonding | Default - OS triggers when device requires encryption |
| Remove bond (Android) | `@OptIn(ExperimentalBleApi::class) peripheral.removeBond()` |
| Remove bond (iOS) | Returns `BondRemovalResult.NotSupported` - use Settings |

### Adapter State

| Concept | kmp-ble |
|---------|---------|
| Create adapter | `BluetoothAdapter()` |
| Observe state | `adapter.state: StateFlow<BluetoothAdapterState>` |
| States | `On`, `Off`, `Unavailable`, `Unauthorized`, `Unsupported` |
| Release | `adapter.close()` |

### Permissions

| Concept | kmp-ble |
|---------|---------|
| Check permissions | `checkBlePermissions(): PermissionResult` |
| Result types | `Granted`, `Denied(permissions)`, `PermanentlyDenied(permissions)` |

### Logging

```kotlin
// Enable debug logging
BleLogConfig.logger = PrintBleLogger()

// Custom logger
BleLogConfig.logger = BleLogger { event ->
    // Forward to Timber, OSLog, Kermit, etc.
}

// Disable
BleLogConfig.logger = null
```

### Testing

```kotlin
// Fake scanner
val scanner = FakeScanner {
    advertisement {
        name("HeartSensor")
        rssi(-55)
        serviceUuids("180d")
    }
}

// Fake peripheral
val peripheral = FakePeripheral {
    service("180d") {
        characteristic("2a37") {
            properties(notify = true, read = true)
            onRead { byteArrayOf(0x00, 72) }
            onObserve { flow { emit(byteArrayOf(0x00, 72)) } }
        }
    }
    onConnect { Result.success(Unit) }
}
```

## Key Differences

### Explicit WriteType

kmp-ble requires an explicit `WriteType` on every write - no default. This prevents the common mistake of accidentally using fire-and-forget writes when acknowledged writes are needed.

### Object Identity for Characteristics

`Characteristic` and `Descriptor` use reference equality, not structural equality. Always get them from `peripheral.services` or `findCharacteristic()` after connecting - don't construct them manually.

### Close vs Disconnect

- `disconnect()` - graceful, peripheral can reconnect
- `close()` - terminal, releases all native resources, peripheral is unusable after

Always call `close()` when done (e.g., `ViewModel.onCleared()`, `deinit`).

### Zero-Copy BleData

Advertisement data (`manufacturerData`, `serviceData`) uses `BleData` instead of `ByteArray`. Call `.toByteArray()` only when you need a mutable copy. On iOS this avoids unnecessary `NSData` → `ByteArray` copies.

## Requirements

- Kotlin 2.3.0+
- Android minSdk 33
- iOS 15+
- kotlinx-coroutines 1.10+
