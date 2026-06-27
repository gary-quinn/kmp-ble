# kmp-ble TODO -- 83 issues (47 done, 3 in-progress, 33 open)
# Implementer: pick first unchecked item, mark [~] while working, [x] when merged.
# Reviewer: auto-approve + merge green PRs.
# CRITICAL: Only @gary-quinn releases. NEVER create tickets for version bumps, CHANGELOG, tags, release.

## Critical Bugs
- [ ] #CRIT: Architectural regression - broken interface extraction merged to main (93a00ea), reverted (209a5a4). Extracted interfaces had missing imports (StateFlow, Flow), no implementations, no cross-platform coverage. Public API surface introduced without compilation verification. Fix: add architecture review gate for public API additions, enforce compileKotlinJvm in PR checklist, redesign #441 extraction approach with proper imports and implementations. [bug, priority: critical, architectural-regression] (plan: process - no code implementation needed)
- [ ] #528: refactor(android): migrate remaining @Volatile fields in AndroidGattServerState to atomicfu [bug, priority: critical] (plan: architecture-plans/issue-528.md)
- [ ] #514: bug(concurrency): migrate @Volatile in ObservationPersistence.android.kt:115 to atomicfu [bug, priority: critical] (plan: architecture-plans/issue-514.md)

## Bugs
- [x] #418: fix(style): FQN Peripheral RESOLVED, but unresolved KDoc [createSync] remains on PeriodicAdvertisingSync.kt:37,39 (post-merge audit of #413) (PR #421) [bug, documentation]
- [x] #420: fix(style): FQN com.atruedev.kmpble.server.PeriodicAdvertisingParameters in PeriodicReport.kt:11 KDoc (post-merge audit of #413) (PR #422) [bug, documentation]
- [x] #417: fix(isochronous): wire IsochronousStreamConfig parameters (bufferCapacity, closeChannelOnClose, secure) into stream behavior -- currently stored but unused [bug] (PR #419)
- [x] #384: fix(concurrency): AdvertisingDataBuilder counter++ is a data race in commonMain -- use atomicfu or document thread-safety (PR #389) [bug]
- [x] #385: fix(style): use imported Duration.ZERO/INFINITE instead of FQN kotlin.time.Duration.ZERO/INFINITE in OperationTimeoutsTest (PR #390) [bug, style]
- [x] #391: docs: fix @throws GattException in connectAndDiscover KDoc -- class does not exist [bug, documentation]
||||- [~] #428: test: add FakePeripheral integration tests for dataLengthParameters property [bug, testing] (plan: architecture-plans/issue-428.md)
- [ ] #530: refactor(android): migrate AndroidGattServerState @Volatile fields to atomicfu [bug, enhancement, severity: medium, impact: medium] (plan: architecture-plans/issue-530.md)

## Enhancements -- High Priority
- [x] #302: feat(dx): add configurable operation timeouts with sensible defaults (PR #383)
- [x] #343: feat(advertising): add AdvertisingData builder DSL for scan record construction (PR #382)
- [x] #357: feat(dx): add connectAndDiscover convenience combining connection and service discovery (PR #388)
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
- [ ] #534: refactor(common): extract ConnectionState state machine to dedicated module [enhancement] (plan: architecture-plans/issue-534.md)
- [ ] #532: feat(parity): add iOS scan response data API to match Android Scanner [enhancement] (plan: architecture-plans/issue-532.md)
- [ ] #531: test(android): add AndroidGattBridge concurrency stress tests [enhancement, testing] (plan: architecture-plans/issue-531.md)
- [ ] #527: test(concurrency): add stress tests for ObservationPersistence SharedPreferences writes [enhancement, testing] (plan: architecture-plans/issue-527.md)
- [ ] #526: feat(parity): implement iOS scan response data reading to match Android functionality [enhancement] (plan: architecture-plans/issue-526.md)
- [ ] #525: refactor(common): extract ConnectionState machine from Peripheral.kt [enhancement] (plan: architecture-plans/issue-525.md)
- [ ] #524: test(android): add AndroidGattBridge callback channel concurrency tests [enhancement, testing] (plan: architecture-plans/issue-524.md)
- [ ] #523: feat(diff): add PHY layer metrics collection for connection quality monitoring [enhancement] (plan: architecture-plans/issue-523.md)
- [ ] #522: feat(dx): add Connection.reconnect() convenience for single-tap reconnection to last known peripheral [enhancement] (plan: architecture-plans/issue-522.md)
- [ ] #521: refactor(android): decompose AndroidGattBridge (253 lines) isolating @Volatile callback channel [enhancement] (plan: architecture-plans/issue-521.md)
- [ ] #520: feat(diff): add GATT notification batching for high-frequency sensor data [enhancement] (plan: architecture-plans/issue-520.md)
- [ ] #518: feat(parity): add PHY read/write API for Bluetooth 5.x physical layer control [enhancement] (plan: architecture-plans/issue-518.md)
- [ ] #516: refactor(common): decompose IsochronousStream.kt (282 lines) into focused modules [enhancement] (plan: architecture-plans/issue-516.md)
- [ ] #515: feat(diff): add connection quality history and RSSI trend analysis for proactive reconnection [enhancement] (plan: architecture-plans/issue-515.md)
- [ ] #519: test(persistence): add ObservationPersistence SharedPreferences Android roundtrip tests [enhancement, testing] (plan: architecture-plans/issue-519.md)
- [ ] #517: test(observe): add observation persistence cancellation and recovery tests [enhancement, testing] (plan: architecture-plans/issue-517.md)

