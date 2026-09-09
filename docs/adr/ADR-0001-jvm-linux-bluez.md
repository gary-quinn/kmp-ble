# ADR-0001: JVM Desktop BLE via Linux BlueZ (D-Bus)

## Status

Accepted (M2: Connect + GATT client)

## Context

kmp-ble ships production BLE on Android and iOS. JVM targets historically threw
`UnsupportedOperationException` from `Scanner()`, `Peripheral`, and related factories.
Desktop and edge Linux hosts (for example DGX Spark with working `hci0`) need a real
stack without forking the common API.

CI runs on GitHub-hosted Ubuntu runners without requiring a Bluetooth adapter. Any JVM
desktop backend must stay opt-in and testable without hardware.

## Decision

Implement **Linux JVM BLE through BlueZ over the system D-Bus bus**, in milestones:

- **M1 (merged):** LE scan only (`Scanner.scanEvents`).
- **M2 (this PR):** Connect + GATT client (`Device1.Connect`, `GattService1` /
  `GattCharacteristic1` / `GattDescriptor1` read/write/notify).

- **Library:** [hypfvieh/bluez-dbus](https://github.com/hypfvieh/bluez-dbus) on
  [hypfvieh/dbus-java](https://github.com/hypfvieh/dbus-java) (junixsocket transport).
- **Activation:** Default `Scanner { }` and `Advertisement.toPeripheral()` on JVM remain
  disabled on CI. Linux apps use explicit `BlueZScanner { }` / `Advertisement.toBlueZPeripheral()`,
  or set `-Dkmpble.bluez.enabled=true` to opt in through the portable factories (lazy: no D-Bus
  probe at factory time; failures surface when scan/connect is invoked).
- **Portable tests:** `FakeScanner` and existing Fake peripheral conformance stay the
  `jvmTest` default. `BlueZScannerLifecycleTest` and `BlueZPeripheralLifecycleTest` use
  internal `BlueZAdapterSession` / `BlueZDeviceSession` fakes to assert discovery,
  connect, GATT discovery, read/write/notify wiring without hardware.
- **Post-processing:** Reuse common `ScannerPipeline`, `PeripheralContext`, `GattOperationQueue`,
  and observation flows so behavior matches Android/iOS where applicable.

## Alternatives considered

| Alternative | Why rejected |
|-------------|--------------|
| Shell out to `bluetoothctl` and scrape output | Fragile, hard to map live RSSI updates, poor testability |
| Windows/macOS JVM stacks first | Ship Linux/BlueZ first: it is the primary desktop/edge JVM BLE surface (BlueZ D-Bus is the standard Linux stack); Windows/macOS JVM JVM backends can follow once the Linux path is proven |
| JNI to proprietary stacks (Intel TinyB, vendor SDKs) | Heavier native burden, weaker fit with KMP JVM artifact |
| Make `Scanner { }` / `toPeripheral()` always use BlueZ on Linux | Would break CI and headless JVM consumers without adapters |
| Split `kmp-ble-bluez` published module now | Deferred; bluez-dbus stays on jvmMain for M1/M2 with documented non-Linux JVM jar pull |

## Consequences

**Positive**

- Single maintained D-Bus binding; no custom JNI in M1/M2.
- Scan + connect/GATT client parity with mobile common API for typical central apps.
- Clear opt-in boundary for library consumers and CI.

**Negative / constraints**

- **Runtime:** Linux only; requires `bluetoothd`, system D-Bus, and permission
  (typically membership in the `bluetooth` group or equivalent polkit rule).
- **CI:** GitHub runners stay on `FakeScanner` / unsupported default factories; no adapter
  required for green builds. `jvmTest` does not prove BlueZ D-Bus integration on hardware.
- **Dependencies:** Non-Linux JVM consumers still resolve bluez-dbus jars on jvmMain
  until a follow-up `kmp-ble-bluez` module split (optional).
- **Incomplete parity (post-M2):** GATT server, L2CAP, isochronous, PAST, direction finding,
  reliable write, MTU negotiation request, and bond management remain unsupported or stubbed
  on JVM BlueZ until later milestones.
- **Connectable / extended advertising:** Missing or empty `AdvertisingFlags` maps to
  `isConnectable = false` (unknown is not connectable). Richer AD parsing deferred to M3.
- **D-Bus callbacks:** High-rate property updates are not stress-tested; buffer/drop
  behavior under load is a known limitation.

## Milestones

| Milestone | Scope | Status |
|-----------|-------|--------|
| **M1** | BlueZ-backed `Scanner` on Linux JVM | Merged |
| **M2** | Connect + GATT client over BlueZ | Implemented (needs Spark L1 hardware evidence) |
| **M3** | Platform quirks (extended ads, adapter selection, richer AD parsing) | Planned |

Room-census classifier work stays out of this repository path (local spike only).

## References

- BlueZ D-Bus API: https://github.com/bluez/bluez/blob/master/doc/org.bluez.Adapter.rst
- BlueZ GATT API: https://github.com/bluez/bluez/blob/master/doc/org.bluez.GattCharacteristic.rst
- kmp-ble scanner pipeline: `src/commonMain/.../scanner/internal/ScannerPipeline.kt`
- kmp-ble peripheral context: `src/commonMain/.../peripheral/internal/PeripheralContext.kt`
