# ADR-0003: JVM desktop backends via ServiceLoader (Linux BlueZ + macOS CoreBluetooth)

## Status

Accepted (Gary, 2026-09-24). Supersedes the packaging and activation parts of [ADR-0001](ADR-0001-jvm-linux-bluez.md).

## Context

ADR-0001 shipped Linux BlueZ scan (M1) and GATT client (M2) directly inside the core `kmp-ble` `jvmMain` source set. The JVM scope is now **Linux (BlueZ) and macOS (CoreBluetooth)**. Keeping every desktop stack in core has three problems:

- Core would need OS-specific dependencies (bluez-dbus, dbus-java, junixsocket) and a macOS native library that can only be built on a macOS host, while the core JVM CI job runs on Ubuntu.
- Every JVM consumer would pull every desktop stack.
- The BlueZ peripheral re-implemented connection, discovery, and notification plumbing next to the common internals, and grew thread-safety and parity bugs (unsynchronized handle maps read on the D-Bus signal thread, MTU never updated, reconnection strategy ignored, wrong flag mapping).

## Decision

1. **Core owns a backend SPI and the generic implementations.** `kmp-ble` `jvmMain` exposes `com.atruedev.kmpble.backend`, gated by the `@KmpBleBackendApi` opt-in annotation. Core implements `Scanner`, `Peripheral`, `BluetoothAdapter`, `GattServer`, `Advertiser`, `ExtendedAdvertiser`, and `L2capListener` once, on top of the existing common internals (`PeripheralContext`, state machine, `GattOperationQueue`, `ObservationManager`, `ReconnectionHandler`, `ScannerPipeline`). Common internals stay `internal`.
2. **Backends are separate modules that implement plain-typed transports.** Transports speak handles (`Long` attribute handles, `String` device handles), `ByteArray`, and common error types (`BleException`, `GattStatus`, `ScanFailedException`). No backend type crosses into the public common API.
   - `kmp-ble-bluez`: Linux, BlueZ over the system D-Bus (hypfvieh bluez-dbus).
   - `kmp-ble-macos`: macOS **arm64 only**, macOS 11+, CoreBluetooth through an Objective-C shim called over **JNI**.
3. **Runtime selection through `ServiceLoader`.** Core loads `BleBackend` implementations, keeps those whose `isSupported()` returns true (OS and architecture check only, no I/O), and picks the highest `priority`. `-Dkmpble.backend=<id>` or `BleBackends.install(...)` overrides the choice. With no backend on the classpath, the portable factories throw `UnsupportedOperationException` exactly as before, so CI and headless consumers are unaffected.
4. **Adding the backend dependency is the opt-in.** `-Dkmpble.bluez.enabled` is removed. Backends stay lazy: nothing touches D-Bus or CoreBluetooth until a scan, connect, or server call.
5. **macOS port is independent of iOS.** The native shim reports every CoreBluetooth callback through one JNI entry point as a plain-typed event (kind, handle ids, status code, text, bytes), mirroring `AppleCallbackEvent` without CoreBluetooth types. `iosMain` is not touched.
6. **Native library packaging.** Gradle compiles the shim with the host `clang` (`-arch arm64 -mmacosx-version-min=11.0`) on macOS hosts, places it in the jar at `native/macos-arm64/libkmpble_macos.dylib`, and the backend extracts and loads it at runtime. Publishing `kmp-ble-macos` fails on a non-macOS host instead of shipping a jar without the dylib.

## Alternatives considered

| Alternative | Why rejected |
| --- | --- |
| Keep all stacks in core `jvmMain` | Forces macOS-only native builds and D-Bus jars on every JVM consumer and on the Ubuntu CI job |
| Backends implement `Peripheral` directly with core internals made public | Exposes the state machine, queue, and registries as public API; duplicates lifecycle logic per backend |
| Java FFM (Panama) instead of JNI | Requires JDK 22+ for consumers; JNI works on the current JDK 17 target |
| SimpleBLE Java bindings | BUSL-1.1 license needs a commercial license for commercial use |
| Kotlin/Native dylib reused from `iosMain` | Two runtimes in one process and a C-level API redesign; would also mean editing `iosMain` |
| Universal (arm64 + x86_64) dylib | Out of scope by decision: arm64 only |

## Consequences

**Positive**

- One implementation of connection, discovery, GATT queueing, observation, and reconnection for every desktop stack, fixing the BlueZ M2 thread-safety and MTU bugs structurally.
- Core JVM jar stays pure Kotlin and builds anywhere; OS dependencies live only where they are used.
- New desktop stacks (for example Windows) only need a transport module.

**Negative / constraints**

- **Breaking for BlueZ users (0.x):** add `com.atruedev:kmp-ble-bluez`; `BlueZ.ENABLE_PROPERTY` is gone; the unreleased `BlueZPeripheral` class is replaced by `Advertisement.toBlueZPeripheral()` returning `Peripheral`. See `MIGRATION.md`.
- **macOS TCC:** a packaged app must declare `NSBluetoothAlwaysUsageDescription` in its `Info.plist` or macOS terminates the process when CoreBluetooth starts. The same applies to the app that launched a plain `java` process, since TCC attributes Bluetooth use to the responsible process. The backend resolves the responsible app, refuses to start CoreBluetooth when its `Info.plist` lacks the key, and reports a clear error instead (`-Dkmpble.macos.usageDescriptionCheck=false` skips the check). Sandboxed apps also need the `com.apple.security.device.bluetooth` entitlement.
- **Notarization:** apps that notarize must sign the bundled dylib (it ships inside the jar).
- **Release pipeline:** `publish.yml` must publish `:kmp-ble-bluez` and `:kmp-ble-macos` from the macOS runner; CI needs a macOS job to build the dylib and run its JNI smoke test. Workflow changes need human review.
- **Linux L2CAP stays unsupported:** BlueZ exposes LE CoC only through `AF_BLUETOOTH` sockets, which need a Linux native library. Tracked as a follow-up.
- Physical hardware verification (Linux adapter, Mac) remains human-only per ADR-0002.

## Parity (JVM backends)

| Feature | Linux BlueZ | macOS CoreBluetooth |
| --- | --- | --- |
| Scan | Yes | Yes |
| Connect, discovery, read, write, notify, descriptors | Yes | Yes |
| MTU / `maximumWriteValueLength` | Negotiated MTU from BlueZ | `maximumWriteValueLength(for:)` |
| Reliable write | Yes (`WriteValue type=reliable`) | No (CoreBluetooth has no prepared-write API) |
| RSSI of a connected device | Last value BlueZ reported | Yes (`readRSSI`) |
| Bonding (`bondState`, `removeBond`, `BondingPreference.Required`) | Yes (`Pair`, `RemoveDevice`) | No (OS managed) |
| `PairingHandler` | Yes (`Agent1`) | No (OS dialog) |
| Adapter state | Yes (`Adapter1.Powered`) | Yes (`CBManager.state`) |
| Permissions | D-Bus access to `org.bluez` | `CBManager.authorization` |
| GATT server | Yes (`GattManager1`) | Yes (`CBPeripheralManager`) |
| Advertiser | Yes (`LEAdvertisingManager1`) | Yes (local name + service UUIDs only) |
| Extended advertiser | Multiple sets, no periodic advertising | One set, legacy PDUs |
| L2CAP channel / listener | No | Yes |
| PHY, connection parameters, subrating, ISO, PAST, direction finding | No | No |
