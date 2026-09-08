# ADR-0001: JVM Desktop BLE via Linux BlueZ (D-Bus)

## Status

Accepted (M1: Scanner only)

## Context

kmp-ble ships production BLE on Android and iOS. JVM targets today throw
`UnsupportedOperationException` from `Scanner()`, `Peripheral`, and related factories.
Desktop and edge Linux hosts (for example DGX Spark with working `hci0`) need a real
stack without forking the common API.

CI runs on GitHub-hosted Ubuntu runners without requiring a Bluetooth adapter. Any JVM
desktop backend must stay opt-in and testable without hardware.

## Decision

Implement **Linux JVM BLE through BlueZ over the system D-Bus bus**, starting with
**M1: LE scan only** (`Scanner.scanEvents`).

- **Library:** [hypfvieh/bluez-dbus](https://github.com/hypfvieh/bluez-dbus) on
  [hypfvieh/dbus-java](https://github.com/hypfvieh/dbus-java) (junixsocket transport).
- **Activation:** Default `Scanner { }` on JVM remains disabled on CI. Linux apps use
  the explicit `BlueZScanner { }` constructor, or set `-Dkmpble.bluez.enabled=true`
  to opt in through `Scanner { }` (lazy: no D-Bus probe at factory time; failures
  surface when `scanEvents` is collected).
- **Portable tests:** `FakeScanner` stays the conformance default for `jvmTest`.
  `BlueZScannerLifecycleTest` uses an internal `BlueZAdapterSession` fake to assert
  discovery start/stop and filter wiring without hardware.
- **Post-processing:** Reuse common `ScannerPipeline` (`toScanEvents`, filters,
  emission policy, timeout) so behavior matches Android/iOS where scanning applies.

## Alternatives considered

| Alternative | Why rejected |
|-------------|--------------|
| Shell out to `bluetoothctl` and scrape output | Fragile, hard to map live RSSI updates, poor testability |
| Windows/macOS JVM stacks first | Ship Linux/BlueZ first: it is the primary desktop/edge JVM BLE surface (BlueZ D-Bus is the standard Linux stack); Windows/macOS JVM backends can follow once the Linux path is proven |
| JNI to proprietary stacks (Intel TinyB, vendor SDKs) | Heavier native burden, weaker fit with KMP JVM artifact |
| Make `Scanner { }` always use BlueZ on Linux | Would break CI and headless JVM consumers without adapters |
| Split `kmp-ble-bluez` published module now | Deferred; bluez-dbus stays on jvmMain for M1 with documented non-Linux JVM jar pull |

## Consequences

**Positive**

- Single maintained D-Bus binding; no custom JNI in M1.
- Scan pipeline parity with mobile (cold `scanEvents`, `scanBatch`, filters).
- Clear opt-in boundary for library consumers and CI.

**Negative / constraints**

- **Runtime:** Linux only; requires `bluetoothd`, system D-Bus, and permission
  (typically membership in the `bluetooth` group or equivalent polkit rule).
- **CI:** GitHub runners stay on `FakeScanner`; no adapter required for green builds.
  `jvmTest` does not prove BlueZ D-Bus integration.
- **Dependencies:** Non-Linux JVM consumers still resolve bluez-dbus jars on jvmMain
  until a follow-up `kmp-ble-bluez` module split (optional).
- **Incomplete parity:** `Advertisement.toPeripheral()`, GATT, L2CAP, and server APIs
  remain unsupported on JVM until later milestones.
- **Connectable / extended advertising:** Missing or empty `AdvertisingFlags` maps to
  `isConnectable = false` (unknown is not connectable). Richer AD parsing deferred to M3.
- **D-Bus callbacks:** High-rate property updates are not stress-tested; buffer/drop
  behavior under load is a known limitation for M1.

## Milestones (document only)

| Milestone | Scope |
|-----------|--------|
| **M1 (this PR)** | BlueZ-backed `Scanner` on Linux JVM |
| **M2** | Connect + GATT client over BlueZ |
| **M3** | Platform quirks (extended ads, adapter selection, richer AD parsing) |

Room-census classifier work stays out of this repository path (local spike only).

## References

- BlueZ D-Bus API: https://github.com/bluez/bluez/blob/master/doc/org.bluez.Adapter.rst
- kmp-ble scanner pipeline: `src/commonMain/.../scanner/internal/ScannerPipeline.kt`
