# ADR-0001: JVM Desktop BLE via Linux BlueZ (D-Bus)

## Status

Accepted (M1 scan, M2 connect + GATT client). Packaging and activation are superseded by [ADR-0003](ADR-0003-jvm-backend-spi.md): BlueZ now lives in the `kmp-ble-bluez` module, is selected through `ServiceLoader`, and implements the plain-typed transports of the core backend SPI.

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
- **M2 (merged):** Connect + GATT client (`Device1.Connect`, `GattService1` /
  `GattCharacteristic1` / `GattDescriptor1` read/write/notify).

- **Library:** [hypfvieh/bluez-dbus](https://github.com/hypfvieh/bluez-dbus) on
  [hypfvieh/dbus-java](https://github.com/hypfvieh/dbus-java) (junixsocket transport).
- **Activation (superseded by ADR-0003):** M1/M2 gated the portable factories behind
  `-Dkmpble.bluez.enabled=true`. Since ADR-0003, adding `kmp-ble-bluez` to the classpath is the
  opt-in; construction stays lazy and failures surface when scan/connect is invoked.
- **Portable tests:** `FakeScanner` and existing Fake peripheral conformance stay the
  `jvmTest` default. `BlueZScanTransportTest` and `BlueZPeripheralTransportTest` (in
  `kmp-ble-bluez`) use internal `BlueZAdapterSession` / `BlueZDeviceSession` fakes to assert discovery,
  connect, GATT discovery, read/write/notify wiring without hardware.
- **Post-processing:** Reuse common `ScannerPipeline`, `PeripheralContext`, `GattOperationQueue`,
  and observation flows so behavior matches Android/iOS where applicable.

## Alternatives considered

| Alternative | Why rejected |
|-------------|--------------|
| Shell out to `bluetoothctl` and scrape output | Fragile, hard to map live RSSI updates, poor testability |
| Windows/macOS JVM stacks first | Ship Linux/BlueZ first: it is the primary desktop/edge JVM BLE surface (BlueZ D-Bus is the standard Linux stack); Windows/macOS JVM backends can follow once the Linux path is proven |
| JNI to proprietary stacks (Intel TinyB, vendor SDKs) | Heavier native burden, weaker fit with KMP JVM artifact |
| Make `Scanner { }` / `toPeripheral()` always use BlueZ on Linux | Would break CI and headless JVM consumers without adapters |
| Split `kmp-ble-bluez` published module now | Deferred for M1/M2; done in ADR-0003 |

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
- **Dependencies:** Resolved by ADR-0003; bluez-dbus and dbus-java are dependencies of
  `kmp-ble-bluez` only.
- **Remaining gaps (after ADR-0003):** L2CAP, isochronous, PAST, direction finding, PHY and
  connection-parameter requests stay unsupported on BlueZ. See the parity table in ADR-0003.
- **Connectable / extended advertising:** Missing or empty `AdvertisingFlags` maps to
  `isConnectable = false` (unknown is not connectable). Richer AD parsing deferred to M3.
- **D-Bus callbacks:** High-rate property updates are not stress-tested; buffer/drop
  behavior under load is a known limitation.

## Milestones

| Milestone | Scope | Status |
|-----------|-------|--------|
| **M1** | BlueZ-backed `Scanner` on Linux JVM | Merged |
| **M2** | Connect + GATT client over BlueZ | Merged (needs Spark L1 hardware evidence) |
| **ADR-0003** | Backend module, bonding / agent, MTU, reliable write, adapter, GATT server, advertising | Implemented (needs hardware evidence) |
| **M3** | Extended-advertising scan reports, richer AD parsing, L2CAP over `AF_BLUETOOTH` | Planned |

Room-census classifier work stays out of this repository path (local spike only).

## References

- BlueZ D-Bus API: https://github.com/bluez/bluez/blob/master/doc/org.bluez.Adapter.rst
- BlueZ GATT API: https://github.com/bluez/bluez/blob/master/doc/org.bluez.GattCharacteristic.rst
- kmp-ble scanner pipeline: `src/commonMain/.../scanner/internal/ScannerPipeline.kt`
- kmp-ble peripheral context: `src/commonMain/.../peripheral/internal/PeripheralContext.kt`
