# kmp-ble TODO -- 76 issues (48 done, 1 in-progress, 27 open)
# Implementer: pick first unchecked item, mark [~] while working, [x] when merged.
# Reviewer: auto-approve + merge green PRs.

## Critical Bugs
- [x] #424: CRITICAL -- LE Connection Subrating commits pushed directly to main bypassing PR #423. PR #423 is still OPEN (never merged). Revert both commits from main, force-push main back to 8ba9505, merge PR #423 through proper workflow. [bug, priority: critical, process-violation]
- [x] #396: CRITICAL -- #293 CCC persistence pushed directly to main (commit da3bb55), bypassing PR gates. Hand-rolled JsonArrayEncoder in androidMain (~170 lines) has zero Android-path test coverage. serializeBackpressure/deserializeBackpressure duplicated across androidMain and iosMain. Stale KDoc in ObservationPersistence.kt says "On Android, this is a no-op". Fix: revert, file proper PR, extract shared serialization to commonMain, add Android SharedPreferences roundtrip tests, update KDoc. [bug, priority: critical, process-violation]
- [x] #397: CRITICAL -- PR #396 merged but autopilot directive items remain unresolved: (1) JsonArrayEncoder (~114 lines hand-rolled JSON parser, androidMain) has ZERO Android-path test coverage -- jvmTest only tests JVM in-memory impl, SharedPreferences code path untested. (2) serializeBackpressure/deserializeBackpressure duplicated identically in androidMain:110-127 AND iosMain:142-159 -- extract to commonMain. (3) Stale KDoc at ObservationPersistence.kt:19 says "On Android, this is a no-op" -- Android now uses SharedPreferences. Fix: extract serialization to commonMain, add androidHostTest for SharedPreferences+JsonArrayEncoder roundtrip, update KDoc. [bug, priority: critical, test-gap]
- [x] #366: ConnectionOptions.timeout dead code after per-operation timeouts merge (PR #380) [bug, priority: critical]
- [x] #449: fix(concurrency): complete @Volatile to atomicfu migration -- #342 follow-up (PR #454) [bug, priority: critical]
- [x] #342: fix(concurrency): replace @Volatile with kotlinx-atomicfu across all platform sources [bug, priority: critical]
- [x] #341: GattConformanceTest bypasses buildPeripheral() factory for notification test [bug]
- [x] #261: BeaconScanner.close() swallows CancellationException in cleanup (PR #381)

## Bugs
- [x] #418: fix(style): FQN Peripheral RESOLVED, but unresolved KDoc [createSync] remains on PeriodicAdvertisingSync.kt:37,39 (post-merge audit of #413) (PR #421) [bug, documentation]
- [x] #420: fix(style): FQN com.atruedev.kmpble.server.PeriodicAdvertisingParameters in PeriodicReport.kt:11 KDoc (post-merge audit of #413) (PR #422) [bug, documentation]
- [x] #417: fix(isochronous): wire IsochronousStreamConfig parameters (bufferCapacity, closeChannelOnClose, secure) into stream behavior -- currently stored but unused [bug] (PR #419)
- [x] #384: fix(concurrency): AdvertisingDataBuilder counter++ is a data race in commonMain -- use atomicfu or document thread-safety (PR #389) [bug]
- [x] #385: fix(style): use imported Duration.ZERO/INFINITE instead of FQN kotlin.time.Duration.ZERO/INFINITE in OperationTimeoutsTest (PR #390) [bug, style]
- [x] #391: docs: fix @throws GattException in connectAndDiscover KDoc -- class does not exist [bug, documentation]
- [x] #428: test: add FakePeripheral integration tests for dataLengthParameters property [bug, testing] (PR #472)

## Enhancements -- High Priority
- [x] #302: feat(dx): add configurable operation timeouts with sensible defaults (PR #383)
- [x] #343: feat(advertising): add AdvertisingData builder DSL for scan record construction (PR #382)
- [x] #357: feat(dx): add connectAndDiscover convenience combining connection and service discovery [enhancement] (PR #388)
- [x] #289: feat(diff): support simultaneous central and peripheral roles (PR #297)
- [x] #329: feat(dx): add configurable GATT operation retry policies with exponential backoff (PR #394) [enhancement]
- [x] #301: feat(gatt): add ATT MTU negotiation API for GATT throughput optimization (PR #395) [enhancement]
- [x] #293: feat(cache): persist CCC descriptor states for seamless GATT reconnection (PR #396) [enhancement]
- [x] #334: feat(parity): add client-side BondManager interface with iOS implementation (PR #402) [enhancement]
- [x] #333: feat(dx): add ConnectionParamPreset enums for common BLE connection configurations [enhancement]
- [x] #372: feat(dx): add ConnectionOptions validation warnings for common misconfigurations (PR #399) [enhancement]
- [x] #364: feat(dx): add typed BLE error hierarchy with platform-native error mapping (PR #400) [enhancement]
- [x] #317: feat(dx): add structured logging and tracing hooks for BLE operation lifecycle (PR #401) [enhancement]
- [x] #379: feat(dx): add structured BLE telemetry and logging interface -- superseded by #317 (PR #401) [enhancement]
- [x] #304: feat(dx): add Bluetooth 5.x feature detection and capability query API (PR #403) [documentation, enhancement]
- [x] #259: feat(parity): add iOS device quirk detection matching Android QuirkRegistry (PR #404) [enhancement, ios, priority]
- [x] #377: feat(parity): add iOS device quirks system matching Android BleQuirks (PR #404) [enhancement]

