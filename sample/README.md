# kmp-ble Sample App

Compose Multiplatform app (Android + iOS) exercising the full kmp-ble v0.2 API surface.

## Screens

### Scanner

Discovers nearby BLE peripherals using legacy and BLE 5.0 extended scanning.

| API | Usage |
|-----|-------|
| `Scanner { emission, legacyOnly }` | Configurable scan with RSSI threshold filtering |
| `Advertisement` | Name, RSSI, service UUIDs, connectable flag |
| `Advertisement.primaryPhy / secondaryPhy / advertisingSid` | BLE 5.0 extended advertisement metadata |
| `EmissionPolicy.FirstThenChanges` | Reduces redundant scan results |

Scan results are collected into a `HashMap` and snapshotted to an `@Immutable` data class at 4 Hz to avoid recomposition storms in `LazyColumn`. Devices not seen for 10 seconds are evicted to keep the list bounded and relevant.

### Device Detail

Connects to a peripheral and exposes all GATT client operations.

**Connection**

| API | Usage |
|-----|-------|
| `Peripheral.connect(ConnectionOptions)` | Configurable connection with auto-connect, bonding, reconnection |
| `ConnectionRecipe.MEDICAL / FITNESS / IOT / CONSUMER` | Preset connection profiles |
| `ReconnectionStrategy.ExponentialBackoff` | Automatic reconnection on disconnect |
| `Peripheral.state` | Granular connection state machine (transport, auth, discovery, config) |

**Pairing**

| API | Usage |
|-----|-------|
| `PairingHandler` | Suspending callback for pairing events |
| `PairingEvent.NumericComparison / PasskeyRequest / JustWorks / OOB` | All pairing flows |
| `PairingResponse.Confirm / ProvidePin / ProvideOobData` | User responses |
| `Peripheral.bondState` | Bond state tracking |
| `Peripheral.removeBond()` | Bond removal |

Pairing is managed by `PairingCoordinator`, which uses a `Channel<PairingResponse>(RENDEZVOUS)` to suspend the pairing handler until the UI responds — no locks or mutable continuation vars.

**GATT Operations**

| API | Usage |
|-----|-------|
| `Peripheral.read(characteristic)` | Read characteristic value |
| `Peripheral.write(characteristic, data, writeType)` | Write with/without response |
| `Peripheral.observe(characteristic, BackpressureStrategy.Latest)` | Subscribe to notifications/indications |
| `Peripheral.readRssi()` | Live signal strength |
| `Peripheral.requestMtu(mtu)` | MTU negotiation |
| `Peripheral.maximumWriteValueLength` | MTU-derived write size |

**Benchmarks**

| API | Usage |
|-----|-------|
| `bleStopwatch("connect") { peripheral.connect() }` | Connection timing |
| `ThroughputMeter` | Bytes/sec across multiple reads |
| `LatencyTracker` | p50/p95/p99 read latency stats |

### Server

Hosts a GATT server with Heart Rate service and manages advertising.

**GATT Server**

| API | Usage |
|-----|-------|
| `GattServer { service { characteristic { onRead, properties, permissions } } }` | Declarative server builder |
| `GattServer.open() / close()` | Server lifecycle |
| `GattServer.notify(uuid, device, data)` | Push notifications to connected clients |
| `GattServer.connectionEvents` | Connect/disconnect event stream |

The `onRead` lambda captures a `StateFlow` reference (not a snapshot) so the returned heart rate value reflects the current state.

**Advertising**

| API | Usage |
|-----|-------|
| `Advertiser` | Legacy (BLE 4.x) advertising |
| `AdvertiseConfig(name, serviceUuids, connectable)` | Legacy config |
| `ExtendedAdvertiser` | BLE 5.0 multi-set advertising |
| `ExtendedAdvertiseConfig(primaryPhy, secondaryPhy, interval)` | PHY and interval selection |
| `Phy.Le1M / Le2M / LeCoded` | PHY options for range vs throughput |

## Architecture

```
App.kt                  Navigation, adapter lifecycle, state restoration
ScannerScreen.kt         Scan collection with @Immutable snapshot throttling
DeviceDetailScreen.kt    GATT client UI, delegates to BleViewModel
BleViewModel.kt          Peripheral lifecycle scoped to ViewModel
PairingCoordinator.kt    Channel-based pairing state (composed into BleViewModel)
ServerScreen.kt          Server/advertiser UI, delegates to ServerViewModel
ServerViewModel.kt       Server + advertiser lifecycle scoped to ViewModel
```

**Key patterns:**
- `Peripheral.close()` in `ViewModel.onCleared()` prevents GATT connection leaks
- All BLE operations scoped to `viewModelScope` for structured cancellation
- `Channel(RENDEZVOUS)` for pairing instead of mutable continuation vars
- `@Immutable` data classes for Compose skip optimization in scan list
- `Flow.conflate()` + periodic snapshot decouples scan collection from rendering

## Running

**Android:** `./gradlew :sample-android:installDebug`

**iOS:** Open `iosApp/iosApp.xcodeproj` in Xcode, select a device, and run. BLE requires a physical device — simulators don't support CoreBluetooth scanning.
