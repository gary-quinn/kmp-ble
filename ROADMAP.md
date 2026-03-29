# kmp-ble Roadmap

> Full-featured Bluetooth Low Energy for Kotlin Multiplatform — Android & iOS.

This document tracks what's shipped, what's next, and where kmp-ble is headed. Updated as milestones are reached.

**Platforms:** Android (API 33+), iOS (15+)

> **Note:** All 0.x releases may contain breaking API changes. Pin to a specific minor version for stability. See the [CHANGELOG](CHANGELOG.md) for migration notes between versions.

> **Privacy:** kmp-ble does not store, log, or transmit user data. Payload logging is byte-count only — no raw data is ever written to logs.

---

## Shipped

### v0.1.x — Core BLE Toolkit

Everything needed to build production BLE apps on Android and iOS from shared Kotlin code.

| Feature | Version | Details |
|---------|---------|---------|
| **Scanning & Discovery** | v0.1.0 | Filters (service UUID, name, manufacturer data, RSSI), emission policy (dedup/all), cold Flow API |
| **GATT Client** | v0.1.0 | Read, write (with/without response), observe (notifications/indications), descriptors, MTU negotiation |
| **14-State State Machine** | v0.1.0 | Exhaustive transition table — no invalid states. Covers transport, authentication, discovery, service changes, bonding changes |
| **Bonding & Pairing** | v0.1.0 | Just Works + Passkey Entry (standard BLE pairing methods). Proactive or implicit bonding. Bond state tracking |
| **Reconnection Strategies** | v0.1.0 | Built-in ExponentialBackoff and LinearBackoff. Configurable per-peripheral |
| **Testing Infrastructure** | v0.1.0 | FakePeripheral, FakeScanner — full BLE simulation for unit tests, no hardware required |
| **Permissions** | v0.1.0 | Cross-platform permission checking API |
| **Logging** | v0.1.0 | Structured BLE events, pluggable backends |
| **Distribution** | v0.1.0 | Maven Central (`com.atruedev:kmp-ble`) + Swift Package Manager (XCFramework) |
| **Observation Resilience** | v0.1.1 | `observe()` / `observeValues()` survive disconnects. Auto-resubscribe to CCCD on reconnect |
| **Device Quirk Registry** | v0.1.3 | Internal registry for Android OEM workarounds (Samsung, Pixel, Xiaomi, OnePlus) |
| **L2CAP Channels** | v0.1.4 | High-throughput streaming on Android and iOS. Bypass GATT for bulk data/DFU |
| **GATT Server** | v0.1.5–v0.1.6 | Peripheral role on both platforms. Define services, handle reads/writes, send notifications/indications |
| **Advertiser** | v0.1.5–v0.1.6 | BLE advertising with configurable name, service UUIDs, manufacturer data, TX power |
| **Server Testing** | v0.1.5–v0.1.6 | FakeGattServer, FakeAdvertiser, FakeL2capChannel for testing server and L2CAP code |
| **Device Quirks Module** | v0.1.10 | Extracted OEM quirks into `kmp-ble-quirks` module with public SPI (`QuirkKey`, `QuirkProvider`, `QuirkRegistry`) |

### v0.2 — Production Hardening & Platform Expansion

Hardened for production with background support, BLE 5.0 coverage, performance tooling, and complete pairing.

| Feature | Version | Details |
|---------|---------|---------|
| **iOS State Restoration** | v0.1.8–v0.1.9 | Survive app termination and restore BLE connections on cold launch via `CBCentralManager(restoreIdentifier:)` |
| **Extended Advertising (BLE 5.0) — Scanner** | v0.2 | `Advertisement` model extended with `isLegacy`, `primaryPhy`, `secondaryPhy`, `advertisingSid`, `periodicAdvertisingInterval`, `dataStatus`. `ScannerConfig.legacyOnly` flag for extended ad reception |
| **Extended Advertising (BLE 5.0) — Advertiser** | v0.2 | `ExtendedAdvertiser` interface with multiple concurrent advertising sets, PHY selection (LE 1M/2M/Coded), configurable interval. Android: `AdvertisingSet` API. iOS: legacy fallback |
| **Numeric Comparison + OOB Pairing** | v0.2 | `PairingHandler` callback for numeric comparison, passkey entry, Just Works, and OOB pairing. `PairingEvent`/`PairingResponse` sealed types. Android: handles `ACTION_PAIRING_REQUEST`. iOS: system UI with observability |
| **Benchmark Utilities** | v0.2 | `BleStopwatch`, `ThroughputMeter`, `LatencyTracker` — measure connection time, GATT throughput, and operation latency with percentile statistics |

