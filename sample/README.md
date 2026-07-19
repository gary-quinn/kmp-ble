# kmp-ble Sample App

Compose Multiplatform app (Android + iOS) exercising the full kmp-ble v0.10+ API surface.

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
Scanner ──→ Beacon Scanner
     │  └──→ Server
     └──→ DeviceDetail (hub) ──→ Service Explorer
                            ├──→ Heart Rate Monitor
                            ├──→ Battery Level
                            ├──→ Device Information
                            ├──→ Firmware Update (DFU)
                            ├──→ Codec Examples
                            └──→ Connection Monitor
```

Sub-screens share a single `BleViewModel` keyed by device identifier.

## Screens

### Scanner

Discovers nearby BLE peripherals with legacy and BLE 5.0 extended scanning. Shows
platform Bluetooth capabilities (2M PHY, Coded PHY, LE Audio, Direction Finding, etc.)

| API | Usage |
|-----|-------|
| `Scanner { emission, legacyOnly }` | Configurable scan with RSSI threshold filtering |
| `Advertisement` | Name, RSSI, service UUIDs, connectable flag |
| `Advertisement.primaryPhy / secondaryPhy / advertisingSid` | BLE 5.0 extended advertisement metadata |
| `EmissionPolicy.FirstThenChanges` | Reduces redundant scan results |
| `ServiceUuid` | Well-known service name resolution in device cards |
| `BleCapabilities` | BLE 5.x feature detection (2M PHY, LE Audio, etc.) |

Scan results are collected into a `HashMap` confined to `limitedParallelism(1)` and snapshotted to an `@Immutable` data class at 4 Hz. Devices not seen for 10 seconds are evicted.

### Beacon Scanner

Scans for iBeacon (Apple) and Eddystone (UID, URL, TLM) beacons with type filtering.

| API | Usage |
|-----|-------|
| `BeaconScanner(scanner, scope)` | Filters scan events to beacon protocols |
| `Beacon.IBeacon` | Proximity UUID, major, minor, 1m calibrated RSSI |
| `Beacon.EddystoneUID` | 10-byte namespace + 6-byte instance |
| `Beacon.EddystoneURL` | Compressed URL + txPower |
| `Beacon.EddystoneTLM` | Battery, temperature, advertisement count, uptime |

Accessible from the Scanner top bar ("Beacons" button).

### Device Detail (Hub)

Connection management with progressive disclosure. Shows connection options, device info
(including negotiated PHY), and navigation cards to sub-screens after connection.

| API | Usage |
|-----|-------|
| `Peripheral.connect(ConnectionOptions)` | Configurable connection with auto-connect, bonding, reconnection |
| `ConnectionRecipe.MEDICAL / FITNESS / IOT / CONSUMER` | Preset connection profiles |
| `ReconnectionStrategy.ExponentialBackoff` | Automatic reconnection on disconnect |
| `Peripheral.state` | Granular connection state machine |
| `PairingHandler` | Suspending callback for all pairing flows |
| `Peripheral.readPhy()` | Current TX/RX PHY for the connection |
| `Peripheral.readRssi()` | Current RSSI reading |

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
| `HeartRateMeasurement` | Typed HR data with RR intervals, energy, sensor contact |
| `BodySensorLocation` | Sensor placement (chest, wrist, etc.) |

Only accessible when the connected device advertises the Heart Rate service.

**Two-phone demo:** Open the Server screen on phone A (hosts a Heart Rate service), scan with phone B, connect, and see live data in the Heart Rate Monitor.

### Battery Level

Reads and subscribes to Battery Service (0x180F) using `BatteryProfile`.

| API | Usage |
|-----|-------|
| `Peripheral.readBatteryLevel()` | One-shot battery level read |
| `Peripheral.batteryLevelNotifications()` | Continuous battery level updates |

### Device Information

Reads Device Information Service (0x180A) characteristics using `DeviceInformationProfile`.

| API | Usage |
|-----|-------|
| `Peripheral.readDeviceInformation()` | Typed device info (manufacturer, model, serial, firmware, etc.) |
| `DeviceInformation` | Parsed DIS fields with System ID and PnP ID |

### Firmware Update (DFU)

Nordic Secure DFU v2 with progress tracking and abort support.

| API | Usage |
|-----|-------|
| `DfuController(peripheral)` | DFU session management |
| `DfuProgress` | Transferring, Verifying, Completing, Completed, Failed states |
| `FirmwarePackage.Nordic.fromZipBytes()` | Parse Nordic DFU .zip firmware packages |

### Codec Examples

Typed characteristic reads using `BleCodec` / `BleDecoder` instead of raw bytes.

| API | Usage |
|-----|-------|
| `Peripheral.read(char, decoder)` | Typed read with `BleDecoder<T>` |
| `BleDecoder.map / contramap / bimap` | Decoder composition |
| `BleCodec<T>` | Bidirectional codec for read + write |

### Connection Monitor

Real-time connection health dashboard using `ConnectionQualityMonitor` and `PowerMonitor`.

| API | Usage |
|-----|-------|
| `ConnectionQualityMonitor(peripheral, scope)` | Tracks connections, disconnections, last RSSI |
| `PowerMonitor(peripheral, scope, txPower)` | Path loss computation (txPower - RSSI) |
| `ConnectionQuality` | Aggregated stats: totalConnections, reconnectionCount, isConnected |
| `PathLossReading` | Path loss in dB with RSSI and configured TX power |

RSSI auto-polls every 2 seconds while connected. Path loss is assessed with
signal-strength labels (Good / Moderate / Weak / Very Weak).

### Server

GATT server hosting a Heart Rate service with legacy and BLE 5.0 extended advertising
(including periodic advertising). Also hosts L2CAP sensor + blob streams.

| API | Usage |
|-----|-------|
| `GattServer` | Heart Rate service with readable/notifiable characteristic |
| `Advertiser` | Legacy connectable advertising |
| `ExtendedAdvertiser` | BLE 5.0 advertising with PHY selection (1M/2M/Coded) |
| `PeriodicAdvertisingParameters` | Periodic advertising on secondary channels |
| `L2capListener` | Accept L2CAP CoC connections, serve typed SensorReading stream |
| `TypedL2capChannel` | CBOR-framed typed L2CAP channel for blob + sensor demos |

## Architecture

```
App.kt (navigation)
├── ScannerScreen ── @Immutable ScannedDevice, limitedParallelism(1), TTL eviction
│                     BleCapabilities chips, ScanPhy toggle, service filters
├── BeaconScreen ── BeaconScanner, iBeacon + Eddystone type filters
├── DeviceDetailScreen (hub) ── ConnectionSection, InfoSection (PHY/RSSI/MTU), NavigationCards
│   ├── ServiceExplorerScreen ── GATT dump, read/write/observe, benchmarks, L2CAP + Blob
│   ├── HeartRateDemoScreen ── HeartRateProfile, live BPM display
│   ├── BatteryDemoScreen ── BatteryProfile, read + notifications
│   ├── DeviceInfoDemoScreen ── DeviceInformationProfile, typed DIS fields
│   ├── DfuDemoScreen ── DfuController, progress tracking, firmware picker
│   ├── CodecDemoScreen ── BleDecoder, typed reads, decoder composition
│   └── MonitorScreen ── ConnectionQualityMonitor + PowerMonitor, path loss assessment
├── ServerScreen ── ServerViewModel (GATT + advertiser + L2CAP lifecycle)
└── Shared: BleViewModel ── PairingCoordinator, L2capController, BlobL2capController,
                              monitoring helpers, DFU orchestration
```

## Build & Run

```bash
# Android
./gradlew :sample-android:installDebug

# iOS
open iosApp/iosApp.xcodeproj
# Select "iosApp" scheme -> Run on device
```