## BLE Features (Bluetooth 5.x)
- [x] #276: feat(le-audio): add LE Audio Isochronous Channels API (PR #393) [enhancement, priority]
- [x] #363: feat(le-audio): add LE Audio Isochronous Channels streaming API for Bluetooth 5.2+ (PR #416) [enhancement]
- [x] #338: feat(parity): add Periodic Advertising Sync Transfer (PAST) support (Bluetooth 5.1+) (PR #413) [enhancement]
- [x] #368: feat(parity): add BLE Direction Finding (AoA/AoD) API for Bluetooth 5.1+ (PR #427) [enhancement]
- [x] #318: feat(connection): add LE Connection Subrating support (Bluetooth 5.3) [enhancement]
- [x] #359: feat(parity): add LE Data Length Extension API for BLE 4.2+ throughput optimization (PR #426) [enhancement]
- [x] #332: feat(dfu): design and expose public OTA DFU transport API (PR #430) [enhancement]

## Testing
- [x] #339: test(observation): add ObservationPersistence cross-platform roundtrip tests (PR #412) [enhancement, testing]
- [x] #335: test(scanner): add Android Scanner integration tests for scan modes, filters, and edge cases [enhancement, testing]
- [x] #328: test(connection): add edge-case tests for LE Connection Parameter Update negotiation (PR #433) [enhancement, testing]
- [x] #319: test(parity): add cross-platform GATT server integration conformance tests (PR #434) [enhancement, testing]
- [x] #312: test(benchmark): add iOS and Android benchmark runners for kmp-ble-benchmark (PR #435) [enhancement, testing]
- [x] #311: test(ios): add IosPeripheral GATT event handling integration tests (PR #436) [enhancement, testing]
- [x] #305: test(monitoring): add integration tests for PowerMonitor and LePowerController edge cases (PR #437) [enhancement, testing]
- [~] #303: test(peripheral): add AndroidPeripheral GATT event handling integration tests [enhancement, testing]
- [x] #358: test(gatt): add GATT write-type conformance tests for Write Request vs Write Command (PR #438) [enhancement, testing]
- [x] #369: test(advertiser): add cross-platform Advertiser conformance tests (PR #439) [enhancement, testing]
- [x] #378: test(l2cap): add L2CAP channel edge-case tests for disconnection and backpressure [enhancement, testing]
- [x] #444: test(dfu): add DFU Transport API integration and conformance tests [enhancement, testing]
- [x] #440: test(direction): add AoA/AoD Direction Finding integration and conformance tests [enhancement, testing]
- [~] #447: test(connection): add LE Connection Subrating integration and conformance tests [enhancement, testing]
- [ ] #488: test(scanner): add Android-specific Scanner integration tests for BLE scan mode behavior [enhancement, testing]

## Refactoring / Architecture Debt
- [ ] #367: refactor(ios): arrest IosPeripheral regrowth (286->300) back below 250 lines [enhancement]
- [ ] #370: refactor(ios): decompose IosL2capListener (236 lines) into focused handler modules [enhancement]
- [ ] #376: refactor(android): decompose AndroidExtendedAdvertiser (260 lines) into focused handler modules [enhancement]
- [ ] #441: refactor(common): decompose Peripheral.kt (373 lines) into focused handler modules [enhancement]
- [ ] #459: refactor(ios): decompose IosPeripheral.kt (326 lines) - #367 follow-up [enhancement]
- [ ] #469: refactor(server): decompose AndroidGattServer (287 lines) into focused handler modules [enhancement]
- [ ] #483: refactor(android): decompose AndroidPeripheralConnection (253 lines) - single responsibility [enhancement]
- [ ] #487: refactor(android): decompose AndroidGattBridge (253 lines) - GATT callback isolation [enhancement]
- [ ] #490: refactor(common): decompose IsochronousStream.kt (282 lines) into focused modules [enhancement]

