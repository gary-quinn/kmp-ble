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
2. [Delta from ARCHITECTURE.md](#2-delta-from-architecturemd)
3. [Goals and non-goals](#3-goals-and-non-goals)
4. [Platform strategy](#4-platform-strategy)
5. [Native stack choice](#5-native-stack-choice)
6. [Architecture](#6-architecture)
7. [Desktop initialization API](#7-desktop-initialization-api)
8. [Permissions and lifecycle](#8-permissions-and-lifecycle)
9. [Feature parity matrix](#9-feature-parity-matrix)
10. [Implementation inventory](#10-implementation-inventory)
11. [Phased plan](#11-phased-plan)
12. [Testing strategy](#12-testing-strategy)
13. [CI and release](#13-ci-and-release)
14. [Risks and mitigations](#14-risks-and-mitigations)
15. [Open decisions](#15-open-decisions)
16. [References](#16-references)

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

Central stub (message updated in Phase 1):

```kotlin
// src/jvmMain/kotlin/com/atruedev/kmpble/UnsupportedBle.kt
internal fun unsupportedBle(operation: String): Nothing =
    throw UnsupportedOperationException(
        "$operation is not supported on JVM - BLE requires Android or iOS",
    )
// Phase 1: OS-aware message, e.g. "Call KmpBle.initDesktop() on Linux desktop"
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

The JVM implementation introduces `JvmGattBridge` with platform-neutral
`JvmCallbackEvent`, dispatched through the same `handleGattEvent` pattern as
`AndroidPeripheralGattHandler.kt` (not by reusing `GattCallbackEvent`, which
lives in `androidMain` with Android types).

---

## 2. Delta from ARCHITECTURE.md

This section records what changes relative to the existing architecture doc.
`ARCHITECTURE.md` itself is updated in the Phase 1 PR (not blocked on this RFC
merge).

| Topic | ARCHITECTURE.md today | JVM desktop addition |
| --- | --- | --- |
| Supported platforms | Android, iOS | Linux desktop on `jvm()` (v1) |
| Layer 1 callbacks | `HandlerThread` (Android), `DispatchQueue` (iOS) | dbus-java thread pool |
| Layer 2 serialization | `limitedParallelism(1)` per peripheral | Same |
| State machine | 14 states, wildcard `AdapterOff` / `RemoteDisconnected` | Unchanged; JVM must feed it faithfully |
| Timeouts | `OperationTimeouts` + `GattOperationQueue` (10s default) | Same contracts via D-Bus wrappers |
| State restoration | Android + iOS | No-op on JVM |
| Observations | UUID-based, `ObservationRegistry.applyBackpressure()` | Same; bridge must not block dbus-java thread |
| Logging | `BleLogEvent` + `logEvent()` | Required on JVM (see [Structured logging](#structured-logging)) |
| Quirks | Android OEM registry | Empty registry |

Concurrency alignment (two layers, not one):

```
Layer 1 (OS-managed):  dbus-java signal thread -> complete deferred / offer to channel
Layer 2 (serialized):  per-peripheral limitedParallelism(1) -> handleGattEvent / state machine
Layer 3 (consumer):    caller's coroutine context
```

---

## 3. Goals and non-goals

### Goals (v1)

1. **Linux desktop central role** - scan, connect, GATT read/write/observe on
   the existing `jvm()` target.
2. **No public API changes** - all work stays in `jvmMain/`; `commonMain`
   factories remain `expect fun`.
3. **Handler dispatch parity** - reuse the `AndroidPeripheralGattHandler` /
   `handleGattEvent` dispatch pattern via platform-neutral `JvmCallbackEvent`,
   targeting the same state-machine events as `AndroidPeripheralConnection`.
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

## 4. Platform strategy

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

## 5. Native stack choice

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

### Coroutine integration and D-Bus ingress

D-Bus signal callbacks arrive on a dbus-java thread pool. Rules:

1. **Layer 1 only completes deferreds or offers to a bounded channel** - no
   state-machine work, no GATT queue logic on the dbus-java thread.
2. **Cancellation-safe** - if the waiting coroutine is cancelled,
   `CompletableDeferred.cancel()` must not leave a stale completion handler
   that fires later.
3. **Backpressure** - `PropertiesChanged` on notify characteristics can arrive
   faster than the serial dispatcher drains. The bridge offers to a bounded
   channel (drop-oldest or suspend-offer policy TBD in Phase 2 spike);
   consumer-side backpressure is already handled by
   `ObservationRegistry.applyBackpressure()` in `commonMain`.
4. **Layer 2** - `peripheralContext.scope` on `limitedParallelism(1)` runs
   `handleGattEvent` (same as Android).

---

## 6. Architecture

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
|    JvmGattBridge  --JvmCallbackEvent-->  handleGattEvent    |
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
  KmpBle.jvm.kt                    # Desktop init / shutdown API
  UnsupportedBle.kt                # OS-aware messages (Phase 1)
  adapter/
    AdapterFactory.jvm.kt          # actual -> JvmBluetoothAdapter
    JvmBluetoothAdapter.kt
  bluez/
    JvmBluezConnection.kt          # D-Bus singleton, bus lifecycle
    JvmBluezPaths.kt               # Object path helpers
    JvmBluezProperties.kt          # Property change parsing
    JvmBluezErrors.kt              # D-Bus error name constants
  scanner/
    ScannerFactory.jvm.kt          # actual -> JvmScanner
    JvmScanner.kt
    JvmAdvertisementParser.kt
  peripheral/
    PeripheralFactory.jvm.kt       # actual -> JvmPeripheral
    JvmPeripheral.kt
    JvmPeripheralConnection.kt
    JvmPeripheralGattHandler.kt    # handleGattEvent dispatch (mirror Android)
    JvmPeripheralInternal.kt
    JvmGattBridge.kt
    JvmCallbackEvent.kt            # Platform-neutral events (status codes)
    JvmConnectionState.kt          # BlueZ -> state machine mapping
    JvmGattStatusMapper.kt         # D-Bus errors + status -> BleException
  gatt/cache/
    JvmGattCache.kt                # Real file-backed cache
  permissions/
    BlePermissions.jvm.kt          # Check adapter powered + D-Bus access
  server/
    GattServerFactory.jvm.kt         # Phase 4: throws or NotSupported
  ... (l2cap, isochronous, periodic: keep existing NotSupported stubs)
```

### `JvmCallbackEvent` and connection-state fidelity

`JvmCallbackEvent` is platform-neutral. It does **not** use `error: String?`
at the bridge boundary. D-Bus error names are mapped to `status: Int` (via
`JvmGattStatusMapper`) at ingress inside `JvmGattBridge`, before handlers see
events. Handlers convert `status` to `GattStatus` / `BleException` using the
same patterns as `AndroidPeripheralConnection.handleConnectionStateChanged`.

Connection state must be fine-grained enough for the 14-state machine. A bare
`connected: Boolean` cannot distinguish remote disconnect, adapter off, auth
failure, or in-flight disconnect. Model connection transitions with
`(status: Int, newState: JvmConnectionState)`:

```kotlin
/** Maps BlueZ Device1 / Adapter1 properties to handler inputs. */
internal enum class JvmConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,           // Device1.Connected=true, ServicesResolved=false
    SERVICES_RESOLVED,   // Device1.ServicesResolved=true
}

internal sealed interface JvmCallbackEvent {
    data class ConnectionStateChanged(
        val status: Int,
        val newState: JvmConnectionState,
    ) : JvmCallbackEvent

    data class ServicesDiscovered(
        val status: Int,
        val services: List<JvmGattService>,
    ) : JvmCallbackEvent

    data class CharacteristicRead(
        val path: String,
        val value: ByteArray,
        val status: Int,
    ) : JvmCallbackEvent

    data class CharacteristicWrite(val path: String, val status: Int) : JvmCallbackEvent
    data class CharacteristicChanged(val path: String, val value: ByteArray) : JvmCallbackEvent
    data class DescriptorRead(val path: String, val value: ByteArray, val status: Int) : JvmCallbackEvent
    data class DescriptorWrite(val path: String, val status: Int) : JvmCallbackEvent
    data class MtuChanged(val mtu: Int, val status: Int) : JvmCallbackEvent
    // See parity table below for events not emitted on JVM.
}
```

**BlueZ -> `JvmConnectionState` mapping (Phase 0 spike must validate):**

| BlueZ signal / property | `JvmConnectionState` | State-machine event |
| --- | --- | --- |
| `Device1.Connected` false | `DISCONNECTED` | `RemoteDisconnected` or `ByError` via status |
| `Device1.Connected` true, `ServicesResolved` false | `CONNECTED` | `LinkEstablished` -> `Discovering` |
| `Device1.ServicesResolved` true | `SERVICES_RESOLVED` | discovery complete |
| `Adapter1.Powered` false | (wildcard) | `AdapterOff` |
| D-Bus `org.bluez.Error.AuthenticationFailed` | `DISCONNECTED` + error status | `ConnectionFailed` / bonding |
| `Device1.Connect()` in flight | `CONNECTING` | `Transport` |

Handler target: `JvmPeripheralGattHandler.handleGattEvent(event)`, structured
like `AndroidPeripheralGattHandler.kt`, calling
`handleConnectionStateChanged` equivalents that feed `ConnectionEvent` into the
state machine.

### Callback event parity

`GattCallbackEvent` (Android) defines events that JVM may not support. Explicit
parity avoids surprise no-ops in `JvmPeripheralGattHandler`:

| Event | Android | JVM v1 | Notes |
| --- | --- | --- | --- |
| `ConnectionStateChanged` | Yes | **Yes** | `(status, JvmConnectionState)` |
| `ServicesDiscovered` | Yes | **Yes** | via `GetManagedObjects` |
| `MtuChanged` | Yes | **Best effort** | BlueZ `MTU` property |
| `CharacteristicRead` | Yes | **Yes** | |
| `CharacteristicWrite` | Yes | **Yes** | |
| `ReliableWriteCompleted` | Yes | **Never** | BlueZ has no ATT reliable-write batch; `writeReliable` not supported on JVM v1 |
| `CharacteristicChanged` | Yes | **Yes** | `StartNotify` + `PropertiesChanged` |
| `DescriptorRead` / `DescriptorWrite` | Yes | **Yes** | CCCD for observe |
| `ReadRemoteRssi` | Yes | **Stub** | RSSI from `Device1.RSSI` at scan time (Phase 3); no live read API |
| `PhyUpdated` / `PhyRead` | Yes | **Never** | Not on BlueZ D-Bus client |
| `SubrateChanged` | Yes | **Never** | Not on BlueZ D-Bus client |

Events marked **Never** are not emitted; the corresponding `handleGattEvent`
branches are absent. Public API calls that would trigger them (e.g. `readPhy()`)
return `NotSupported` or no-op per existing `commonMain` contracts.

### Timeouts, cancellation, and disconnect propagation

BLE operations are not instantaneous. Every D-Bus GATT call must complete a
`CompletableDeferred` under an explicit timeout, matching existing kmp-ble
contracts:

| Operation | Timeout source | Default |
| --- | --- | --- |
| `Device1.Connect()` | `ConnectionOptions.timeouts.connect` | 30s |
| `GetManagedObjects` / service discovery | `ConnectionOptions.timeouts.serviceDiscovery` | 15s |
| `ReadValue` | `ConnectionOptions.timeouts.read` | 5s |
| `WriteValue` | `ConnectionOptions.timeouts.write` | 5s |
| `StartNotify` / CCCD write | `ConnectionOptions.timeouts.write` | 5s |
| MTU read | `ConnectionOptions.timeouts.mtuNegotiation` | 10s |
| Queued GATT ops | `GattOperationQueue` operation timeout | 10s |

Implementation pattern (same as Android/iOS):

```kotlin
withTimeout(currentTimeouts.read) { deferred.await() }
```

**Disconnect mid-operation:**

1. `Device1.Disconnect()` or adapter power-off cancels all in-flight deferreds
   on that peripheral with `CancellationException`.
2. `JvmGattBridge` maps cancellation to typed `BleException` (e.g.
   `ConnectionLost`, `PeripheralTimeout`) via `JvmGattStatusMapper` - never
   propagate raw D-Bus error strings to handlers.
3. `GattOperationQueue` already handles caller cancellation and timeout;
   `JvmPeripheralConnection` must not assume D-Bus methods return synchronously.

Phase 0 spike must measure typical `Connect()` + `GetManagedObjects` latency on
target hardware to validate default timeouts.

### Scan flow and timeout contract

```
Scanner.scanEvents (Flow)
    <- toScanEvents() [commonMain: filter, emission policy, timeout]
    <- JvmScanner raw Flow<Advertisement>
        <- D-Bus InterfacesAdded / PropertiesChanged on adapter
        <- StartDiscovery / StopDiscovery on Adapter1
        <- JvmAdvertisementParser (ManufacturerData, ServiceUUIDs from Device1 props)
```

**Timeout and cancellation (mandatory):**

- `ScannerConfig.timeout` is enforced by `toScanEvents()` in `commonMain`
  (`ScannerPipeline.kt` applies `takeWhile { elapsed < timeout }`). `JvmScanner`
  must call `Adapter1.StopDiscovery()` when:
  1. The configured timeout elapses (via `awaitClose` / collector cancellation
     propagated from `toScanEvents`), or
  2. The scan `Flow` collector is cancelled cooperatively.
- Unbounded scan (no `timeout` set) is allowed by `ScannerConfig` but
  documented as discouraged; `JvmScanner` still stops discovery on `close()`.
- Filter groups are applied in `commonMain`; `JvmScanner` emits all
  advertisements.

### Connect + GATT flow

```
peripheral.connect(options)
    -> withTimeout(options.timeouts.connect) {
           Device1.Connect() via D-Bus
           await ConnectionStateChanged(CONNECTED) on bridge
       }
    -> JvmPeripheralGattHandler.handleConnectionStateChanged
        -> commonMain state machine (Connecting -> Discovering -> ...)
    -> withTimeout(options.timeouts.serviceDiscovery) {
           GetManagedObjects + ServicesDiscovered event
       }
    -> GattOperationQueue serializes:
           ReadValue / WriteValue / StartNotify on GattCharacteristic1
    -> PropertiesChanged (Value) -> CharacteristicChanged (bounded channel)
```

MTU: BlueZ exposes `MTU` property on `GattCharacteristic1`. Negotiation may
differ from Android `requestMtu()`; map best-effort and document limitations.

### Resource teardown

Holding D-Bus object paths after `Device1.Disconnect()` is an anti-pattern.
Mirror `AndroidPeripheral.close()` / `AndroidGattBridge.close()` ownership:

**On `Peripheral.close()` (terminal):**

1. Set `_closed` flag (same as Android).
2. Cancel `peripheralContext.scope` and reconnection handler.
3. Cancel all in-flight `CompletableDeferred` on the bridge with
   `CancellationException`.
4. Unsubscribe `PropertiesChanged` signal matchers for this device's
   characteristics and descriptors.
5. Null out cached D-Bus object paths (services, characteristics).
6. Call `Device1.Disconnect()` if still connected.
7. `bridge.close()` - clear `onEvent`, release signal registrations.
8. Clear observation manager and persistence for this identifier.
9. `PeripheralRegistry.remove(identifier)`.

**On disconnect (non-terminal, reconnectable):**

- `bridge.releaseDevice()` - disconnect and clear GATT path cache but keep
  bridge reusable (mirror `AndroidGattBridge.releaseGatt()`).

**On `Scanner.close()`:**

- `Adapter1.StopDiscovery()` if scanning.
- Cancel scanner scope; do not tear down the shared D-Bus connection.

### Structured logging

All JVM connection code uses `BleLogEvent` + `logEvent()` (same as Android).
No `println` in library code.

| Level | Examples |
| --- | --- |
| Debug | D-Bus method invoked (path, interface, no payload bytes) |
| Info | Scan started/stopped, connect/disconnect, services discovered |
| Warn | Retryable D-Bus errors, discovery restart |
| Error | Permission denied, unrecoverable GATT failure, timeout |

Deliverable: logging calls added in Phase 1 (adapter/scanner) and Phase 2
(peripheral/GATT) PRs.

---

## 7. Desktop initialization API

Android requires `KmpBle.init(context)`. JVM needs an equivalent entry point
without `Context`, plus explicit teardown for tests.

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

    /**
     * Tear down the D-Bus connection, cancel bridge scopes, and clear adapter
     * selection. Safe to call when not initialized. Required for test isolation.
     */
    public fun closeDesktop()

    /** @VisibleForTesting - reset singleton for unit tests with mocked D-Bus. */
    internal fun resetForTests()

    internal fun requireBluez(): JvmBluezConnection
}
```

`closeDesktop()` disconnects the system bus, cancels any shared coroutine scope,
and clears the selected adapter. Tests call `resetForTests()` in `@AfterEach`
to avoid singleton leakage across mocked-D-Bus cases.

`KmpBle` on JVM and Android are separate objects in v1 (no `commonMain`
`expect object`) to avoid API churn on mobile.

### Adapter selection

1. List adapters via `org.bluez` manager or `GetManagedObjects`
2. Match `adapterAddress` to `Adapter1.Address` or object path `/org/bluez/hciN`
3. Fail fast with clear error if no adapter or Bluetooth powered off

---

## 8. Permissions and lifecycle

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

### Lifecycle summary

| Event | Action |
| --- | --- |
| `initDesktop()` | Open system bus, select adapter, register adapter signal handlers |
| First `Scanner()` / `Peripheral()` | Use shared `JvmBluezConnection` |
| `Scanner.close()` | Stop discovery; keep bus open |
| `Peripheral.close()` | Full teardown per [Resource teardown](#resource-teardown) |
| `closeDesktop()` | Disconnect bus, cancel shared scope, clear singleton state |
| Process exit | OS reclaims D-Bus connection |
| State restoration | No-op on desktop v1 |

### Bonding / pairing

BlueZ handles pairing via `Device1.Pair()`. Pin entry may require an agent
registered on D-Bus (`org.bluez.Agent1`). Phase 3 scope:

- Default agent: auto-accept / NoInputNoOutput for headless CI
- Document limitation: interactive pairing needs consumer-provided agent

---

## 9. Feature parity matrix

| Feature | Android | iOS | JVM v1 target | JVM v1 notes |
| --- | --- | --- | --- | --- |
| `BluetoothAdapter()` | Yes | Yes | **Phase 1** | Powered, address, name |
| `Scanner` | Yes | Yes | **Phase 1** | Legacy-first; extended adv best-effort via Device1 props (BlueZ 5.62+); see risk row |
| `Peripheral.connect()` | Yes | Yes | **Phase 2** | |
| GATT read/write | Yes | Yes | **Phase 2** | |
| GATT notify/observe | Yes | Yes | **Phase 2** | `StartNotify` + PropertiesChanged |
| MTU negotiation | Yes | Partial | **Best effort** | BlueZ MTU property |
| Bonding | Yes | Limited | **Phase 3** | Agent required |
| RSSI | Yes | Yes | **Phase 3** | `Device1.RSSI` at discovery; no `ReadRemoteRssi` |
| PHY / connection priority | Yes | Limited | **No** | Not exposed on BlueZ D-Bus client |
| `writeReliable` | Yes | Yes | **No** | No `ReliableWriteCompleted` on BlueZ |
| `GattServer` / `Advertiser` | Yes | Yes | **Phase 4** | BlueZ peripheral mode |
| L2CAP | Yes | Yes | No | Keep `NotSupported` |
| Isochronous | Yes | Yes | No | Keep `NotSupported` |
| Periodic adv sync | Yes | Yes | No | Keep `NotSupported` |
| State restoration | Yes | Yes | No | No-op |
| Quirks | Yes | Partial | No | Empty registry |
| `createGattCache()` | Yes | Yes | **Phase 2** | File-backed in user cache dir |

---

## 10. Implementation inventory

### Replace stub actuals (by phase)

| File | Phase | Work |
| --- | --- | --- |
| `adapter/AdapterFactory.jvm.kt` | 1 | Wire `JvmBluetoothAdapter` |
| `scanner/ScannerFactory.jvm.kt` | 1 | Wire `JvmScanner` |
| `permissions/BlePermissions.jvm.kt` | 1 | D-Bus + powered check |
| `KmpBle.jvm.kt` (new) | 1 | `initDesktop()`, `closeDesktop()`, `resetForTests()` |
| `UnsupportedBle.kt` | 1 | OS-aware error messages |
| `bluez/*` (new) | 1-2 | D-Bus client layer |
| `peripheral/PeripheralFactory.jvm.kt` | 2 | Wire `JvmPeripheral` |
| `peripheral/JvmPeripheralGattHandler.kt` (new) | 2 | `handleGattEvent` dispatch |
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

`JvmGattStatusMapper` maps at the bridge ingress (before events reach handlers):

- D-Bus error names: `org.bluez.Error.NotReady`,
  `org.bluez.Error.AuthenticationFailed`, `org.freedesktop.DBus.Error.AccessDenied`
- Status integers on `JvmCallbackEvent`
- Output: existing `BleException` / `GattStatus` types in `commonMain`

Handlers never see raw `String?` errors from D-Bus.

---

## 11. Phased plan

### Phase 0 - Spike (1 week, no merge requirement)

- [ ] Shell script: scan via `busctl` / `dbus-send` on Linux with USB dongle
- [ ] Kotlin main: connect dbus-java, list adapters, start discovery
- [ ] Document object paths and property names for target BlueZ version
- [ ] Validate `Connect()` + `GetManagedObjects` latency against default timeouts
- [ ] Prototype `ConnectionStateChanged` mapping from `Device1` property signals
- [ ] Prototype `closeDesktop()` / signal unsubscribe (no leaked matchers)
- [ ] Validate CI runner has no Bluetooth (tests must skip gracefully)

**Exit criteria:** Demonstrate one device discovered and connected via D-Bus
outside kmp-ble; connection-state transitions documented with timestamps.

### Phase 1 - Adapter + Scanner (1-2 weeks)

- [ ] `KmpBle.initDesktop()` + `closeDesktop()` + `resetForTests()`
- [ ] `JvmBluezConnection` singleton with teardown
- [ ] `JvmBluetoothAdapter` - state, powered, address
- [ ] `JvmScanner` - `Flow<ScanEvent>` via discovery; honour timeout + cancellation
- [ ] `checkBlePermissions()` - real checks
- [ ] `BleLogEvent` logging for scan/adapter operations
- [ ] Unit tests with mocked D-Bus
- [ ] `docs/platform-setup-jvm.md` integration guide
- [ ] Update `ARCHITECTURE.md` platform list

**Exit criteria:** `./gradlew jvmTest` passes; manual scan lists devices on Linux.

### Phase 2 - Central GATT (2-3 weeks)

- [ ] `JvmGattBridge` + `JvmCallbackEvent` + `JvmConnectionState`
- [ ] `JvmPeripheralGattHandler` + `JvmPeripheral` + `JvmPeripheralConnection`
- [ ] Connect, discover services, read, write, observe with `withTimeout` on every op
- [ ] Disconnect-mid-operation -> typed `BleException`
- [ ] Resource teardown on `Peripheral.close()`
- [ ] `JvmGattCache` + `ObservationPersistence` (file-backed)
- [ ] `JvmGattStatusMapper` at bridge ingress
- [ ] Bounded channel for notify ingress
- [ ] Hardware-in-the-loop test (optional CI job on self-hosted runner)

**Exit criteria:** GATT workflow (HR service 0x180D) works on Linux; existing
`jvmTest` Fake* tests still pass.

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

## 12. Testing strategy

### Keep existing tests

All `jvmTest` Fake* and Lincheck tests must continue passing unchanged. They
validate `commonMain` without hardware.

### New tests

| Layer | Type | Location |
| --- | --- | --- |
| D-Bus parsing | Unit | `jvmTest/.../bluez/` |
| Advertisement parser | Unit | `jvmTest/.../scanner/` |
| GATT status mapper | Unit | `jvmTest/.../peripheral/` |
| Timeout / cancellation | Unit | `jvmTest/.../peripheral/` (mock bridge) |
| Teardown / `resetForTests` | Unit | `jvmTest/.../KmpBleDesktopTest.kt` |
| Scan + connect | Integration (hardware) | `jvmTest/.../integration/` |
| Conformance | Extend existing | Reuse Fake* patterns where possible |

### Integration test pattern

```kotlin
@EnabledIfBluetoothAvailable // custom JUnit condition
class JvmBleIntegrationTest {
    @AfterEach fun tearDown() { KmpBle.resetForTests() }

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
6. `peripheral.close()` then `KmpBle.closeDesktop()` - verify no leaked discovery

---

## 13. CI and release

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
- Update `ARCHITECTURE.md` platform list (Phase 1 PR)
- Update `README.md` supported platforms table (Phase 1 PR)

---

## 14. Risks and mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| BlueZ version differences across distros | Broken GATT paths | Pin tested BlueZ version; runtime version check |
| D-Bus permission denied in Docker | BLE fails silently | Clear error in `initDesktop()`; document `--privileged` / group membership |
| D-Bus signal latency vs Android callbacks | Timing bugs in state machine | `withTimeout` on every op; integration tests for slow devices |
| Notify delivery via PropertiesChanged | Missed events if not subscribed correctly | Follow BlueZ docs for `StartNotify`; bounded channel; test with nRF firmware |
| Pairing agent on headless servers | Connect fails on bonded devices | Document agent setup; auto-agent for CI |
| dbus-java thread model vs coroutines | Deadlocks | Layer 1 completes deferreds only; Layer 2 on `limitedParallelism(1)` per peripheral |
| Consumer expects JVM = mobile desktop | Confusion on macOS/Windows | Detect OS; throw with "Linux only in v1" message |
| Extended advertising filter parity | Scan misses devices | Legacy-first in v1; document BlueZ 5.62+ extended field limits |
| Stale D-Bus paths after disconnect | Use-after-disconnect crashes | Mandatory teardown checklist on `Peripheral.close()` |
| Singleton bus leaks in tests | Flaky `jvmTest` | `closeDesktop()` + `resetForTests()` in `@AfterEach` |

---

## 15. Open decisions

Decisions to resolve before Phase 1 coding:

| # | Question | Options | Recommendation |
| --- | --- | --- | --- |
| D1 | dbus-java artifact coordinates | hypfvieh fork vs central | hypfvieh (active maintenance) |
| D2 | `KmpBle` expect/actual in commonMain? | JVM-only object vs shared expect | JVM-only object in Phase 1 |
| D3 | Integration test source set | `jvmTest` vs `jvmDeviceTest` | `jvmTest` with `@EnabledIfBluetoothAvailable` |
| D4 | GattServer in v1? | Phase 2 vs Phase 4 | Phase 4 (central first) |
| D5 | Detect non-Linux JVM and throw? | Yes vs silent stub | Yes, with clear error message |
| D6 | Self-hosted CI runner for HIL? | Yes vs manual only | Manual until Phase 2 stabilizes |
| D7 | Notify ingress backpressure policy | Drop-oldest vs suspend-offer | Resolve in Phase 2 spike; must not block dbus-java thread |

---

## 16. References

- [BlueZ D-Bus API documentation](https://github.com/bluez/bluez/blob/master/doc/org.bluez.Adapter.rst)
- [dbus-java](https://github.com/hypfvieh/dbus-java)
- kmp-ble `ARCHITECTURE.md` - concurrency model, state machine
- kmp-ble `docs/platform-parity-audit.md` - androidMain/iosMain file mapping
- `src/androidMain/.../AndroidGattBridge.kt` - callback bridge reference
- `src/androidMain/.../AndroidPeripheralGattHandler.kt` - `handleGattEvent` dispatch target
- `src/androidMain/.../AndroidPeripheralConnection.kt` - `handleConnectionStateChanged`
- `src/commonMain/.../OperationTimeouts.kt` - per-operation timeout defaults
- `src/commonMain/.../GattOperationQueue.kt` - 10s queue operation timeout
- `src/iosMain/.../ApplePeripheralBridge.kt` - delegate bridge reference
- `src/jvmMain/.../JvmGattCache.kt` - original D-Bus hint
