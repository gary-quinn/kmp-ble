# kmp-ble Roadmap

> Full-featured Bluetooth Low Energy for Kotlin Multiplatform — Android & iOS.

This document tracks what's shipped, what's next, and where kmp-ble is headed. Updated as milestones are reached.

**Platforms:** Android (API 33+), iOS (15+)

> **Note:** All 0.x releases may contain breaking API changes. Pin to a specific minor version for stability. See the [CHANGELOG](CHANGELOG.md) for migration notes between versions.

> **Privacy:** kmp-ble does not store, log, or transmit user data. Payload logging is byte-count only — no raw data is ever written to logs.

---

## Shipped

### v0.1.x — Core BLE Toolkit (Current)

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

**Test coverage:** 236+ test methods across 20 test files, CI on every push

### Known Limitations

- No background scanning on iOS without state restoration (planned for v0.2)
- Desktop (JVM), Web, and other platforms are not yet supported

---

## Up Next

### v0.2 — Production Hardening & Platform Expansion

**Theme:** Harden kmp-ble for production with background support, broader BLE 5.0 coverage, and published performance data.

| Feature | Priority | Description |
|---------|----------|-------------|
| **iOS State Restoration** | High | Survive app termination and restore BLE connections on cold launch. Required for medical/fitness background BLE. Uses `CBCentralManager(restoreIdentifier:)` |
| **Extended Advertising (BLE 5.0)** | Medium | Scanner support for extended advertisements (payloads > 31 bytes). Advertiser support for multiple advertisement sets |
| **Performance Benchmarks** | Medium | Published throughput, latency, and reconnection time measurements against real hardware |
| **Power Benchmarks** | Medium | Scan energy, connected idle, and notification stream power consumption |
| **Numeric Comparison + OOB Pairing** | Low | Complete bonding coverage beyond Just Works and Passkey Entry |

---

## Planned

### v0.3 — Productivity Layer

**Theme:** Parse data, not bytes. Make developers productive with type-safe BLE profiles and firmware update support.

| Feature | Description |
|---------|-------------|
| **BLE Profile Modules** | Type-safe parsers for standard profiles — Heart Rate (0x180D), Battery (0x180F), Device Information (0x180A), Cycling (0x1816/0x1818), Blood Pressure (0x1810), Glucose (0x1808). Published as a separate opt-in artifact (`com.atruedev:kmp-ble-profiles`) |
| **DFU/OTA Module** | Firmware update support starting with Nordic DFU protocol. Observable progress, resume from failure. Published as `com.atruedev:kmp-ble-dfu` |
| **Community Infrastructure** | CONTRIBUTING.md, ARCHITECTURE.md, "good first issue" labels, public device quirk documentation |

### v1.0 — Stability Guarantee

**Theme:** API stability commitment backed by production usage.

**Criteria:**
- Core API stable across 3+ minor releases
- Validated in production by multiple teams
- No critical bugs in last 3 releases
- Comprehensive documentation
- Semantic versioning strictly followed — no breaking changes in 1.x

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

---

## How to Influence This Roadmap

- **Feature requests:** [Open an issue](https://github.com/atruedeveloper/kmp-ble/issues) describing your use case
- **Bug reports:** Include platform, device model, and minimal reproduction
- **Contributions:** Device quirk entries and profile modules are great first contributions — PRs welcome

---

*Current as of v0.1.7*