## Competitive Differentiation
- [ ] #442: feat(diff): add LE Coded PHY awareness to scanner results for long-range discovery [enhancement]
- [ ] #446: feat(diff): add RSSI streaming via Connection.rssiFlow for connection quality monitoring [enhancement]
- [ ] #451: feat(diff): add BLE Channel Assessment for interference detection and adaptive PHY selection [enhancement]
- [ ] #456: feat(diff): add Bluetooth LE encrypted connection support for L2CAP security [enhancement]
- [ ] #462: feat(diff): add Periodic Advertising with Response (PAR) API for BLE 6.0 [enhancement]
- [ ] #474: feat(diff): add Connection.qualityFlow for RSSI-trend-based link health indicator [enhancement]
- [ ] #481: feat(diff): add Bluetooth Channel Sounding API for BLE 6.0 distance/anti-spoofing [enhancement]
- [ ] #491: feat(diff): add LE Power Control (LEPC) API for Bluetooth 5.4 adaptive transmit power [enhancement]

## DX Improvements
- [ ] #443: feat(dx): add scanAndConnect convenience combining Scanner and Peripheral lifecycle [enhancement]
- [ ] #448: feat(dx): add ScanRegion and proximity-based zone entry/exit detection [enhancement]
- [ ] #452: feat(dx): add ConnectionFailure class for categorized BLE connection error handling [enhancement]
- [ ] #457: feat(dx): add batch peripheral operations for multi-device workflows [enhancement]
- [ ] #464: feat(dx): add multi-device scan filtering and batch matching [enhancement]
- [ ] #467: feat(dx): add Connection.stateFlow for reactive lifecycle tracking [enhancement]
- [ ] #468: feat(dx): add Scanner.scanWithRetry with automatic backoff for unreliable environments [enhancement]
- [ ] #473: feat(dx): add Peripheral.batchRead() for parallel characteristic reading [enhancement]
- [ ] #477: feat(dx): add Scanner.scanFor() convenience returning first matching device [enhancement]
- [ ] #486: feat(dx): add Peripheral.withConnection {} scope function for automatic lifecycle management [enhancement]
- [ ] #489: feat(dx): add Scanner.ScanConfig presets for common discovery patterns [enhancement]
- [ ] #493: feat(dx): add Scanner.scanBatch() returning top N results sorted by signal strength [enhancement]

## Cross-Platform Parity
- [ ] #450: feat(parity): add prepareWrite/executeWrite reliable long write API for large characteristic values [enhancement]
- [ ] #453: feat(parity): add L2CAP channel error recovery and graceful close [enhancement]
- [ ] #460: feat(parity): add iOS BondManager implementation matching Android capability [enhancement]
- [ ] #471: feat(parity): add prepareWrite support to IosPeripheral matching Android implementation [enhancement]
- [ ] #476: feat(parity): add connection encryption level query to common API [enhancement]

## Documentation
- [ ] #414: docs(quirks): fix KDoc link registerIosProvider -> IosQuirkProviders.register [docs]

## Testing (Open)
- [ ] #455: test(bonding): add BondManager cross-platform integration tests [enhancement, testing]
- [ ] #458: test(dfu): add DFU Transport error handling and interrupt recovery tests [enhancement, testing]
- [ ] #463: test(concurrency): add tests for remaining @Volatile fields in androidMain [enhancement, testing]
- [ ] #470: test(fake): add FakeGattResponder edge-case tests for concurrent client scenarios [enhancement, testing]
- [ ] #475: test(reliability): add GATT operation tests under simulated packet loss and high latency [enhancement, testing]
- [ ] #482: test(ble-5): add BLE 5.x feature test coverage matrix for platform capability detection [enhancement, testing]
- [ ] #492: test(isochronous): add LE Audio streaming integration and conformance tests [enhancement, testing]

## Scan Results (2026-06-24)
| Metric | Value |
|--------|-------|
| Latest commit | 13bfb5d fix(test): rename AndroidScannerFiltersTest to FakeScannerFiltersTest (#485) |
| Open issues | 48 |
| Open PRs | 0 |
| Stalled PRs | 0 |
| Largest source file | Peripheral.kt: 373 lines |
| Largest platform file | IosPeripheral.kt: 326 lines |
| androidMain total lines | ~4,959 across 47 files |
| iosMain total lines | ~4,495 across 51 files |
| commonMain total lines | ~10,701 |
| test total lines | ~17,822 |
| @Volatile remaining | 8 fields (all androidMain: ObservationPersistence x1, AndroidGattServerState x2, AndroidL2capListener x2, AndroidPeripheral x1, AndroidGattBridge x2) |
| GlobalScope | 0 |
| .lock() | 0 |
| Mutex() | 0 |
| synchronized | 0 (commentary only in AndroidExtendedAdvertiser) |
| ReentrantLock | 0 |
| Unstaged files | TODO.md (modified), AndroidPeripheralIntegrationTest.kt (added), .claude/ (untracked) |
| New tickets this run | #490 (IsochronousStream refactor), #491 (LE Power Control), #492 (isochronous tests), #493 (scanBatch DX) |
