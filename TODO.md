# kmp-ble TODO -- 84 issues (47 done, 3 in-progress, 34 open)
# Implementer: pick first unchecked item, mark [~] while working, [x] when merged.
# Reviewer: auto-approve + merge green PRs.
# CRITICAL: Only @gary-quinn releases. NEVER create tickets for version bumps, CHANGELOG, tags, release.

## Critical Bugs
- [ ] #CRIT: Architectural regression - broken interface extraction merged to main (93a00ea), reverted (209a5a4). Extracted interfaces had missing imports (StateFlow, Flow), no implementations, no cross-platform coverage. Public API surface introduced without compilation verification. Fix: add architecture review gate for public API additions, enforce compileKotlinJvm in PR checklist, redesign #441 extraction approach with proper imports and implementations. [bug, priority: critical, architectural-regression] (plan: process - no code implementation needed)
- [ ] #528: refactor(android): migrate remaining @Volatile fields in AndroidGattServerState to atomicfu [bug, priority: critical] (plan: architecture-plans/issue-528.md)
- [ ] #514: bug(concurrency): migrate @Volatile in ObservationPersistence.android.kt:115 to atomicfu [bug, priority: critical] (plan: architecture-plans/issue-514.md)
- [ ] #547: fix(scanner): ScanMode enum missing Opportunistic, test references non-existent entry [bug, priority: critical] (plan: none)

## Bugs
- [x] #418: fix(style): FQN Peripheral RESOLVED, but unresolved KDoc [createSync] remains on PeriodicAdvertisingSync.kt:37,39 (post-merge audit of #413) (PR #421) [bug, documentation]
- [x] #420: fix(style): FQN com.atruedev.kmpble.server.PeriodicAdvertisingParameters in PeriodicReport.kt:11 KDoc (post-merge audit of #413) (PR #422) [bug, documentation]
- [x] #417: fix(isochronous): wire IsochronousStreamConfig parameters (bufferCapacity, closeChannelOnClose, secure) into stream behavior -- currently stored but unused [bug] (PR #419)
- [x] #384: fix(concurrency): AdvertisingDataBuilder counter++ is a data race in commonMain -- use atomicfu or document thread-safety (PR #389) [bug]
- [x] #385: fix(style): use imported Duration.ZERO/INFINITE instead of FQN kotlin.time.Duration.ZERO/INFINITE in OperationTimeoutsTest (PR #390) [bug, style]
- [x] #391: docs: fix @throws GattException in connectAndDiscover KDoc -- class does not exist [bug, documentation]
|||||- [~] #428: test: add FakePeripheral integration tests for dataLengthParameters property [bug, testing] (plan: architecture-plans/issue-428.md)
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

## Scan Results (2026-06-27T22:45:00Z)
| Metric | Value |
|--------|-------|
| Latest commit | 81ff2c6 fix(scanner): remove orphaned Opportunistic test, align with ScanMode enum (3 values) |
| Open issues | 34 |
| Open PRs | 0 |
| Largest production source | AndroidGattServer.kt: 287 lines |
| androidMain files | 20 |
| iosMain files | 14 |
| @Volatile in code | 8 (production: AndroidPeripheral.kt:95, AndroidGattBridge.kt:27,29, ObservationPersistence.android.kt:115, AndroidGattServerState.kt:176,180, AndroidL2capListener.kt:32,48) |
| GlobalScope in code | 0 |
| synchronized/mutex/lock | 0 |
| expect/actual parity | 15 public + 4 internal pairs verified |
