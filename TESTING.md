# Testing Guide

## Automated Tests

### Common Tests (all platforms)

Unit tests using Fake* test doubles. Runs on JVM, iOS simulator, and Android.

```bash
./gradlew iosSimulatorArm64Test   # Common tests on iOS
```

### Android Host Tests (JVM)

Tests Android constant mappings, quirk registry, and L2CAP socket logic.
Runs on JVM without a device or emulator.

```bash
./gradlew testAndroidHostTest
```

### Android Instrumented Tests (emulator/device)

Tests Android framework integration: BroadcastReceiver lifecycle, ScanFilter
construction, permissions, HandlerThread dispatch, and GATT type construction.
Requires a connected device or running emulator.

```bash
./gradlew connectedAndroidDeviceTest
```

### JVM Concurrency Tests (Lincheck)

Stress tests for concurrent access patterns in GattOperationQueue and
PeripheralRegistry.

```bash
./gradlew jvmTest
```

### JVM Backend Tests

The shared JVM implementations run against fake transports in core, and each
backend runs against fakes of its OS layer. On a macOS arm64 host,
`kmp-ble-macos` also rebuilds the CoreBluetooth shim and runs a JNI smoke test
that never starts CoreBluetooth.

```bash
./gradlew :jvmTest --tests "com.atruedev.kmpble.backend.*"
./gradlew :kmp-ble-bluez:jvmTest :kmp-ble-macos:jvmTest
```

## Manual E2E Test Checklist

Real BLE operations require physical hardware. Complete this checklist using
the `sample` app before tagging a release.

### Prerequisites

- Android device running API 33+
- BLE peripheral (e.g., nRF52 DK, Heart Rate sensor, or second Android device)
- `sample` app installed on the Android device

### Scanner

| # | Scenario | Steps | Pass Criteria |
|---|----------|-------|---------------|
| 1 | Scan for peripherals | Open Scanner tab, start scan | Nearby BLE advertisements appear |
| 2 | Filter by service UUID | Set Heart Rate (180D) filter, scan | Only HR devices shown |
| 3 | Scan timeout | Set 5s timeout, scan | Scan stops after 5 seconds |
| 4 | Stop scan | Start scan, tap stop | Scan stops, no more results |

### Connection

| # | Scenario | Steps | Pass Criteria |
|---|----------|-------|---------------|
| 5 | Connect to peripheral | Tap advertisement, connect | State transitions to Connected |
| 6 | Discover services | Connect to peripheral | Services and characteristics listed |
| 7 | Read characteristic | Tap Read on a readable characteristic | Value displayed |
| 8 | Write characteristic | Enter value, tap Write | Device acknowledges, no error |
| 9 | Subscribe to notifications | Tap Observe on a notify characteristic | Real-time values stream in |
| 10 | Disconnect | Tap Disconnect | State transitions to Disconnected |
| 11 | Reconnection | Disconnect, then reconnect | Connection re-established |

### Bonding

| # | Scenario | Steps | Pass Criteria |
|---|----------|-------|---------------|
| 12 | Create bond | Connect, initiate bonding | Bond state transitions to Bonded |
| 13 | Bond survives reconnect | Bond, disconnect, reconnect | Bond state remains Bonded |

### GATT Server

| # | Scenario | Steps | Pass Criteria |
|---|----------|-------|---------------|
| 14 | Start GATT server | Open Server tab, start server | Server opens without error |
| 15 | Advertise | Start advertising | Advertisement visible from another device |
| 16 | Client read | Connect from another device, read characteristic | Correct value returned |
| 17 | Client write | Write from another device | onWrite handler fires, data received |
| 18 | Notify client | Subscribe from client, trigger notification | Client receives data |

### L2CAP

| # | Scenario | Steps | Pass Criteria |
|---|----------|-------|---------------|
| 19 | Open L2CAP channel | Connect, open L2CAP channel | Channel opens without error |
| 20 | Bidirectional transfer | Send data in both directions | Data received correctly |

### DFU (if applicable)

| # | Scenario | Steps | Pass Criteria |
|---|----------|-------|---------------|
| 21 | Nordic Secure DFU | Select firmware .zip, start DFU | Progress reported, DFU completes |
| 22 | MCUboot SMP | Select firmware, start SMP upload | Upload completes |

### Edge Cases

| # | Scenario | Steps | Pass Criteria |
|---|----------|-------|---------------|
| 23 | Bluetooth off during scan | Turn off Bluetooth while scanning | Scan stops gracefully, error reported |
| 24 | Bluetooth off during connection | Turn off Bluetooth while connected | Disconnection event emitted |
| 25 | Out of range | Walk away from peripheral | ConnectionLost error, reconnection if configured |
| 26 | Rapid connect/disconnect | Connect and disconnect 5 times quickly | No crashes, state machine consistent |

## JVM Desktop Checklist (Linux BlueZ, macOS arm64)

Run with `sample-jvm` on each desktop OS before tagging a release. Human only.

### Prerequisites

- Linux: BlueZ 5.62+ with `bluetoothd` running, the user in the `bluetooth` group
- macOS: Apple silicon, macOS 11+, Bluetooth allowed for the terminal app
- A BLE peripheral with a notifying characteristic (for example a heart-rate sensor or nRF Connect on a phone)

| # | Scenario | Steps | Pass Criteria |
|---|----------|-------|---------------|
| D1 | Permissions | `./gradlew :sample-jvm:run --args="permissions"` | `Granted` (macOS: after allowing the prompt once) |
| D2 | Adapter state | `--args="adapter"`, then toggle Bluetooth | `state=On`; off/on reflected on rerun |
| D3 | Scan | `--args="scan 10"` | Nearby advertisements with names, RSSI, service UUIDs |
| D4 | Connect + GATT | `--args="connect <identifier> 15"` | Services listed, readable values printed, `mtu` above 23 on BlueZ 5.62+ |
| D5 | Notifications | Same run as D4 | `notify` lines appear; they stop after the run (CCCD disabled) |
| D6 | Remote disconnect | Power off the peripheral during D4 | State ends in `Disconnected`, no hang |
| D7 | Bluetooth off | Turn Bluetooth off during D4 | `Disconnected.BySystemEvent`, process keeps running |
| D8 | Linux pairing | Connect with `BondingPreference.Required` and a `pairingHandler` to a device that needs pairing | Handler receives the prompt; `bondState` becomes `Bonded` |
| D9 | GATT server + advertising | Start `GattServer { }` and `Advertiser()`; connect from a phone | Phone sees the service, reads and writes reach the handlers |
| D10 | macOS packaged app | Package with jpackage **without** `NSBluetoothAlwaysUsageDescription` | kmp-ble reports `ERROR_USAGE_DESCRIPTION_MISSING` instead of the process being killed |
| D11 | macOS host app | Run D1-D3 from Terminal.app, then from a terminal app without the key | Terminal.app prompts and scans; the other host gets `ERROR_USAGE_DESCRIPTION_MISSING` naming that app, no crash report |

