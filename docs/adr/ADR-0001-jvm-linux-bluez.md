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
  to opt in through `Scanner { }` when BlueZ is available.
- **Portable tests:** `FakeScanner` stays the conformance default for `jvmTest`.
- **Post-processing:** Reuse common `ScannerPipeline` (`toScanEvents`, filters,
  emission policy, timeout) so behavior matches Android/iOS.

## Alternatives considered

| Alternative | Why rejected |
|-------------|--------------|
| Shell out to `bluetoothctl` and scrape output | Fragile, hard to map live RSSI updates, poor testability |
| Windows/macOS JVM stacks first | Gary's priority is Linux/BlueZ on Spark and similar hosts |
| JNI to proprietary stacks (Intel TinyB, vendor SDKs) | Heavier native burden, weaker fit with KMP JVM artifact |
| Make `Scanner { }` always use BlueZ on Linux | Would break CI and headless JVM consumers without adapters |

## Consequences

**Positive**

- Single maintained D-Bus binding; no custom JNI in M1.
- Scan pipeline parity with mobile (cold `scanEvents`, `scanBatch`, filters).
- Clear opt-in boundary for library consumers and CI.

**Negative / constraints**

- **Runtime:** Linux only; requires `bluetoothd`, system D-Bus, and permission
  (typically membership in the `bluetooth` group or equivalent polkit rule).
- **CI:** GitHub runners stay on `FakeScanner`; no adapter required for green builds.
- **Incomplete parity:** `Advertisement.toPeripheral()`, GATT, L2CAP, and server APIs
  remain unsupported on JVM until later milestones.
- **Connectable / extended advertising:** M1 maps core Device1 properties; richer AD
  parsing and platform quirks are deferred.

## Milestones (document only)

| Milestone | Scope |
|-----------|--------|
| **M1 (this PR)** | BlueZ-backed `Scanner` on Linux JVM |
| **M2** | Connect + GATT client over BlueZ |
| **M3** | Platform quirks (connectable detection, extended ads, adapter selection) |

Room-census classifier work stays out of this repository path (local spike only).

## References

- BlueZ D-Bus API: https://github.com/bluez/bluez/blob/master/doc/org.bluez.Adapter.rst
- kmp-ble scanner pipeline: `src/commonMain/.../scanner/internal/ScannerPipeline.kt`