## Testing
- [x] #339: test(observation): add ObservationPersistence cross-platform roundtrip tests (PR #412) [enhancement, testing]
- [x] #328: test(connection): add edge-case tests for LE Connection Parameter Update negotiation (PR #433) [enhancement, testing]
- [x] #319: test(parity): add cross-platform GATT server integration conformance tests (PR #434) [enhancement, testing]
- [x] #312: test(benchmark): add iOS and Android benchmark runners for kmp-ble-benchmark (PR #435) [enhancement, testing]
- [x] #311: test(ios): add IosPeripheral GATT event handling integration tests (PR #436) [enhancement, testing]
- [x] #305: test(monitoring): add integration tests for PowerMonitor and LePowerController edge cases (PR #437) [enhancement, testing]
- [x] #358: test(gatt): add GATT write-type conformance tests for Write Request vs Write Command (PR #438) [enhancement, testing]
- [x] #369: test(advertiser): add cross-platform Advertiser conformance tests (PR #439) [enhancement, testing]
- [x] #378: test(l2cap): add L2CAP channel edge-case tests for disconnection and backpressure [enhancement, testing]
- [x] #444: test(dfu): add DFU Transport API integration and conformance tests [enhancement, testing]
- [x] #440: test(direction): add AoA/AoD Direction Finding integration and conformance tests [enhancement, testing]
||||- [~] #303: test(peripheral): add AndroidPeripheral GATT event handling integration tests [enhancement, testing] (plan: architecture-plans/issue-303.md)
- [ ] #335: test(scanner): add Android Scanner integration tests for scan modes, filters, and edge cases [enhancement, testing] (plan: architecture-plans/issue-335.md)
- [ ] #447: test(connection): add LE Connection Subrating integration and conformance tests [enhancement, testing] (plan: architecture-plans/issue-447.md)

## Refactoring
- [ ] #367: refactor(ios): arrest IosPeripheral regrowth (286->300) back below 250 lines [enhancement] (plan: architecture-plans/issue-367.md)
- [ ] #441: refactor(common): decompose Peripheral.kt (373 lines) into focused handler modules [enhancement] (plan: architecture-plans/issue-441.md)

## Competitive Differentiation
- [ ] #446: feat(diff): add RSSI streaming via Connection.rssiFlow for connection quality monitoring [enhancement] (plan: architecture-plans/issue-446.md)
- [ ] #451: feat(diff): add BLE Channel Assessment for interference detection and adaptive PHY selection [enhancement] (plan: architecture-plans/issue-451.md)
- [ ] #442: feat(diff): add LE Coded PHY awareness to scanner results for long-range discovery [enhancement] (plan: architecture-plans/issue-442.md)

## DX Improvements
- [ ] #443: feat(dx): add scanAndConnect convenience combining Scanner and Peripheral lifecycle [enhancement] (plan: architecture-plans/issue-443.md)
- [ ] #448: feat(dx): add ScanRegion and proximity-based zone entry/exit detection [enhancement] (plan: architecture-plans/issue-448.md)
- [ ] #452: feat(dx): add ConnectionFailure class for categorized BLE connection error handling [enhancement] (plan: architecture-plans/issue-452.md)

## Cross-Platform Parity
- [ ] #450: feat(parity): add prepareWrite/executeWrite reliable long write API for large characteristic values [enhancement] (plan: architecture-plans/issue-450.md)
- [ ] #453: feat(parity): add L2CAP channel error recovery and graceful close [enhancement] (plan: architecture-plans/issue-453.md)

## Documentation
- [ ] #414: docs(quirks): fix KDoc link registerIosProvider -> IosQuirkProviders.register [docs] (plan: architecture-plans/issue-414.md)

## Scan Results (2026-06-27T19:30:00Z)
| Metric | Value |
|--------|-------|
| Latest commit | c98613b fix(concurrency): resolve android compile errors from atomicfu migration |
| Open issues | 22 |
| Open PRs | 5 |
| PR #543 | GREEN (all checks passing) -- merge-ready |
| PRs #538,#540,#541,#542 | android CI FAILING (environment issue on Linux ARM64, per directive) |
| Largest production source | Peripheral.kt: 373 lines |
| androidMain files | 47 |
| iosMain files | 51 |
| @Volatile in code | 0 (comments only mention deprecated usage) |
| GlobalScope in code | 0 |
| synchronized/mutex/lock | 0 |
| expect/actual parity | 15 public + 4 internal expect declarations all have matching actuals on both platforms |
| Concurrency anti-patterns | PASS -- no banned primitives in production code |
