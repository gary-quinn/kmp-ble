# Platform Setup: macOS (JVM + CoreBluetooth)

This guide covers kmp-ble in JVM apps on macOS (Compose Desktop, Swing, CLI tools) through the `kmp-ble-macos` backend. The backend drives CoreBluetooth through a small Objective-C shim loaded over JNI and supports the central role (scan, connect, GATT client, L2CAP) and the peripheral role (GATT server, advertising, L2CAP listener). See [ADR-0003](adr/ADR-0003-jvm-backend-spi.md) for the design.

Native iOS and macOS Kotlin/Native targets are unrelated to this module; it is for JVM processes only.

## Requirements

- macOS 11 or later on **Apple silicon (arm64)**. Intel Macs are not supported.
- Java 17+ (an arm64 JDK)
- For packaged apps: an `Info.plist` with `NSBluetoothAlwaysUsageDescription` (see below)

## Gradle dependency

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.atruedev:kmp-ble:<version>")
        }
        jvmMain.dependencies {
            implementation("com.atruedev:kmp-ble-macos:<version>")
        }
    }
}
```

The jar bundles `native/macos-arm64/libkmpble_macos.dylib`. On first use the backend copies it to a temporary directory and loads it; set `-Dkmpble.macos.library=/absolute/path/libkmpble_macos.dylib` to load your own copy instead (for example one you signed yourself).

The module registers `MacosBackend` through `META-INF/services`, so `Scanner { }`, `Advertisement.toPeripheral()`, `BluetoothAdapter()`, `GattServer { }`, `Advertiser()`, `ExtendedAdvertiser()`, and `L2capListener()` use it automatically on macOS arm64. CoreBluetooth starts on the first scan, connect, adapter-state read, or server call, not at construction.

## Bluetooth permission (TCC)

macOS asks the user for Bluetooth access the first time a process starts CoreBluetooth, and attributes the request to the *responsible* app: the packaged `.app` itself, or for a plain `java` process the app that launched it (Terminal, iTerm, an IDE, or another tool). That app's `Info.plist` must contain `NSBluetoothAlwaysUsageDescription`; when it does not, macOS terminates the whole process with a TCC crash report the moment CoreBluetooth starts.

kmp-ble looks up the responsible app before starting CoreBluetooth and fails instead of being killed: scans report `ScanEvent.Failed` with `Macos.ERROR_USAGE_DESCRIPTION_MISSING` (the message names the app), connects throw `ConnectionFailed` with that `platformCode`, and `checkBlePermissions()` returns `PermanentlyDenied(["NSBluetoothAlwaysUsageDescription"])`. Apple's own apps under `/System` (Terminal.app) are not checked.

- **Packaged app (`.app`)**: add the key to the bundle's `Info.plist` (see below).
- **Plain `java` from a terminal or IDE**: run from Terminal.app or another host that declares the key, then grant access in System Settings > Privacy & Security > Bluetooth. A host that lacks the key (some IDEs and agent tools) cannot use Bluetooth for the processes it launches.
- **False positive**: if the check refuses a host that macOS actually accepts, set `-Dkmpble.macos.usageDescriptionCheck=false` (`Macos.USAGE_DESCRIPTION_CHECK_PROPERTY`) to skip it.

`checkBlePermissions()` reads `CBManager.authorization` without prompting: `Granted`, `Denied` (not determined yet), or `PermanentlyDenied` (denied or restricted).

### jpackage

Add the key to the generated `Info.plist` through a resource directory:

```bash
jpackage --type app-image --name MyBleApp --input build/libs --main-jar app.jar \
  --resource-dir packaging/macos
