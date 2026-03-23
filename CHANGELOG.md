# Changelog

All notable changes to kmp-ble are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

> **Note:** All 0.x releases may contain breaking API changes. Pin to a specific minor version for stability.

---

## [Unreleased]

_Changes on `main` that have not yet been tagged for release._

---

## [0.3.3] - 2026-03-23

### Other
- update Package.swift for v0.3.3
- docs: add Dokka 2.x API reference and DFU module KDoc (#59)
- docs: update ROADMAP.md to reflect v0.3 shipped state (#58)
- update Package.swift for v0.3.3-alpha1


---

## [0.3.3-alpha1] - 2026-03-23

### Other
- update Package.swift for v0.3.3-alpha1
- ci: fix GitHub Pages deployment on tag push (#56)
- update Package.swift for v0.3.2


---

## [0.3.2] - 2026-03-23

### Other
- update Package.swift for v0.3.2
- docs: add Dokka 2.x API reference with GitHub Pages deployment (#54)
- update Package.swift for v0.3.1


---

## [0.3.1] - 2026-03-23

### Other
- update Package.swift for v0.3.1
- feat(sample): integrate profiles, DFU, and codec modules (#52)
- build: integrate ktlint for static formatting checks (#51)
- update Package.swift for v0.3.0


---

## [0.3.0] - 2026-03-22

### Added
- feat: add format-agnostic codec module (kmp-ble-codec) (#48)
- feat: add kmp-ble-dfu module with Nordic Secure DFU v2 (#47)
- feat: add kmp-ble-profiles module with type-safe GATT profile parsing (#46)

### Other
- update Package.swift for v0.3.0
- ci(dependabot): bump actions/github-script from 7.0.1 to 8.0.0 (#45)
- update Package.swift for v0.2.2


---

## [0.2.2] - 2026-03-22

### Fixed
- fix: use project.exec in assembleXCFramework task


---

## [0.1.10] - 2026-03-21

### Changed
- refactor: extract device quirks into kmp-ble-quirks module


---

## [0.1.9] - 2026-03-21

### Added
- feat(ios): add Core Bluetooth state restoration support


---

## [0.1.8] - 2026-03-20

### Added
- feat(ios): add Core Bluetooth state restoration support


---

## [0.1.7] - 2026-03-19

### Changed
- Repository-wide code review cleanup for v0.2 readiness
- Trimmed unnecessary comments across the codebase
- CI: auto-merge the update-README PR to reduce manual steps

---

## [0.1.6] - 2026-03-19

### Changed
- Repository-wide code review cleanup — improved consistency, removed dead code, tightened access modifiers

---

## [0.1.5] - 2026-03-18

### Added
- **GATT Server (iOS)** — peripheral role using `CBPeripheralManager`. Define services, handle read/write requests, send notifications and indications
- **Advertiser (iOS)** — BLE advertising via `CBPeripheralManager` with configurable name, service UUIDs, and manufacturer data

### Changed
- Use `BleData.slice` for zero-copy offset reads in server handlers
- Thread `BleData` through server handler and notify signatures for consistency
- Replace flaky delay-based waits with polling in L2CAP tests
- Improve SOLID compliance and address DRY violations across server code

### Fixed
- `close()` ordering and `readyToUpdate` safety in iOS GATT server
- Thread-safety and robustness issues surfaced during code review

---

## [0.1.4] - 2026-03-18

### Added
- **GATT Server (Android)** — peripheral role using `BluetoothGattServer`. DSL builder for defining services, characteristics, and descriptors with read/write/notify support
- **Advertiser (Android)** — BLE advertising with configurable mode (LowPower, Balanced, LowLatency), TX power levels, service UUIDs, and manufacturer data
- **FakeGattServer** and **FakeAdvertiser** — test doubles for server and advertiser code

### Changed
- Configurable advertise mode and TX power included in `AdvertiseConfig` equals/hashCode/toString
- Connection limit warning when max server connections approached

### Fixed
- `close()` deadlock in Android GATT server
- Thread-safety issues in subscription index management
- Dead code and DSL correctness issues surfaced during architect review

---

## [0.1.3] - 2026-03-18

### Added
- **L2CAP Channels (Android)** — high-throughput streaming via `BluetoothSocket`, bypassing GATT for bulk data and DFU transfers
- **FakeL2capChannel** — test double for L2CAP channel code with socket abstraction

### Fixed
- Removed dead partial-write loop in L2CAP channel implementation

---

## [0.1.2] - 2026-03-18

### Added
- **L2CAP Channels (iOS)** — high-throughput streaming via `CBL2CAPChannel`, bypassing GATT for bulk data and DFU transfers

### Changed
- Repository URLs migrated from `gary-quinn` to `atruedeveloper`

---

## [0.1.1] - 2026-03-17

### Added
- **Device Quirk Registry** — internal registry for Android OEM-specific BLE workarounds (Samsung, Pixel, Xiaomi, OnePlus)
- Consumer ProGuard rules bundled in the AAR
- CI: test suite runs on every push
- CI: workflow to auto-update README on release

### Fixed
- Checkout main branch for release-triggered CI workflow

---

## [0.1.0] - 2026-03-17

### Added
- **Scanning & Discovery** — filters (service UUID, name, manufacturer data, RSSI), emission policy (dedup/all), cold Flow API
- **GATT Client** — read, write (with/without response), observe (notifications/indications), descriptors, MTU negotiation
- **14-State State Machine** — exhaustive transition table with no invalid states, covering transport, authentication, discovery, service changes, and bonding changes
- **Bonding & Pairing** — Just Works + Passkey Entry. Proactive or implicit bonding with bond state tracking
- **Reconnection Strategies** — built-in `ExponentialBackoff` and `LinearBackoff`, configurable per-peripheral
- **Observation Resilience** — `observe()` / `observeValues()` survive disconnects and auto-resubscribe to CCCD on reconnect
- **Testing Infrastructure** — `FakePeripheral`, `FakeScanner` for full BLE simulation in unit tests without hardware
- **Permissions** — cross-platform BLE permission checking API
- **Logging** — structured BLE event logging with pluggable backends
- **Distribution** — Maven Central (`com.atruedev:kmp-ble`) + Swift Package Manager (XCFramework)

### Changed
- Renamed package to `com.atruedev.kmpble`, group to `com.atruedev`
- UUID-based tracking for reconnection resilience (replacing index-based)

---

## [0.1.0-alpha09] - 2026-03-17

### Changed
- Package renamed to `com.atruedev.kmpble`
- Copyright updated to Huynh Thien Thach
- README rewritten with badges, common factories, bonding/reconnection/permissions/logging examples

---

## [0.1.0-alpha06] - 2026-03-17

### Added
- Bonding — Just Works + Passkey Entry
- `FakeScanner` + property-based state machine tests
- Logging infrastructure + error model refinement
- BLE permissions check API
- Sample app with auto-init
- Migration guide — API mapping and key differences from other BLE libraries

### Fixed
- iOS scanner initialization issue

---

## [0.1.0-alpha01] - 2026-03-15

### Added
- Initial release — BLE scanning, connecting, GATT read/write/observe
- Android and iOS platform support
- CI/CD with GitHub Actions and Dependabot

---

[Unreleased]: https://github.com/gary-quinn/kmp-ble/compare/v0.3.3...HEAD
[0.3.3]: https://github.com/gary-quinn/kmp-ble/compare/v0.3.3-alpha1...v0.3.3
[0.3.3-alpha1]: https://github.com/gary-quinn/kmp-ble/compare/v0.3.2...v0.3.3-alpha1
[0.3.2]: https://github.com/gary-quinn/kmp-ble/compare/v0.3.1...v0.3.2
[0.3.1]: https://github.com/gary-quinn/kmp-ble/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/gary-quinn/kmp-ble/compare/v0.2.2...v0.3.0
[0.2.2]: https://github.com/gary-quinn/kmp-ble/compare/v0.2.1...v0.2.2
[0.1.10]: https://github.com/gary-quinn/kmp-ble/compare/v0.1.9...v0.1.10
[0.1.9]: https://github.com/gary-quinn/kmp-ble/compare/v0.1.8-alpha2...v0.1.9
[0.1.8]: https://github.com/gary-quinn/kmp-ble/compare/v0.1.8-alpha2...v0.1.8
[0.1.7]: https://github.com/gary-quinn/kmp-ble/compare/v0.1.6...v0.1.7
[0.1.6]: https://github.com/gary-quinn/kmp-ble/compare/v0.1.5...v0.1.6
[0.1.5]: https://github.com/gary-quinn/kmp-ble/compare/v0.1.4...v0.1.5
[0.1.4]: https://github.com/gary-quinn/kmp-ble/compare/v0.1.3...v0.1.4
[0.1.3]: https://github.com/gary-quinn/kmp-ble/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/gary-quinn/kmp-ble/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/gary-quinn/kmp-ble/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/gary-quinn/kmp-ble/compare/v0.1.0-alpha09...v0.1.0
[0.1.0-alpha09]: https://github.com/gary-quinn/kmp-ble/compare/v0.1.0-alpha06...v0.1.0-alpha09
[0.1.0-alpha06]: https://github.com/gary-quinn/kmp-ble/compare/v0.1.0-alpha01...v0.1.0-alpha06
[0.1.0-alpha01]: https://github.com/gary-quinn/kmp-ble/releases/tag/v0.1.0-alpha01