**Test coverage:** 260+ test methods across 24 test files, CI on every push

### v0.3 — Productivity Layer

Parse data, not bytes. Type-safe BLE profiles, firmware update support, and codec infrastructure for developer productivity.

| Feature | Version | Details |
|---------|---------|---------|
| **BLE Profile Modules** | v0.3.0 | Type-safe parsers for standard profiles — Heart Rate (0x180D), Battery (0x180F), Device Information (0x180A), Cycling Speed & Cadence (0x1816), Blood Pressure (0x1810), Glucose (0x1808). Published as `com.atruedev:kmp-ble-profiles` |
| **DFU/OTA Module** | v0.3.0 | Nordic Secure DFU v2 with GATT and L2CAP transport. Observable progress, resume from failure. Published as `com.atruedev:kmp-ble-dfu` |
| **Codec Module** | v0.3.0 | Format-agnostic typed serialization/deserialization for characteristics, advertisements, L2CAP, and GATT server payloads. Published as `com.atruedev:kmp-ble-codec` |
| **API Documentation Site** | v0.3.0 | Dokka-generated API docs on [GitHub Pages](https://gary-quinn.github.io/kmp-ble/) |
| **Community Infrastructure** | v0.3.0 | CONTRIBUTING.md, ARCHITECTURE.md, "good first issue" labels, public device quirk documentation |

### Known Limitations

- Desktop (JVM), Web, and other platforms are not yet supported
- iOS `ExtendedAdvertiser` falls back to legacy advertising (CoreBluetooth limitation)
- iOS does not expose PHY or advertising set ID fields in scan results

---

## Planned

### v1.0 — Stability Guarantee

**Theme:** API stability commitment backed by production usage.

**Criteria:**

API stability:
- Core module API (Scanner, Peripheral, GattServer, Advertiser, L2capChannel) unchanged for 2+ minor releases
- Deprecation cycle enforced: deprecated APIs survive at least 1 minor release before removal
- All public APIs have KDoc documentation

Quality:
- Zero known critical bugs at release time
- Test coverage for all public API entry points
- CI green on both Android and iOS targets

Community:
- Validated in production by at least 3 external teams
- Migration guide published
- API docs site live with full coverage

Distribution:
- Semantic versioning strictly followed from v1.0 onward
- CHANGELOG.md covers every release

---

## Future Considerations (v1.x+)

Features we're tracking but not actively working on. Community interest and use cases will determine priority.

**Inclusion here does not imply commitment.** These items may be reprioritized, redesigned, or dropped based on real-world demand.

| Feature | Notes |
|---------|-------|
| JVM Desktop | Native Bluetooth stack integration. Useful for developer tooling and desktop apps |
| JS / Wasm (Web Bluetooth) | Browser-based BLE. Mobile-first strategy means this comes later |
| LE Audio / Isochronous Channels | Entirely different transport model from classic BLE |
| BLE Mesh | Large scope, niche use case |
| Direction Finding (AoA/AoD) | Requires specific hardware support |
| Periodic Advertising with Responses (PAwR) | BLE 5.4 feature, limited platform support today |
| BLE Integration Test Framework | Enhanced FakePeripheral/FakeScanner with behavior scripting (delay injection, error injection, conditional responses). Exploring whether this becomes a separate tool or stays in-library. Community interest will determine scope |
| Record-Replay Testing | Record real device GATT interactions and replay on phone for offline testing. Early exploration phase |
| Additional DFU Protocols | MCUboot, STM32 OTA, Espressif OTA. Depends on community demand and protocol documentation access |
| Additional GATT Profiles | Running Speed & Cadence, Environmental Sensing, Weight Scale, Continuous Glucose. PRs welcome — profile modules are good first contributions |

---

## How to Influence This Roadmap

- **Feature requests:** [Open an issue](https://github.com/gary-quinn/kmp-ble/issues) describing your use case
- **Bug reports:** Include platform, device model, and minimal reproduction
- **Contributions:** Device quirk entries and profile modules are great first contributions — PRs welcome

---

*Current as of v0.3.8*