```

with `packaging/macos/Info.plist` based on the default template plus:

```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>MyBleApp uses Bluetooth to talk to your sensors.</string>
```

Compose Desktop users can set the same key through `nativeDistributions { macOS { infoPlist { extraKeysRawXml = "..." } } }`.

### Sandbox, hardened runtime, notarization

- Sandboxed apps need the `com.apple.security.device.bluetooth` entitlement.
- Notarization rejects unsigned native code, including dylibs inside jars. Either sign `libkmpble_macos.dylib` inside the jar with your Developer ID before notarizing, or extract it, sign it, and point `kmpble.macos.library` at the signed file.

## Feature notes

| Feature | CoreBluetooth behavior |
|---------|------------------------|
| Identifiers | `Identifier.value` is the `CBPeripheral` UUID, which is stable per Mac but is not the device MAC address. |
| Scan | Unfiltered `scanForPeripheralsWithServices` with duplicates allowed; filters run in core. `rawAdvertising` holds AD structures rebuilt from CoreBluetooth's advertisement dictionary. |
| Connect / discovery | `connectPeripheral` (no OS timeout; core applies `ConnectionOptions.timeouts.connect`), then services, characteristics, and descriptors. Cancelling a connect calls `cancelPeripheralConnection`. |
| MTU | `maximumWriteValueLength(for: .withoutResponse) + 3`. `requestMtu()` returns it; CoreBluetooth negotiates on its own. |
| Writes | `WithoutResponse` waits for `canSendWriteWithoutResponse`; `Signed` is sent as a write with response, as on iOS. `writeReliable()` is not supported. |
| CCCD | Writes to the Client Characteristic Configuration descriptor are translated to `setNotifyValue`, because CoreBluetooth forbids writing it directly. |
| RSSI | `readRSSI` on the connected peripheral. |
| Bonding | Managed by macOS: `bondState` stays `Unknown`, `removeBond()` returns `NotSupported`, and `PairingHandler` is not used. |
| Adapter state | `CBManager.state`, updated live. `getBondedDevices()` is empty. `capabilities` are fixed, not queried (CoreBluetooth on macOS has no feature API): LE 2M and LE Coded PHY `true` as on iOS (Apple silicon ships Bluetooth 5.x, the OS picks the PHY), extended and periodic advertising `false`. |
| GATT server | `CBPeripheralManager` services. CoreBluetooth manages CCCDs and reports no central connect/disconnect, so connections are inferred from requests and evicted when idle. Descriptors in the server DSL are not published. |
| Advertising | Local name and service UUIDs only, one set per process. Other `AdvertiseConfig` / `ExtendedAdvertiseConfig` fields are logged and ignored. |
| L2CAP | `openL2CAPChannel` and `publishL2CAPChannel`. The PSM encryption requirement is set by the listener; the `secure` flag of `openL2capChannel()` has no effect on macOS. |
| Not supported | PHY and connection-parameter requests, subrating, isochronous channels, PAST, direction finding, reliable writes. |

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `UnsupportedOperationException` from `Scanner { }` | `kmp-ble-macos` missing, Intel Mac, or a jar built without the dylib | Add the dependency; run an arm64 JDK on Apple silicon |
| Process exits with a TCC crash report | CoreBluetooth started outside kmp-ble, or with `kmpble.macos.usageDescriptionCheck=false`, under a responsible app without the usage description | Add `NSBluetoothAlwaysUsageDescription`, or launch from a host that has it |
| `ERROR_USAGE_DESCRIPTION_MISSING` from a plain `java` run | The terminal or IDE that launched the JVM lacks the key | Run from Terminal.app or a host that declares it |
| `ScanEvent.Failed` with `Macos.ERROR_UNAUTHORIZED` | Bluetooth access denied for the responsible app | Allow it in System Settings > Privacy & Security > Bluetooth |
| `ScanEvent.Failed` with `Macos.ERROR_ADAPTER_OFF` | Bluetooth is off | Turn Bluetooth on |
| `ConnectionFailed` with `UNKNOWN_DEVICE` | CoreBluetooth has not seen the identifier in this process | Scan first, or use an identifier from a previous scan on the same Mac |
| A bonded peripheral no longer shows up in scans | Some peripherals stop advertising in a form CoreBluetooth reports to scanners once they are bonded | Build an `Advertisement` from the stored identifier and call `toPeripheral().connect()`; CoreBluetooth retrieves the known peripheral and connects when it is reachable (`sample-jvm connect` does this) |

## CI note

The dylib is compiled by Gradle on macOS hosts (`xcrun clang -arch arm64`). On Linux CI the module's tests run against a fake native API, and the JNI smoke test is skipped. Publishing `kmp-ble-macos` fails on a non-macOS host so a jar without the native library is never released. Hardware checks are manual (see `TESTING.md`).

## See also

- [ADR-0003: JVM backend SPI](adr/ADR-0003-jvm-backend-spi.md)
- [Platform Setup: Linux](platform-setup-linux.md)
- [Platform Setup: iOS](platform-setup-ios.md)
