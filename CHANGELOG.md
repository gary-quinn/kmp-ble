# Changelog

All notable changes to kmp-ble are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

> **Note:** All 0.x releases may contain breaking API changes. Pin to a specific minor version for stability.

---

## [0.8.5] - 2026-06-18

### Added
- feat(sample): add BleQuickstart end-to-end code examples and update README (#227)
- test(benchmark): add cross-platform benchmark comparison tests (#228)

### Fixed
- fix: replace em-dashes with hyphens in comments and README

---

## [Unreleased]

_Changes on `main` that have not yet been tagged for release._

---

## [0.10.0] - 2026-07-13

### Added
- feat(benchmark): add BleBenchmark utility for operation latency and throughput
- feat(sample): add BleQuickstart end-to-end code examples and update README
- feat(error): add recoveryHint to all BleError types
- feat(monitoring): add ConnectionQualityMonitor with RSSI and connection lifecycle tracking
- feat(conformance): add cross-platform conformance test harness
- feat(l2cap): add L2CAP conformance tests to BleConformanceTest
- feat(beacon): add iBeacon and Eddystone scanning support
- feat(phy): add readPhy() and phyUpdate flow to Peripheral (#263)
- feat(server): add periodic advertising support to ExtendedAdvertiser
- feat(connection): add LE Connection Parameter Update request support
- feat(cache): add GattCache for fast GATT service reconnection
- feat(monitoring): add PowerMonitor for LE path loss tracking
- feat(scanner): add PHY selection for BLE scanning parameters
- feat(power): add LePowerController for active peer power adjustment
- feat(scanner): add LE Coded PHY long-range scanning support with dedicated ScanPhy enum
- feat(ios): add IosPairingRequestHandler for GATT server pairing parity
- feat(dx): add ConnectionOptions presets and ScannerConfig.default
- feat(scanner): add AdvertisingDataBuilder DSL for scan record construction
- feat(dx): add connectAndDiscover convenience combining connection and service discovery
- feat(le-audio): add openIsochronousChannel to Peripheral interface
- feat(retry): add configurable GATT operation retry policies with exponential backoff
- feat(gatt): add mtu StateFlow and document requestMtu for MTU negotiation API
- feat(gatt): persist CCC descriptor states for seamless GATT reconnection
- feat(connection): add ConnectionParamPreset enums for common BLE connection configurations
- feat(connection): add ConnectionOptions.validate() warnings for common misconfigurations
- feat(error): add ServiceDiscoveryError, CharacteristicError, and ConnectionFailureReason
- feat(logging): add platform loggers and integrate scan lifecycle logging
- feat(ios): add IosBondManager for bond/pairing management parity
- feat(adapter): add BleCapabilities for Bluetooth 5.x feature detection
- feat(quirks): add iOS device quirk detection, move core types to commonMain
- feat(periodic): add Periodic Advertising Sync Transfer (PAST) API for BLE 5.1
- feat(le-audio): add IsochronousStream streaming API for BLE 5.2+
- feat(connection): add LE Connection Subrating API for Bluetooth 5.3
- feat(parity): add LE Data Length Extension API for Peripheral
- feat(direction): add BLE Direction Finding (AoA/AoD) API for Bluetooth 5.1+
- feat(dfu): expose public OTA DFU transport API
- feat(scanner): add configurable scanMode to ScannerConfig

### Changed
- ci(dependabot): bump gradle/actions from 6.1.0 to 6.2.0
- test(benchmark): add cross-platform benchmark comparison tests
- chore(release): prep v0.8.5 -- version bump, Package.swift, docs
- refactor(benchmark): extract duplicated try-catch into benchmarkTimed helper
- refactor(benchmark): extract benchmark package to separate kmp-ble-benchmark module
- refactor(server): decompose AndroidGattServer (1003 loc) into 5 focused files
- refactor(server): decompose AndroidGattServerCallback (444→126 loc)
- refactor(android): decompose AndroidPeripheral 876->334 loc via extension functions
- refactor(ios): decompose IosPeripheral 713->390 loc via extension functions
- refactor(ios): decompose IosGattServer 684->390 loc via extension functions
- refactor(ios): decompose IosGattServer 390->228 loc (second pass)
- refactor(android): extract GattCallbackEvent sealed interface from AndroidGattBridge
- refactor(android): decompose AndroidPeripheral 380->236 via extension functions (#306)
- refactor(peripheral): trim verbose KDoc in Peripheral interface (300->216 lines)
- refactor(testing): extract FakePeripheral simulation methods to extension functions
- chore: add .hermes/ and TODO.md to gitignore
- chore: remove pipeline comment from gitignore
- refactor(testing): extract stub GATT methods to FakeGattResponderStubs extension file
- refactor(beacon): extract beacon parsing internals to BeaconParser.kt
- refactor(ios): decompose IosPeripheral facade from 411 to 286 lines via extension functions
- test(scanner): add Android Scanner integration tests for scan modes, filters, and edge cases
- test(conformance): split BleConformanceTest into focused files
- test(dfu): add integration tests for OTA DFU transport layers
- style(dfu): replace FQN type names with imports in DFU tests
- test(connection): add edge-case tests for ReconnectionHandler backoff and cancellation
- chore(connection): remove duplicate timeouts param, delete dead PeripheralTimeout.kt
- ci(dependabot): bump actions/checkout from 6.0.3 to 7.0.0
- ci(dependabot): bump actions/setup-java from 5.2.0 to 5.3.0
- build(dependabot): bump gradle-wrapper from 9.5.1 to 9.6.0
- build(dependabot): bump org.jetbrains.kotlinx:atomicfu from 0.27.0 to 0.33.0
- test(observation): add ObservationPersistence cross-platform roundtrip tests
- chore(todo): mark #424 and #318 as done (PR #423 merged)
- chore(todo): mark #368 and #359 as done
- test(parity): add DataLengthParameters test coverage for DLE API
- test(connection): add edge-case tests for Connection Parameter Update negotiation
- test(gatt): add cross-platform GATT server conformance tests
- test(benchmark): add iOS and Android benchmark runners for kmp-ble-benchmark
- test(ios): add IosPeripheral GATT event handling integration tests
- test(monitoring): add PowerMonitor and LePowerController edge case tests
- test(gatt): add GATT write-type conformance tests for Write Request vs Write Command
- test(advertiser): add cross-platform Advertiser conformance tests
- chore(todo): mark 8 testing/completed items as done with PR references
- test(peripheral): add platform smoke tests for dataLengthParameters (#428)
- refactor(ios): decompose IosPeripheral (326->240 lines) by extracting connect/disconnect/close/refreshServices
- test(peripheral): add FakePeripheral dataLengthParameters integration tests
- build(dependabot): bump com.vanniktech.maven.publish from 0.36.0 to 0.37.0
- ci(dependabot): bump actions/setup-java from 5.3.0 to 5.4.0
- ci(dependabot): bump actions/cache from 5.0.5 to 6.1.0
- build(dependabot): bump gradle-wrapper from 9.6.0 to 9.6.1
- test(scanner): add Android Scanner integration tests for scan modes, filters, and edge cases (#335)
- ci(dependabot): bump reactivecircus/android-emulator-runner from 2.37.0 to 2.38.0
- ci(dependabot): bump actions/setup-java from 5.4.0 to 5.5.0

### Fixed
- fix: pass null to scanForPeripheralsWithServices, not service filter UUIDs
- fix(ios): prevent CoreBluetooth crash on repeated service discovery
- fix(ios): conditionally implement willRestoreState only when state restoration enabled
- fix(kmp-314): replace qualified kotlinx.coroutines names with imports
- fix(server): add WHY comments for runBlocking in close() methods
- fix(ci): use last docs-update merge commit as changelog baseline
- fix(server): rethrow CancellationException in notify() async catch block
- fix(android): rethrow CancellationException in pairing handler scope.launch
- fix(android): convert expression-body overrides to block bodies for explicitApi()
- fix(android): rethrow CancellationException in periodic advertising callback scope.launch
- fix(cache): replace synchronized blocks with Mutex in AndroidGattCache
- fix(cache): replace Mutex with single-threaded dispatcher in AndroidGattCache
- fix(concurrency): replace @Volatile with kotlinx-atomicfu in commonMain
- fix(test): use buildPeripheral factory in Gatt notification test
- fix(connection): remove dead ConnectionOptions.timeout, wire OperationTimeouts
- fix(concurrency): use atomicfu for AdvertisingDataBuilder counter to eliminate data race
- fix(style): replace FQN Duration.ZERO/INFINITE with imported Duration in OperationTimeoutsTest
- fix(persistence): extract shared serialization to commonMain, add Android SharedPreferences roundtrip tests
- fix(ios): remove dead createBond() from IosBondManager
- fix(concurrency): use atomic for QuirkRegistry cached singleton
- fix(style): replace FQN kotlin.time.Duration with import in IosOemQuirkProvider
- fix(isochronous): wire IsochronousStreamConfig parameters into stream behavior
- fix(style): resolve dangling KDoc [createSync] reference in PeriodicAdvertisingSync
- fix(style): replace FQN PeriodicAdvertisingParameters with import in PeriodicReport
- fix(concurrency): complete @Volatile to atomicfu migration
- fix(connection): add deprecated timeout constructor shim to ConnectionOptions
- fix(ci): update Package.swift via PR instead of direct push to main
- fix(test): resolve KMP-WD1 build-break and fix test compilation issues
- fix(style): replace FQN Phy references with import in AndroidScannerFiltersTest
- fix(test): rename AndroidScannerFiltersTest to FakeScannerFiltersTest and remove redundant StateFlow assertion
- fix(concurrency): migrate @Volatile fields to atomicfu
- fix(ios): reject duplicate connect callback to prevent overlapping discovery cycles
- fix(ci): update upload/download-artifact SHAs to verified v4 tags

### Other
- docs(changelog): populate v0.8.4 release notes with all changes since v0.8.3
- docs(l2cap): add architecture documentation for L2CAP subsystem
- docs(parity): add platform API surface parity audit
- docs(setup): add platform setup guides and troubleshooting for iOS and Android
- docs(api): add API quick reference with code snippets for common BLE workflows
- docs(migration): add v0.8.x to v0.9.0 migration guide with breaking changes and new features
- docs(dx): add production integration patterns and BLE recovery guide
- docs(background): add background BLE operation patterns for iOS and Android
- docs(beacon): clarify CancellationException comment in BeaconScanner.close()
- docs(peripheral): fix @throws GattException reference to BleException in connectAndDiscover KDoc
- docs(quirks): fix KDoc link registerIosProvider -> IosQuirkProviders.register


### Added
- feat(roles): add `SimultaneousRoleManager` for concurrent central and peripheral operation (Closes #289)

---

## [0.8.4] - 2026-06-18

### Added
- feat(benchmark): add BleBenchmark utility for operation latency and throughput (#225)

### Changed
- fix(server): add WHY comments for runBlocking in close() methods (#224)

### Fixed
- fix(ios): prevent CoreBluetooth crash on repeated service discovery
- fix(ios): remove dead quiesce code in close() (#220)
- fix(ios): conditionally implement willRestoreState only when state restoration enabled (#222)
- fix(ios): replace qualified NSError with imported type in CentralDelegateImpl and CentralDelegateState
- fix(kmp-314): replace qualified kotlinx.coroutines names with imports in AndroidGattServer, IosGattServer, FakePeripheral (#223)
- fix: add missing TimeoutCancellationException import in AndroidGattServer


---

## [0.8.3] - 2026-06-15

### Changed
- ci(dependabot): bump gradle/actions from 6.1.0 to 6.2.0

### Fixed
- fix: pass null to scanForPeripheralsWithServices, not service filter UUIDs
- fix(test): resolve KMP-310/KMP-311 failing and flaky tests


---

## [0.8.2] - 2026-06-12

### Changed
- ci(dependabot): bump actions/checkout from 6.0.2 to 6.0.3
- build(dependabot): bump org.jetbrains.compose from 1.11.0 to 1.11.1
- build(dependabot): bump kotlin from 2.3.21 to 2.4.0
- build(dependabot): bump androidx.core:core-ktx from 1.18.0 to 1.19.0
- refactor(testing): decompose FakePeripheral into FakeConnectionSimulator + FakeGattResponder
- refactor(shared): decompose ObservationManager into ObservationRegistry + ObservationEmitter

### Fixed
- fix(shared): rethrow CancellationException in catch blocks
- fix(shared): use import for CancellationException instead of qualified name
- fix(shared): require serial dispatcher for ObservationManager
- fix: move JVM-only virtual time tests to jvmTest source set
- fix: retrieve bonded peripherals before scanning on iOS

### Other
- docs(getting-started): add comprehensive 5-minute onboarding guide


---

## [0.8.1] - 2026-06-01

### Added
- feat: clear Android GATT cache before refreshServices()


---

## [0.8.0] - 2026-06-01

### Added
- feat!: surface scan failures as ScanEvent instead of crashing

### Changed
- build(dependabot): bump org.jetbrains.lincheck:lincheck from 3.5 to 3.6


---

## [0.7.1] - 2026-05-19

### Fixed
- fix(ios): open NSStreams before reading from L2CAP channel


---

## [0.7.0] - 2026-05-18

### Added
- feat(sample): pin and highlight kmp-ble servers in scanner
- feat(codec): add L2capListener.framedConnections for typed server streams
- feat(codec): distinguish PeripheralNotReady from CharacteristicNotFound
- feat(sample): add L2CAP blob stream demo with 3-layer stats

### Changed
- build(dependabot): bump org.jetbrains.compose from 1.10.3 to 1.11.0
- build(dependabot): bump gradle-wrapper from 9.5.0 to 9.5.1

### Fixed
- fix(ci): bump-docs regex matches multi-segment module suffixes
- fix(sample): apply kotlinx-serialization plugin for CBOR codec
- fix(server): re-throw CancellationException in Android GATT handlers
- fix(deps): pin OpenTelemetry to 1.62.0 for CVE-2026-45292

### Other
- docs(architecture): document L2CAP server and typed codec layer
- docs(streams): add HOWTO for typed L2CAP streams


---

## [0.6.0] - 2026-05-12

### Added
- feat(profiles): add typed Codec API with length-prefix framing
- feat(l2cap): add server-side L2capListener for peripheral mode
- feat(codec): add framed L2CAP convenience extensions
- feat(codec): add kmp-ble-codec-serialization with CBOR adapter
- feat(sample): replace L2CAP echo demo with CBOR-framed sensor stream

### Changed
- build(dependabot): bump coroutines from 1.10.2 to 1.11.0
- build(dependabot): bump agp from 9.2.0 to 9.2.1
- refactor: consolidate Phase 0 codec into kmp-ble-codec module

### Fixed
- fix(deps): bump netty to 4.1.133.Final to fix CVE-2026-41417


---

## [0.5.0] - 2026-05-05

### Added
- feat(scanner): expose raw advertising payload via RawAdvertising

### Changed
- ci: add typography-check job
- refactor(peripheral): confine lifecycle deferreds to serial dispatcher
- build(dependabot): bump kotlin from 2.3.20 to 2.3.21
- build(dependabot): bump agp from 9.1.1 to 9.2.0
- build(dependabot): bump gradle-wrapper from 9.4.1 to 9.5.0
- refactor(sample): restore working demo from v0.3.8 baseline

### Added
- `Advertisement.rawAdvertising`: optional `RawAdvertising` exposing the on-air
  AD record. `RawAdvertising.OnAir` on Android (from `ScanRecord.getBytes()`);
  `RawAdvertising.Reconstructed` on iOS, re-encoded as TLV from the
  CoreBluetooth advertisement dictionary because the public CoreBluetooth API
  does not surface the wire payload.

### Changed
- `Advertisement` is now a `data class`. Manual `equals` / `hashCode` removed;
  `copy` and component functions are autogenerated. `platformContext` moved
  out of the primary constructor (internal-only) and is excluded from equality.

---

## [0.4.3] - 2026-04-25

### Changed
- ci(dependabot): bump actions/cache from 5.0.4 to 5.0.5
- ci(dependabot): bump actions/github-script from 8.0.0 to 9.0.0
- build(dependabot): bump agp from 9.1.0 to 9.1.1
- style: replace em-dashes with hyphens across repo
- chore: add agent guidelines and typography pre-commit hook

### Fixed
- fix(deps): pin Bouncy Castle to 1.84 to resolve security advisories


---

## [0.4.2] - 2026-04-16

### Changed
- refactor: replace non-null assertions; feat(l2cap): configurable iOS MTU


---

## [0.4.1] - 2026-04-13

### Fixed
- fix(ci): use macos runner for Dokka API docs generation


---

## [0.4.0] - 2026-04-13

### Changed
- refactor(shared): unify error handling and reduce duplication across peripherals
- refactor(shared): unify pending ops, observe pattern, and scan predicate equality
- ci(dependabot): bump gradle/actions from 6.0.1 to 6.1.0
- ci(dependabot): bump actions/cache from 4.2.0 to 5.0.4
- ci(dependabot): bump peter-evans/create-pull-request from 8.1.0 to 8.1.1
- ci(dependabot): bump actions/upload-artifact from 7.0.0 to 7.0.1
- ci(dependabot): bump actions/upload-pages-artifact from 4.0.0 to 5.0.0
- build(dependabot): bump org.robolectric:robolectric from 4.14.1 to 4.16.1
- build(dependabot): bump androidx.test:runner from 1.6.2 to 1.7.0
- build(dependabot): bump androidx.test.ext:junit-ktx from 1.2.1 to 1.3.0
- build(dependabot): bump androidx.test:core-ktx from 1.6.1 to 1.7.0

### Fixed
- fix(shared): replace iOS connection polling with ObjC delegate proxy, use structured GATT errors
- fix(shared): eliminate TOCTOU race in GattOperationQueue and harden StateMachine hierarchy
- fix(shared): add missing volatile annotation and remove redundant cleanup
- fix: harden lifecycle safety and fix docs CI runner
- fix(ci): enable cinterop commonization for Dokka docs generation
- fix(shared): harden thread safety and unify error types across peripherals


---

## [0.3.18] - 2026-04-08

### Changed
- chore: remove duplicate CHANGELOG entry for v0.3.15
- test(ios): add iOS platform tests for GATT status mapper and peripheral manager delegate

### Fixed
- fix(shared): align MtuExceeded with BleError hierarchy
- fix(ios): evict idle centrals from GATT server via IdleTracker


---

## [0.3.17] - 2026-04-07

### Fixed
- fix(android): correct package name in ProGuard consumer rules


---

## [0.3.16] - 2026-04-06

_No notable changes._


---

## [0.3.15] - 2026-04-05

### Fixed
- fix(ci): use connectedAndroidDeviceTest to run instrumented tests


---

## [0.3.13] - 2026-04-05

### Other
- update Package.swift for v0.3.13
- fix(ci): strip v prefix from VERSION env var in publish workflow (#102)
- ci(dependabot): bump gradle/actions from 6.0.1 to 6.1.0 (#100)
- build(dependabot): bump org.jetbrains.lincheck:lincheck from 3.4 to 3.5 (#101)
- update Package.swift for v0.3.12


---

## [0.3.12] - 2026-04-02

### Added
- feat: configurable GATT timeout, reconnection jitter, strict mode (#98)

### Other
- update Package.swift for v0.3.12
- update Package.swift for v0.3.11


---

## [0.3.11] - 2026-04-01

### Fixed
- fix: address review findings - race conditions, error types, code duplication (#96)

### Other
- update Package.swift for v0.3.11
- docs: update copyright holder in LICENSE file
- update Package.swift for v0.3.10


---

## [0.3.10] - 2026-03-30

### Other
- update Package.swift for v0.3.10
- fix(server): handle Prepared Write (Write Long) in GattServer (#92)
- update Package.swift for v0.3.9


---

## [0.3.9] - 2026-03-29

### Other
- update Package.swift for v0.3.9
- docs: remove CONTRIBUTING and ROADMAP, clean up references (#90)
- refactor(sample): merge ble-toolkit as primary sample app (#88)
- update Package.swift for v0.3.8


---

## [0.3.8] - 2026-03-29

### Fixed
- fix: remove unnecessary casts in test assertions (#79)

### Other
- update Package.swift for v0.3.8
- build: upgrade ktlint to 14.2.0 with formatting fixes (#86)
- ci(dependabot): bump actions/upload-pages-artifact from 3.0.1 to 4.0.0 (#83)
- ci(dependabot): bump gradle/actions from 5.0.2 to 6.0.1 (#82)
- ci(dependabot): bump actions/deploy-pages from 4.0.5 to 5.0.0 (#84)
- build(dependabot): bump org.jetbrains.dokka from 2.1.0 to 2.2.0 (#85)
- update Package.swift for v0.3.7


---

## [0.3.7] - 2026-03-27

### Fixed
- fix: bump Netty to 4.1.132.Final for CVE remediation (#78)

### Other
- update Package.swift for v0.3.7
- docs: add KDoc to all undocumented public API surface (#77)
- update Package.swift for v0.3.6


---

## [0.3.6] - 2026-03-27

### Other
- update Package.swift for v0.3.6
- feat(dfu): add MCUboot and ESP OTA DFU protocols (#72)
- docs: remove unverified consumer count from roadmap (#71)
- docs: add BLE Toolkit showcase to README (#70)
- update Package.swift for v0.3.5


---

## [0.3.5] - 2026-03-25

### Fixed
- fix: resolve all 21 Dependabot security alerts (#68)
- fix: update outdated GitHub repository URL (#65)

### Other
- update Package.swift for v0.3.5
- docs: add llms.txt for LLM-friendly project documentation (#67)
- ci: add dependabot.yml (#66)
- Update GitHub Sponsors username in FUNDING.yml (#64)
- ci: skip docs job on pull requests (#63)
- update Package.swift for v0.3.4


---

## [0.3.4] - 2026-03-23

### Other
- update Package.swift for v0.3.4
- feat(testing): enhance FakePeripheral with write handling, delay/error injection, and concurrency tests (#61)
- update Package.swift for v0.3.3
- docs: add Dokka 2.x API reference and DFU module KDoc (#59)
- docs: update ROADMAP.md to reflect v0.3 shipped state (#58)
- update Package.swift for v0.3.3-alpha1


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
- Repository-wide code review cleanup - improved consistency, removed dead code, tightened access modifiers

---

## [0.1.5] - 2026-03-18

### Added
- **GATT Server (iOS)** - peripheral role using `CBPeripheralManager`. Define services, handle read/write requests, send notifications and indications
- **Advertiser (iOS)** - BLE advertising via `CBPeripheralManager` with configurable name, service UUIDs, and manufacturer data

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
- **GATT Server (Android)** - peripheral role using `BluetoothGattServer`. DSL builder for defining services, characteristics, and descriptors with read/write/notify support
- **Advertiser (Android)** - BLE advertising with configurable mode (LowPower, Balanced, LowLatency), TX power levels, service UUIDs, and manufacturer data
- **FakeGattServer** and **FakeAdvertiser** - test doubles for server and advertiser code

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
- **L2CAP Channels (Android)** - high-throughput streaming via `BluetoothSocket`, bypassing GATT for bulk data and DFU transfers
- **FakeL2capChannel** - test double for L2CAP channel code with socket abstraction

### Fixed
- Removed dead partial-write loop in L2CAP channel implementation

---

## [0.1.2] - 2026-03-18

### Added
- **L2CAP Channels (iOS)** - high-throughput streaming via `CBL2CAPChannel`, bypassing GATT for bulk data and DFU transfers

### Changed
- Repository URLs migrated from `gary-quinn` to `atruedeveloper`

---

## [0.1.1] - 2026-03-17

### Added
- **Device Quirk Registry** - internal registry for Android OEM-specific BLE workarounds (Samsung, Pixel, Xiaomi, OnePlus)
- Consumer ProGuard rules bundled in the AAR
- CI: test suite runs on every push
- CI: workflow to auto-update README on release

### Fixed
- Checkout main branch for release-triggered CI workflow

---

## [0.1.0] - 2026-03-17

### Added
- **Scanning & Discovery** - filters (service UUID, name, manufacturer data, RSSI), emission policy (dedup/all), cold Flow API
- **GATT Client** - read, write (with/without response), observe (notifications/indications), descriptors, MTU negotiation
- **14-State State Machine** - exhaustive transition table with no invalid states, covering transport, authentication, discovery, service changes, and bonding changes
- **Bonding & Pairing** - Just Works + Passkey Entry. Proactive or implicit bonding with bond state tracking
- **Reconnection Strategies** - built-in `ExponentialBackoff` and `LinearBackoff`, configurable per-peripheral
- **Observation Resilience** - `observe()` / `observeValues()` survive disconnects and auto-resubscribe to CCCD on reconnect
- **Testing Infrastructure** - `FakePeripheral`, `FakeScanner` for full BLE simulation in unit tests without hardware
- **Permissions** - cross-platform BLE permission checking API
- **Logging** - structured BLE event logging with pluggable backends
- **Distribution** - Maven Central (`com.atruedev:kmp-ble`) + Swift Package Manager (XCFramework)

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
- Bonding - Just Works + Passkey Entry
- `FakeScanner` + property-based state machine tests
- Logging infrastructure + error model refinement
- BLE permissions check API
- Sample app with auto-init
- Migration guide - API mapping and key differences from other BLE libraries

### Fixed
- iOS scanner initialization issue

---

## [0.1.0-alpha01] - 2026-03-15

### Added
- Initial release - BLE scanning, connecting, GATT read/write/observe
- Android and iOS platform support
- CI/CD with GitHub Actions and Dependabot

---

[Unreleased]: https://github.com/gary-quinn/kmp-ble/compare/v0.10.0...HEAD
[0.10.0]: https://github.com/gary-quinn/kmp-ble/compare/v0.9.0...v0.10.0
[0.8.5]: https://github.com/gary-quinn/kmp-ble/compare/v0.8.4...v0.8.5
[0.8.4]: https://github.com/gary-quinn/kmp-ble/compare/v0.8.3...v0.8.4
[0.8.3]: https://github.com/gary-quinn/kmp-ble/compare/v0.8.2...v0.8.3
[0.8.2]: https://github.com/gary-quinn/kmp-ble/compare/v0.8.1...v0.8.2
[0.8.1]: https://github.com/gary-quinn/kmp-ble/compare/v0.8.0...v0.8.1
[0.8.0]: https://github.com/gary-quinn/kmp-ble/compare/v0.7.1...v0.8.0
[0.7.1]: https://github.com/gary-quinn/kmp-ble/compare/v0.7.0...v0.7.1
[0.7.0]: https://github.com/gary-quinn/kmp-ble/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/gary-quinn/kmp-ble/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/gary-quinn/kmp-ble/compare/v0.4.3...v0.5.0
[0.4.3]: https://github.com/gary-quinn/kmp-ble/compare/v0.4.2...v0.4.3
[0.4.2]: https://github.com/gary-quinn/kmp-ble/compare/v0.4.1...v0.4.2
[0.4.1]: https://github.com/gary-quinn/kmp-ble/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/gary-quinn/kmp-ble/compare/v0.3.18...v0.4.0
[0.3.18]: https://github.com/gary-quinn/kmp-ble/compare/v0.3.17...v0.3.18
[0.3.17]: https://github.com/gary-quinn/kmp-ble/compare/v0.3.16...v0.3.17
[0.3.16]: https://github.com/gary-quinn/kmp-ble/compare/v0.3.15...v0.3.16
[0.3.15]: https://github.com/gary-quinn/kmp-ble/compare/v0.3.14...v0.3.15
[0.3.15]: https://github.com/gary-quinn/kmp-ble/compare/v0.3.14...v0.3.15
[0.3.13]: https://github.com/gary-quinn/kmp-ble/compare/v0.3.12...v0.3.13
[0.3.12]: https://github.com/gary-quinn/kmp-ble/compare/v0.3.11...v0.3.12
[0.3.11]: https://github.com/gary-quinn/kmp-ble/compare/v0.3.10...v0.3.11
[0.3.10]: https://github.com/gary-quinn/kmp-ble/compare/v0.3.9...v0.3.10
[0.3.9]: https://github.com/gary-quinn/kmp-ble/compare/v0.3.8...v0.3.9
[0.3.8]: https://github.com/gary-quinn/kmp-ble/compare/v0.3.7...v0.3.8
[0.3.7]: https://github.com/gary-quinn/kmp-ble/compare/v0.3.6...v0.3.7
[0.3.6]: https://github.com/gary-quinn/kmp-ble/compare/v0.3.5...v0.3.6
[0.3.5]: https://github.com/gary-quinn/kmp-ble/compare/v0.3.4...v0.3.5
[0.3.4]: https://github.com/gary-quinn/kmp-ble/compare/v0.3.3-alpha1...v0.3.4
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
