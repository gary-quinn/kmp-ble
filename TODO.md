# kmp-ble TODO -- 44 issues from backlog
# Implementer: pick first unchecked item, mark [~] while working, [x] when merged.
# Reviewer: auto-approve + merge green PRs.

## Critical Bugs
- [x] #424: CRITICAL -- LE Connection Subrating commits 2d952ac/7b7e4b5 pushed directly to main bypassing PR #423. PR #423 is still OPEN (never merged). Revert both commits from main, force-push main back to 8ba9505, merge PR #423 through proper workflow. [bug, priority: critical, process-violation]
- [x] #396: CRITICAL -- #293 CCC persistence pushed directly to main (commit da3bb55), bypassing PR gates. Hand-rolled JsonArrayEncoder in androidMain (~170 lines) has zero Android-path test coverage. serializeBackpressure/deserializeBackpressure duplicated across androidMain and iosMain. Stale KDoc in ObservationPersistence.kt says "On Android, this is a no-op". Fix: revert, file proper PR, extract shared serialization to commonMain, add Android SharedPreferences roundtrip tests, update KDoc. [bug, priority: critical, process-violation]
- [x] #397: CRITICAL -- PR #396 merged but autopilot directive items remain unresolved: (1) JsonArrayEncoder (~114 lines hand-rolled JSON parser, androidMain) has ZERO Android-path test coverage -- jvmTest only tests JVM in-memory impl, SharedPreferences code path untested. (2) serializeBackpressure/deserializeBackpressure duplicated identically in androidMain:110-127 AND iosMain:142-159 -- extract to commonMain. (3) Stale KDoc at ObservationPersistence.kt:19 says "On Android, this is a no-op" -- Android now uses SharedPreferences. Fix: extract serialization to commonMain, add androidHostTest for SharedPreferences+JsonArrayEncoder roundtrip, update KDoc. [bug, priority: critical, test-gap]
- [x] #366: ConnectionOptions.timeout dead code after per-operation timeouts merge (PR #380) [bug, priority: critical]
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
- [ ] #332: feat(dfu): design and expose public OTA DFU transport API [enhancement]

## Testing
- [x] #339: test(observation): add ObservationPersistence cross-platform roundtrip tests (PR #412) [enhancement, testing]
- [ ] #335: test(scanner): add Android Scanner integration tests for scan modes, filters, and edge cases [enhancement, testing]
- [ ] #328: test(connection): add edge-case tests for LE Connection Parameter Update negotiation [enhancement, testing]
- [ ] #319: test(parity): add cross-platform GATT server integration conformance tests [enhancement, testing]
- [ ] #312: test(benchmark): add iOS and Android benchmark runners for kmp-ble-benchmark [enhancement, testing]
- [ ] #311: test(ios): add IosPeripheral GATT event handling integration tests [enhancement, testing]
- [ ] #305: test(monitoring): add integration tests for PowerMonitor and LePowerController edge cases [enhancement, testing]
- [ ] #303: test(peripheral): add AndroidPeripheral GATT event handling integration tests [enhancement, testing]
- [ ] #358: test(gatt): add GATT write-type conformance tests for Write Request vs Write Command [enhancement, testing]
- [~] #369: test(advertiser): add cross-platform Advertiser conformance tests [enhancement, testing]
- [ ] #378: test(l2cap): add L2CAP channel edge-case tests for disconnection and backpressure [enhancement, testing]

## Refactoring
- [ ] #367: refactor(ios): arrest IosPeripheral regrowth (286->300) back below 250 lines [enhancement]
- [ ] #365: refactor(server): decompose AndroidGattServer (287 lines) into focused handler modules [enhancement]
- [ ] #376: refactor(android): decompose AndroidExtendedAdvertiser (260 lines) into focused handler modules [enhancement]
- [ ] #370: refactor(ios): decompose IosL2capListener (236 lines) into focused handler modules [enhancement]
