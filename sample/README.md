# kmp-ble Sample App

Compose Multiplatform app (Android + iOS) exercising the full kmp-ble v0.8+ API surface.

## Quickstart (Code Examples)

For a self-contained code walkthrough without running the full app, see
[BleQuickstart.kt](src/commonMain/kotlin/com/atruedev/kmpble/sample/BleQuickstart.kt).
It demonstrates the complete BLE lifecycle:

1. **Scan** for peripherals advertising a specific service
2. **Connect** with configurable options
3. **Discover** services and characteristics
4. **Read** characteristic values
5. **Observe** notifications with transparent reconnection
6. **Disconnect** and clean up

Two patterns are provided:
- `bleQuickstartHeartRate()` - continuous monitoring with observations
- `bleQuickstartReadOnce()` - scan, connect, read a single value, disconnect

## Navigation

```
Scanner ──→ DeviceDetail (hub) ──→ Service Explorer
       │                       └──→ Heart Rate Monitor
       └──→ Server
```

Sub-screens share a single `BleViewModel` keyed by device identifier.

## Screens

### Scanner

Discovers nearby BLE peripherals with legacy and BLE 5.0 extended scanning.

| API | Usage |
|-----|-------|
| `Scanner { emission, legacyOnly }` | Configurable scan with RSSI threshold filtering |
| `Advertisement` | Name, RSSI, service UUIDs, connectable flag |
| `Advertisement.primaryPhy / secondaryPhy / advertisingSid` | BLE 5.0 extended advertisement metadata |
| `EmissionPolicy.FirstThenChanges` | Reduces redundant scan results |
| `ServiceUuid` | Well-known service name resolution in device cards |

Scan results are collected into a `HashMap` confined to `limitedParallelism(1)` and snapshotted to an `@Immutable` data class at 4 Hz. Devices not seen for 10 seconds are evicted.

### Device Detail (Hub)

Connection management with progressive disclosure. Shows connection options and device info. Navigation cards to sub-screens appear after connection.

| API | Usage |
|-----|-------|
| `Peripheral.connect(ConnectionOptions)` | Configurable connection with auto-connect, bonding, reconnection |
| `ConnectionRecipe.MEDICAL / FITNESS / IOT / CONSUMER` | Preset connection profiles |
| `ReconnectionStrategy.ExponentialBackoff` | Automatic reconnection on disconnect |
| `Peripheral.state` | Granular connection state machine |
| `PairingHandler` | Suspending callback for all pairing flows |

Pairing is managed by `PairingCoordinator` using `Channel<PairingResponse>(RENDEZVOUS)`.

### Service Explorer

GATT tree browsing with read/write/observe operations, benchmarks, and L2CAP.

| API | Usage |
|-----|-------|
| `Peripheral.dump()` | Tree-formatted GATT service/characteristic/descriptor dump |
| `Peripheral.read / write / observe` | Standard GATT operations |
| `Peripheral.openL2capChannel(psm)` | High-throughput streaming channel |
| `L2capChannel.write / incoming` | Stream-oriented data transfer |
| `bleStopwatch` / `ThroughputMeter` / `LatencyTracker` | Connection and read benchmarks |

L2CAP is managed by `L2capController` composed into `BleViewModel`.

### Heart Rate Monitor

End-to-end demo: auto-finds Heart Rate service (0x180D), subscribes via `observeValues()` for transparent reconnection, displays live BPM.

| API | Usage |
|-----|-------|
| `Peripheral.observeValues(characteristic)` | Connection-transparent value streaming |
| Heart Rate Measurement (0x2A37) | Standard HR data parsing (8-bit and 16-bit formats) |

Only accessible when the connected device advertises the Heart Rate service.

**Two-phone demo:** Open the Server screen on phone A (hosts a Heart Rate service), scan with phone B, connect, and see live data in the Heart Rate Monitor.

### Server

GATT server hosting a Heart Rate service with legacy and BLE 5.0 extended advertising.

| API | Usage |
|-----|-------|
| `GattServer` | Heart Rate service with readable/notifiable characteristic |
| `Advertiser` | Legacy connectable advertising |
| `ExtendedAdvertiser` | BLE 5.0 advertising with PHY selection (1M/2M/Coded) |

## Architecture

```
App.kt (navigation)
├── ScannerScreen ── @Immutable ScannedDevice, limitedParallelism(1), TTL eviction
├── DeviceDetailScreen (hub) ── ConnectionSection, InfoSection, NavigationCards
│   ├── ServiceExplorerScreen ── GATT dump, read/write/observe, benchmarks, L2CAP
│   └── HeartRateDemoScreen ── observeValues(), live BPM display
├── ServerScreen ── ServerViewModel (server + advertiser lifecycle)
└── Shared: BleViewModel ── PairingCoordinator, L2capController (composition)
```

## Build & Run

```bash
# Android
./gradlew :sample:installDebug

# iOS
open iosApp/iosApp.xcodeproj
# Select "iosApp" scheme → Run on device
```
