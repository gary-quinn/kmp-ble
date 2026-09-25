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

Run with `sample-jvm` on each desktop OS before tagging a release. Human only. Add `-Dkmpble.log=true` to see state transitions, for example `./gradlew :sample-jvm:run -Dkmpble.log=true --args="connect <identifier> 15"`.

### Prerequisites

- Linux: BlueZ 5.62+ with `bluetoothd` running and access to `org.bluez` on the system bus (the `bluetooth` group or the distribution default policy)
- macOS: Apple silicon, macOS 11+, a terminal app that declares `NSBluetoothAlwaysUsageDescription` and has Bluetooth access
- A BLE peripheral with a readable and a notifying characteristic; one that requires pairing for D8
- A phone with a generic BLE client app for D9

| # | Scenario | Steps | Pass Criteria |
|---|----------|-------|---------------|
| D1 | Permissions | `./gradlew :sample-jvm:run --args="permissions"` | `Granted` (macOS: after allowing the prompt once) |
| D2 | Adapter state | `--args="adapter"`, then toggle Bluetooth | `state=On`; off/on reflected on rerun |
| D3 | Scan | `--args="scan 10"` | Nearby advertisements with names, RSSI, service UUIDs; the scan ends after 10 s |
| D4 | Connect + GATT | `--args="connect <identifier> 15"` | Services listed, readable values printed, `mtu` above 23 on BlueZ 5.62+ |
| D5 | Notifications | Same run as D4, then run it again | `notify` lines appear; the second run behaves the same, no `Disabling notifications ... failed` warnings |
| D6 | Remote disconnect | Power off the peripheral during D4 | State ends in `Disconnected.ByError(ConnectionLost)`, no hang |
| D7 | Bluetooth off | Turn Bluetooth off during D4 | `Disconnected.BySystemEvent`, process exits cleanly |
| D8 | Linux pairing | `--args="bond <identifier> 15"` against a device that needs pairing; answer the prompt on stdin. `--args="unbond <identifier>"` resets it | The `pairing:` line shows the prompt; `connected:` reports `bond=Bonded`; `adapter` lists the device as bonded |
| D9 | GATT server + advertising | `--args="server 60"`; from the phone connect to `kmp-ble-sample`, read and write `6b6d7062-6c65-4e00-8000-000000000002`, subscribe to `...0003` | Phone sees the name and service `6b6d7062-6c65-4e00-8000-000000000001`; reads and writes are logged in order and the echo reads back the last write; counter notifications arrive |
| D10 | macOS packaged app | `./gradlew :sample-jvm:packageMacApp`, then `open -W --stdout out.txt --stderr out.txt sample-jvm/build/jpackage/without-usage-description/KmpBleSample.app --args scan 5` (also `permissions`, `connect <identifier> 5`) | `ERROR_USAGE_DESCRIPTION_MISSING` (-33) for scan and connect, `PermanentlyDenied` for permissions, no crash report in `~/Library/Logs/DiagnosticReports` |
| D10b | macOS packaged app with the key | `./gradlew :sample-jvm:packageMacApp -Pkmpble.sample.usageDescription=true`, launch from `build/jpackage/with-usage-description` | The Bluetooth prompt appears once; after allowing it, D1-D3 pass |
| D11 | macOS host app | `./gradlew :sample-jvm:installSampleLibs`, then from each host run `java -cp 'sample-jvm/build/sample/lib/*' com.atruedev.kmpble.sample.jvm.MainKt scan 5` (and `permissions`, `adapter`): Terminal.app, then a host without the key | Terminal.app is not terminated and scans once Bluetooth is allowed for it; the other host gets `ERROR_USAGE_DESCRIPTION_MISSING` naming that app, no crash report |

Launch `java` directly for D11 so that the host under test starts the JVM, rather than a Gradle daemon started from another host.

### BlueZ without a radio

`bluetoothd` can be exercised end to end on any Linux VM through virtual controllers. This covers the D-Bus integration, not RF behavior, and does not replace the hardware run.

1. Load `hci_vhci` (Ubuntu: `linux-modules-extra-$(uname -r)`) and start two virtual controllers with `btvirt -l2` (BlueZ `emulator/`, packaged as `bluez-test-tools` on Ubuntu). Use dual-mode controllers: the LE-only type (`-L`) sends a single advertising report when a scan starts, so `bluetoothd` never lists the peer.
2. Before starting `bluetoothd`, bring the second controller down (`hciconfig hci1 down`) and bind a user-space BLE host stack to it through an HCI user channel to act as the peer: a peripheral with a readable, a notifying, and an authenticated characteristic for D3-D8, and a central that writes and subscribes for D9. Keep its advertising data within 31 bytes.
3. Run `sample-jvm` with `-Dkmpble.bluez.adapter=hci0`. Drop the link from the peer for D6 and use `bluetoothctl power off` for D2 and D7.
