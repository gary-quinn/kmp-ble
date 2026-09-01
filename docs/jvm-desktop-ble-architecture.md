# JVM Desktop BLE Architecture

> **Status:** Draft RFC (not implemented) | **Date:** 2026-09-01
>
> Design for real BLE support on the existing `jvm()` KMP target. Today JVM is a
> stub platform used for unit tests and Lincheck concurrency tests. This doc
> defines how to turn it into a Linux desktop BLE runtime without changing the
> public API surface in `commonMain`.

---

## Table of Contents

1. [What exists today](#1-what-exists-today)
2. [Goals and non-goals](#2-goals-and-non-goals)
3. [Platform strategy](#3-platform-strategy)
4. [Native stack choice](#4-native-stack-choice)
5. [Architecture](#5-architecture)
6. [Desktop initialization API](#6-desktop-initialization-api)
7. [Permissions and lifecycle](#7-permissions-and-lifecycle)
8. [Feature parity matrix](#8-feature-parity-matrix)
9. [Implementation inventory](#9-implementation-inventory)
10. [Phased plan](#10-phased-plan)
11. [Testing strategy](#11-testing-strategy)
12. [CI and release](#12-ci-and-release)
13. [Risks and mitigations](#13-risks-and-mitigations)
14. [Open decisions](#14-open-decisions)
15. [References](#15-references)

---

## 1. What exists today

### JVM target role

The `jvm()` target is configured in the core module and all satellite modules
(codec, profiles, dfu, benchmark, mesh). It compiles and publishes a JVM
artifact, but **does not provide a BLE runtime**.

| Asset | Location | Role today |
| --- | --- | --- |
| Stub actuals | `src/jvmMain/` (19 files) | `unsupportedBle()` or no-op |
| Fake test doubles | `src/commonMain/.../testing/` | Hardware-free logic tests |
| Conformance tests | `src/jvmTest/` (~30 files) | State machine, GATT queue, fakes |
| Lincheck tests | `src/jvmTest/` | Concurrent access stress tests |
| `JvmGattCache` | `gatt/cache/JvmGattCache.kt` | No-op stub; comment mentions D-Bus |

Central stub:

```kotlin
// src/jvmMain/kotlin/com/atruedev/kmpble/UnsupportedBle.kt
internal fun unsupportedBle(operation: String): Nothing =
    throw UnsupportedOperationException(
        "$operation is not supported on JVM - BLE requires Android or iOS",
    )
```

### What already works on JVM

- `BleData` constructors (byte buffer wrapper)
- All `commonMain` logic when driven by `FakePeripheral`, `FakeScanner`, etc.
- Satellite modules (codec, profiles, dfu) compile against JVM stubs

### What does not work

Every public factory throws or returns `NotSupported`:

| expect declaration | JVM behavior today |
| --- | --- |
| `BluetoothAdapter()` | throws |
| `Scanner { }` | throws |
| `Advertisement.toPeripheral()` | throws |
| `GattServer { }` | throws |
| `Advertiser()` / `ExtendedAdvertiser()` | throws |
| `L2capListener()` | `L2capException.NotSupported` |
| `PeriodicAdvertisingSync(...)` | `PastException.NotSupported` |
| `IsochronousListener()` | `IsochronousException.NotSupported` |
| `checkBlePermissions()` | `PermissionResult.Denied` |
| `enableStateRestoration(...)` | no-op |
| `createGattCache()` | no-op stub |

### Reference implementations

Android and iOS each implement the same bridge pattern:

```
OS callback thread
    -> sealed callback event (GattCallbackEvent / AppleCallbackEvent)
        -> CompletableDeferred / Channel
            -> commonMain serialized dispatcher (limitedParallelism(1))
                -> suspend consumer API
```

Android reference: `AndroidGattBridge` maps `BluetoothGattCallback` to
`GattCallbackEvent`. iOS reference: `ApplePeripheralBridge` maps
`CBPeripheralDelegate` to `AppleCallbackEvent`.

The JVM implementation should introduce `JvmGattBridge` with
`JvmCallbackEvent`, following the Android model more closely because BlueZ
GATT objects map 1:1 to Android GATT concepts.

---

## 2. Goals and non-goals

### Goals (v1)

1. **Linux desktop central role** - scan, connect, GATT read/write/observe on
   the existing `jvm()` target.
2. **No public API changes** - all work stays in `jvmMain/`; `commonMain`
   factories remain `expect fun`.
3. **Android bridge parity** - reuse `GattCallbackEvent` semantics where
   possible so `JvmPeripheralConnection` can share handler patterns with
   `AndroidPeripheralConnection`.
4. **Dogfood path** - CLI tools, test harnesses, CI hardware-in-the-loop, and
   headless Linux servers can use kmp-ble without an Android emulator or iOS
   simulator.

### Non-goals (v1 and possibly permanent)

| Item | Reason |
| --- | --- |
| macOS / Windows desktop | Separate native stacks; defer until Linux validates architecture |
| KMP `linuxX64` native target | JVM + D-Bus is sufficient for v1; native cinterop adds CI cost |
| L2CAP CoC | BlueZ L2CAP socket API differs from Android/iOS; keep `NotSupported` |
| LE Audio (isochronous) | No desktop stack path; keep `NotSupported` |
| Periodic advertising sync | Rare on desktop; keep `NotSupported` |
| BLE Mesh on JVM | Already `MeshNotSupported`; out of scope |
| `kmp-ble-quirks` on JVM | Android OEM workarounds; desktop uses empty quirk registry |
| Compose Desktop sample | Optional later; not required for library support |
| Publishing a separate artifact | Extend existing `jvm` publication |

---

## 3. Platform strategy

### Extend `jvm()`, do not add desktop KMP targets

The repo already compiles `jvmMain` across all modules. Extending it avoids:

- New source sets (`linuxX64Main`, etc.)
- New cinterop / JNI build pipelines per OS
- Fragmenting the Maven artifact (one `jvm` jar vs many native klibs)

### Linux-first rationale

| Factor | Linux | macOS JVM | Windows JVM |
| --- | --- | --- | --- |
| BLE stack | BlueZ (standard, D-Bus) | CoreBluetooth via JNI (cannot reuse `iosMain`) | WinRT BLE via JNI |
| Dev/CI cost | Low (GitHub Actions `ubuntu-latest`) | Needs macOS runner + JNI bridge | Needs Windows runner |
| Hint in codebase | `JvmGattCache` mentions D-Bus | None | None |
| Headless server use case | Strong (Docker, CI, edge) | Moderate | Moderate |

macOS and Windows become **Phase 5+** options after the bridge architecture
is proven on Linux.

### Runtime requirements (Linux v1)

- Linux kernel with BlueZ 5.50+ (BLE 4.2+ GATT client)
- `bluetoothd` running with D-Bus system bus access
- User in `bluetooth` group (or polkit rule) for D-Bus `org.bluez`
- Physical BLE adapter or USB dongle (e.g. Intel, Realtek, Nordic)

---

## 4. Native stack choice

### Recommended: BlueZ over D-Bus via dbus-java

[BlueZ](https://www.bluez.org/) exposes BLE through D-Bus on the system bus:

| D-Bus interface | Purpose |
| --- | --- |
| `org.bluez.Adapter1` | Power, discovery, adapter properties |
| `org.bluez.Device1` | Connect, pair, device properties |
| `org.bluez.GattService1` | Service discovery |
| `org.bluez.GattCharacteristic1` | Read, write, notify |
| `org.bluez.GattDescriptor1` | CCCD and other descriptors |
| `org.freedesktop.DBus.ObjectManager` | Tree enumeration on adapter |

**Binding:** [dbus-java](https://github.com/hypfvieh/dbus-java) (pure JVM,
actively maintained). No native `.so` to ship; connects to the system D-Bus
socket.

### Alternatives considered

| Option | Pros | Cons | Verdict |
| --- | --- | --- | --- |
| **dbus-java** | Pure JVM, coroutine-friendly signals | Hand-roll BlueZ object paths | **Recommended** |
| tinyb (JNI) | Thin BlueZ wrapper | Unmaintained, native lib per arch | Reject |
| HCI raw sockets | Full control | Bypasses kernel GATT; security/compat issues | Reject |
| Separate process (`bluetoothctl`) | Quick spike | No async notify, fragile parsing | Spike only |

### D-Bus connection model

```
JvmBluezConnection (singleton per process)
    |
    +-- system bus (DBusConnectionBuilder.forSystemBus)
    +-- ObjectManager on / (or adapter path)
    +-- signal handlers (InterfacesAdded, PropertiesChanged)
    +-- method calls (Connect, ReadValue, StartNotify, ...)
```

Coroutine integration:

- D-Bus signal callbacks arrive on a dbus-java thread pool
- Bridge posts `JvmCallbackEvent` and completes `CompletableDeferred`
- Consumer-facing work runs on `limitedParallelism(1)` (same as Android/iOS)

---

## 5. Architecture

### Layer diagram

```
+-------------------------------------------------------------+
|  Consumer (CLI, server, test)                               |
|    Scanner / Peripheral / BluetoothAdapter                  |
+-------------------------------------------------------------+
|  commonMain                                                 |
|    State machine, GattOperationQueue, observations, errors  |
+-------------------------------------------------------------+
|  jvmMain (new)                                              |
|    JvmPeripheral, JvmScanner, JvmBluetoothAdapter           |
|    JvmGattBridge  --JvmCallbackEvent-->  handlers           |
+-------------------------------------------------------------+
|  dbus-java                                                  |
|    system bus, org.bluez.* interfaces                       |
+-------------------------------------------------------------+
|  BlueZ / kernel                                             |
|    bluetoothd, HCI, USB dongle                              |
+-------------------------------------------------------------+
```

### Proposed `jvmMain` file layout

```
src/jvmMain/kotlin/com/atruedev/kmpble/
  KmpBle.jvm.kt                    # Desktop init API
  UnsupportedBle.kt                # Remove or narrow to unimplemented features
  adapter/
    AdapterFactory.jvm.kt          # actual -> JvmBluetoothAdapter
    JvmBluetoothAdapter.kt
  bluez/
    JvmBluezConnection.kt          # D-Bus singleton, bus lifecycle
    JvmBluezPaths.kt               # Object path helpers
    JvmBluezProperties.kt          # Property change parsing
    JvmBluezErrors.kt              # D-Bus error -> kmp-ble exceptions
  scanner/
    ScannerFactory.jvm.kt          # actual -> JvmScanner
    JvmScanner.kt
    JvmAdvertisementParser.kt
  peripheral/
    PeripheralFactory.jvm.kt       # actual -> JvmPeripheral
    JvmPeripheral.kt
    JvmPeripheralConnection.kt
    JvmPeripheralInternal.kt
    JvmGattBridge.kt
    JvmCallbackEvent.kt            # Mirror GattCallbackEvent (platform-neutral types)
    JvmGattStatusMapper.kt
  gatt/cache/
    JvmGattCache.kt                # Real file-backed cache
  permissions/
    BlePermissions.jvm.kt          # Check adapter powered + D-Bus access
  server/
    GattServerFactory.jvm.kt       # Phase 4: throws or NotSupported until implemented
  ... (l2cap, isochronous, periodic: keep existing NotSupported stubs)
```

### `JvmCallbackEvent` design

Mirror `GattCallbackEvent` but use kmp-ble types instead of Android classes:

```kotlin
internal sealed interface JvmCallbackEvent {
    data class ConnectionStateChanged(val connected: Boolean, val error: String?) : JvmCallbackEvent
    data class ServicesDiscovered(val services: List<JvmGattService>, val error: String?) : JvmCallbackEvent
    data class CharacteristicRead(val path: String, val value: ByteArray, val error: String?) : JvmCallbackEvent
    data class CharacteristicWrite(val path: String, val error: String?) : JvmCallbackEvent
    data class CharacteristicChanged(val path: String, val value: ByteArray) : JvmCallbackEvent
    data class DescriptorRead(val path: String, val value: ByteArray, val error: String?) : JvmCallbackEvent
    data class DescriptorWrite(val path: String, val error: String?) : JvmCallbackEvent
    data class MtuChanged(val mtu: Int) : JvmCallbackEvent
}
```

`JvmGattService` holds D-Bus object paths and UUIDs. Handlers translate paths
to `DiscoveredService` / `GattCharacteristic` in commonMain terms.

### Shared handler logic (optional refactor)

If `AndroidPeripheralConnection` and `JvmPeripheralConnection` diverge only in
native object types, consider extracting a `commonJvmAndroidMain` source set
later. **Do not do this in Phase 1** - copy the Android handler structure
first, refactor when duplication is proven.

### Scan flow

```
Scanner.scanEvents (Flow)
    <- toScanEvents() [commonMain, unchanged]
    <- JvmScanner raw Flow<Advertisement>
        <- D-Bus InterfacesAdded / PropertiesChanged on adapter
        <- StartDiscovery / StopDiscovery on Adapter1
        <- JvmAdvertisementParser (ManufacturerData, ServiceUUIDs from Device1 props)
```

Filter groups from `ScannerConfig` are applied in commonMain (`toScanEvents`).
JvmScanner emits all advertisements; filtering stays shared.

### Connect + GATT flow

```
peripheral.connect()
    -> Device1.Connect() via D-Bus
    -> JvmGattBridge.onConnectionStateChanged
        -> commonMain state machine (Connecting -> Discovering -> ...)
    -> GATT tree via ObjectManager.GetManagedObjects
    -> ReadValue / WriteValue / StartNotify on GattCharacteristic1
    -> PropertiesChanged (Value) -> CharacteristicChanged
```

MTU: BlueZ exposes `MTU` property on `GattCharacteristic1`. Negotiation may
differ from Android `requestMtu()`; map best-effort and document limitations.

---

## 6. Desktop initialization API

Android requires `KmpBle.init(context)`. JVM needs an equivalent entry point
without `Context`.

### Proposed API

```kotlin
// src/jvmMain/kotlin/com/atruedev/kmpble/KmpBle.jvm.kt
public object KmpBle {
    /**
     * Initialize desktop BLE. Must be called once before any BLE operation.
     *
     * @param adapterAddress Bluetooth adapter address (e.g. "hci0" path hint or
     *   MAC). Null selects the default adapter.
     * @param dbusAddress D-Bus address override. Null uses system bus
     *   (/var/run/dbus/system_bus_socket).
     */
    public fun initDesktop(
        adapterAddress: String? = null,
        dbusAddress: String? = null,
    )

    internal fun requireBluez(): JvmBluezConnection
}
```

`KmpBle` on JVM and Android are separate `actual` types (or a common
`expect object` if we add `commonMain` declaration later). **Phase 1:** JVM-only
`object` in `jvmMain` to avoid API churn on mobile.

### Adapter selection

1. List adapters via `org.bluez` manager or `GetManagedObjects`
2. Match `adapterAddress` to `Adapter1.Address` or object path `/org/bluez/hciN`
3. Fail fast with clear error if no adapter or Bluetooth powered off

---

## 7. Permissions and lifecycle

### Permission model

Desktop has no runtime permission dialog. Replace Android/iOS checks with:

| Check | Implementation |
| --- | --- |
| D-Bus access | Try `DBusConnectionBuilder.forSystemBus()`; catch `DBusExecutionException` |
| Adapter powered | Read `Adapter1.Powered` property |
| Adapter present | At least one `org.bluez.Adapter1` object |

```kotlin
public actual fun checkBlePermissions(): PermissionResult {
    // Granted if D-Bus connects and default adapter is powered
    // Denied with actionable message: "Add user to bluetooth group" etc.
}
```

### Lifecycle

- `JvmBluezConnection` is process-singleton (like iOS `CentralManagerProvider`)
- `Scanner.close()` stops discovery, does not tear down bus
- Process exit disconnects D-Bus automatically
- No state restoration on desktop v1 (`enableStateRestoration` remains no-op)

### Bonding / pairing

BlueZ handles pairing via `Device1.Pair()`. Pin entry may require an agent
registered on D-Bus (`org.bluez.Agent1`). Phase 3 scope:

- Default agent: auto-accept / NoInputNoOutput for headless CI
- Document limitation: interactive pairing needs consumer-provided agent

---

## 8. Feature parity matrix

| Feature | Android | iOS | JVM v1 target | JVM v1 notes |
| --- | --- | --- | --- | --- |
| `BluetoothAdapter()` | Yes | Yes | **Phase 1** | Powered, address, name |
| `Scanner` | Yes | Yes | **Phase 1** | Legacy + extended adv via Device1 props |
| `Peripheral.connect()` | Yes | Yes | **Phase 2** | |
| GATT read/write | Yes | Yes | **Phase 2** | |
| GATT notify/observe | Yes | Yes | **Phase 2** | `StartNotify` + PropertiesChanged |
| MTU negotiation | Yes | Partial | **Best effort** | BlueZ MTU property |
| Bonding | Yes | Limited | **Phase 3** | Agent required |
| RSSI | Yes | Yes | **Phase 3** | `Device1.RSSI` on discovery |
| PHY / connection priority | Yes | Limited | **No** | Not exposed on BlueZ D-Bus client |
| `GattServer` / `Advertiser` | Yes | Yes | **Phase 4** | BlueZ peripheral mode |
| L2CAP | Yes | Yes | No | Keep `NotSupported` |
| Isochronous | Yes | Yes | No | Keep `NotSupported` |
| Periodic adv sync | Yes | Yes | No | Keep `NotSupported` |
| State restoration | Yes | Yes | No | No-op |
| Quirks | Yes | Partial | No | Empty registry |
| `createGattCache()` | Yes | Yes | **Phase 2** | File-backed in user cache dir |

---

## 9. Implementation inventory

### Replace stub actuals (by phase)

| File | Phase | Work |
| --- | --- | --- |
| `adapter/AdapterFactory.jvm.kt` | 1 | Wire `JvmBluetoothAdapter` |
| `scanner/ScannerFactory.jvm.kt` | 1 | Wire `JvmScanner` |
| `permissions/BlePermissions.jvm.kt` | 1 | D-Bus + powered check |
| `KmpBle.jvm.kt` (new) | 1 | `initDesktop()` |
| `bluez/*` (new) | 1-2 | D-Bus client layer |
| `peripheral/PeripheralFactory.jvm.kt` | 2 | Wire `JvmPeripheral` |
| `gatt/cache/JvmGattCache.kt` | 2 | Real cache |
| `gatt/internal/ObservationPersistence.jvm.kt` | 2 | File-backed observations |
| `quirks/QuirkRegistry.jvm.kt` | 2 | Keep empty registry |
| `quirks/DeviceInfo.jvm.kt` | 2 | Return desktop placeholder |
| `server/GattServerFactory.jvm.kt` | 4 | Implement or keep throw |
| `l2cap/*`, `isochronous/*`, `periodic/*` | - | No change |
| `connection/StateRestorationApi.jvm.kt` | - | No change (no-op) |

### Gradle dependencies (jvmMain)

```kotlin
// build.gradle.kts - jvmMain.dependencies
implementation("com.github.hypfvieh:dbus-java-core:<version>")
implementation("com.github.hypfvieh:dbus-java-transport-junixsocket:<version>")
```

Exact version pinned in `gradle/libs.versions.toml`. junixsocket transport
connects to `/var/run/dbus/system_bus_socket` without native dbus libs.

### Error mapping

Introduce `JvmGattStatusMapper` analogous to `AndroidGattStatusMapper` /
`IosGattStatusMapper`. Map D-Bus errors (`org.bluez.Error.NotReady`,
`org.freedesktop.DBus.Error.AccessDenied`) to existing `BleException` types in
`commonMain` so consumers see consistent errors across platforms.

---

## 10. Phased plan

### Phase 0 - Spike (1 week, no merge requirement)

- [ ] Shell script: scan via `busctl` / `dbus-send` on Linux with USB dongle
- [ ] Kotlin main: connect dbus-java, list adapters, start discovery
- [ ] Document object paths and property names for target BlueZ version
- [ ] Validate CI runner has no Bluetooth (tests must skip gracefully)

**Exit criteria:** Demonstrate one device discovered and connected via D-Bus
outside kmp-ble.

### Phase 1 - Adapter + Scanner (1-2 weeks)

- [ ] `KmpBle.initDesktop()`
- [ ] `JvmBluezConnection` singleton
- [ ] `JvmBluetoothAdapter` - state, powered, address
- [ ] `JvmScanner` - `Flow<ScanEvent>` via discovery
- [ ] `checkBlePermissions()` - real checks
- [ ] Unit tests with mocked D-Bus (or skip-if-no-adapter integration test)
- [ ] `docs/platform-setup-jvm.md` integration guide

**Exit criteria:** `./gradlew jvmTest` passes; manual scan lists devices on Linux.

### Phase 2 - Central GATT (2-3 weeks)

- [ ] `JvmGattBridge` + `JvmCallbackEvent`
- [ ] `JvmPeripheral` + `JvmPeripheralConnection`
- [ ] Connect, discover services, read, write, observe
- [ ] `JvmGattCache` + `ObservationPersistence` (file-backed)
- [ ] Error mapping
- [ ] Hardware-in-the-loop test (optional CI job on self-hosted runner)

**Exit criteria:** Sample GATT workflow (HR service 0x180D) works on Linux;
existing `jvmTest` Fake* tests still pass.

### Phase 3 - Bonding + polish (1-2 weeks)

- [ ] `Device1.Pair()` integration
- [ ] Headless pairing agent for CI
- [ ] RSSI in scan results
- [ ] Connection parameter / timeout tuning (document limits)
- [ ] Troubleshooting doc updates

### Phase 4 - Peripheral role (optional, 2+ weeks)

- [ ] `GattManager1.RegisterApplication`
- [ ] `LEAdvertisingManager1` for `Advertiser`
- [ ] Only if product need confirmed; BlueZ peripheral support varies by adapter

### Phase 5+ - Other desktop OS (future RFC)

- macOS: JNI to CoreBluetooth or IOBluetooth
- Windows: WinRT `Windows.Devices.Bluetooth` via JNA
- Evaluate separate artifacts if stub-vs-real JVM split is needed

---

## 11. Testing strategy

### Keep existing tests

All `jvmTest` Fake* and Lincheck tests must continue passing unchanged. They
validate `commonMain` without hardware.

### New tests

| Layer | Type | Location |
| --- | --- | --- |
| D-Bus parsing | Unit | `jvmTest/.../bluez/` |
| Advertisement parser | Unit | `jvmTest/.../scanner/` |
| GATT status mapper | Unit | `jvmTest/.../peripheral/` |
| Scan + connect | Integration (hardware) | `jvmTest/.../integration/` or separate source set |
| Conformance | Extend existing | Reuse Fake* patterns where possible |

### Integration test pattern

```kotlin
@EnabledIfBluetoothAvailable // custom JUnit condition
class JvmBleIntegrationTest {
    @Test fun scanFindsDevices() { ... }

    @Test fun connectAndReadHeartRate() { ... }
}
```

Skip when:

- Not Linux
- No `org.bluez` on D-Bus
- `BLUETOOTH_HIL=0` env var set

Mirror Android `androidDeviceTest` pattern (`RecordingAndroidGattBridge`).

### Manual E2E checklist

Add JVM section to `TESTING.md`:

1. Linux machine with USB BLE dongle
2. `KmpBle.initDesktop()`
3. Scan, connect to nRF52 DK or HR sensor
4. Read Battery Level (0x2A19)
5. Subscribe to Heart Rate Measurement (0x2A37)

---

## 12. CI and release

### CI changes (incremental)

| When | Change |
| --- | --- |
| Phase 1 | Add `./gradlew jvmTest` to `ci.yml` (no hardware needed) |
| Phase 2 | Optional self-hosted Linux runner with USB dongle for HIL |
| Release | Update POM description: "Android, iOS, and JVM (Linux desktop)" |

### Publishing

No new Maven artifact. Existing `jvm` publication gains real BLE on Linux.
Document that macOS/Windows JVM consumers still throw until Phase 5.

### Documentation deliverables

- `docs/jvm-desktop-ble-architecture.md` (this doc)
- `docs/platform-setup-jvm.md` (Phase 1)
- Update `ARCHITECTURE.md` platform list
- Update `README.md` supported platforms table

---

## 13. Risks and mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| BlueZ version differences across distros | Broken GATT paths | Pin tested BlueZ version; runtime version check |
| D-Bus permission denied in Docker | BLE fails silently | Clear error in `initDesktop()`; document `--privileged` / group membership |
| D-Bus signal latency vs Android callbacks | Timing bugs in state machine | Reuse existing `CompletableDeferred` timeouts; add integration tests |
| Notify delivery via PropertiesChanged | Missed events if not subscribed correctly | Follow BlueZ docs for `StartNotify`; test with nRF firmware |
| Pairing agent on headless servers | Connect fails on bonded devices | Document agent setup; auto-agent for CI |
| dbus-java thread model vs coroutines | Deadlocks | Single bridge thread + `limitedParallelism(1)` (proven on Android) |
| Consumer expects JVM = mobile desktop | Confusion on macOS/Windows | Detect OS; throw with "Linux only in v1" message |
| Extended advertising filter parity | Scan misses devices | Document BlueZ advertisement field coverage vs Android |

---

## 14. Open decisions

Decisions to resolve before Phase 1 coding:

| # | Question | Options | Recommendation |
| --- | --- | --- | --- |
| D1 | dbus-java artifact coordinates | hypfvieh fork vs central | hypfvieh (active maintenance) |
| D2 | `KmpBle` expect/actual in commonMain? | JVM-only object vs shared expect | JVM-only object in Phase 1 |
| D3 | Integration test source set | `jvmTest` vs `jvmDeviceTest` | `jvmTest` with `@EnabledIfBluetoothAvailable` |
| D4 | GattServer in v1? | Phase 2 vs Phase 4 | Phase 4 (central first) |
| D5 | Detect non-Linux JVM and throw? | Yes vs silent stub | Yes, with clear error message |
| D6 | Self-hosted CI runner for HIL? | Yes vs manual only | Manual until Phase 2 stabilizes |

---

## 15. References

- [BlueZ D-Bus API documentation](https://github.com/bluez/bluez/blob/master/doc/org.bluez.Adapter.rst)
- [dbus-java](https://github.com/hypfvieh/dbus-java)
- kmp-ble `ARCHITECTURE.md` - concurrency model, state machine
- kmp-ble `docs/platform-parity-audit.md` - androidMain/iosMain file mapping
- `src/androidMain/.../AndroidGattBridge.kt` - callback bridge reference
- `src/iosMain/.../ApplePeripheralBridge.kt` - delegate bridge reference
- `src/jvmMain/.../JvmGattCache.kt` - original D-Bus hint
