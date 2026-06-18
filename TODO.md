# kmp-ble Product Backlog
> Last review: 2026-06-18 16:15 UTC (cron)
> Commit: main (9fbc4c6 — refactor(ios): decompose IosPeripheral 713->390 loc via extension functions)
> Directive: Resolve #246 (CxE swallow in AndroidPairingRequestHandler), complete IosGattServer decomposition (WIP), L2CAP parity audit, architecture docs
> Scan: clean — no GlobalScope/Mutex/ReentrantLock, no qualified names in code body, @Volatile documented, runBlocking correctly scoped, all catch(Exception) properly rethrow CancellationException
> commonMain files: all under 300 lines (max 285). Module parity: androidMain 43 files/4404 loc vs iosMain 36 files/4222 loc — IosGattServer (684 loc) is the last God file
> Untracked WIP: IosGattServer decomposition — IosGattServerExtensions.kt (113 loc) + IosGattServerHandlers.kt (216 loc) uncommitted
> Open PRs: 0
> Open issues: #246

## Critical (blocking adoption)
- [x] [KMP-312] iOS: CBCentralManager created without restore identifier but delegate implements willRestoreState
  - Fixed in PR #222 (87f4019). Merged: 2026-06-17

- [x] [KMP-313] Watchdog: Qualified names in code body violate Kotlin conventions (CentralDelegateImpl.kt:32, CentralDelegateState.kt:104)
  - Fixed in commit 29f7489

- [x] [#241] **CancellationException swallowed in async{} inside map{}.awaitAll()** — AndroidGattServer.kt notify() path (PR #242)
  - File: `src/androidMain/kotlin/com/atruedev/kmpble/server/AndroidGattServer.kt`, lines 155-159
  - Impact: Prevents clean structured cancellation — coroutine doesn't propagate cancellation to parent
  - Fix: Add `if (e is CancellationException) throw e` before `logEvent(...)` in the async catch block
  - Filed: 2026-06-18 14:25 UTC

- [x] [#246] **CancellationException swallowed in AndroidPairingRequestHandler scope.launch** (new)
  - Fixed in PR #247 (63e383c)
  - Impact: Pairing handler coroutine continues running after cancellation — cancellation signal lost
  - Fix: Add `if (e is CancellationException) throw e` before `logEvent(...)` in the catch block
  - Detected by: Post-merge architectural audit (cron)
  - Filed: 2026-06-18 15:54 UTC

## High (competitive advantage)
- [x] [#234] arch: benchmark package adds 8 public dev-tooling types to commonMain production API
  - Merged in PR #239 (1fd4f95): extracted to separate kmp-ble-benchmark module

- [x] [KMP-321] Competitive: Connection quality observables
  - Merged in PR #235 (1d2c4e3): ConnectionQualityMonitor + ConnectionQuality data class

- [x] [KMP-320] DX: Add recoveryHint to BleError types (PR #233)
  - Merged: d526085

- [x] [KMP-212] Bonded peripheral retrieval before scanning on iOS (PR #213)
  - Merged in 4d14fc3

- [x] runBlocking WHY comments added to AndroidAdvertiser.kt, AndroidExtendedAdvertiser.kt (PR #224)

- [x] [KMP-316] DOCS: v0.8.4 release notes / changelog (PR #226)

- [x] [KMP-317] SAMPLES: end-to-end BLE sample app (PR #227)

- [x] [KMP-318] BENCHMARKS: cross-platform benchmark comparison (PR #228)

- [x] **AndroidGattServer decomposed**: 1003->286 loc (facade) + 4 support files. PR #240.
  - AndroidGattServerState.kt (204), AndroidGattServerCallback.kt (444), AndroidGattServerSetup.kt (183), AndroidGattServerExtensions.kt (27)

- [x] [#241-P2] **AndroidGattServerCallback God File (444 lines, target <300)**: Decomposed into focused handlers (PR #243)
  - AndroidGattServerCallback.kt: 126, AndroidGattServerConnectionHandlers.kt: 46, AndroidGattServerReadHandlers.kt: 161, AndroidGattServerWriteHandlers.kt: 227

- [x] **AndroidPeripheral.kt (876 loc) decomposition**: Apply Class Decomposition Pattern (Approach B: Extension Functions)
  - Merged in PR #244 (c9081bc): facade 334 loc + 4 extension files (Connection, GattHandler, Internal, L2cap)

- [x] **IosPeripheral.kt (713 loc) decomposition**: Apply Class Decomposition Pattern.
  - Merged in PR #245 (9fbc4c6): facade 390 loc + 5 extension files (BridgeHandlers, Connection, Discovery, Internal, L2cap)

- [~] **IosGattServer.kt (684 loc) decomposition**: Apply Class Decomposition Pattern.
  - PR #248 (auto-merge): facade 390 loc + 2 extension files (Extensions 113, Handlers 215)
  - Approach B (Extension Functions) — same pattern as IosPeripheral (#245), AndroidPeripheral (#244)

- [~] **Cross-platform L2CAP parity audit**: Compare iOS vs Android L2CAP feature surface (channels, listeners, PSM management). Identify gaps and add conformance tests.
  - Audit completed 2026-06-18: feature parity confirmed — both platforms support channels + listeners
  - Gaps filed: #249 (conformance tests), #250 (architecture docs)
  - Platform loc: commonMain 290 + iOS 433 + Android 354 + JVM stubs
  - Next: add L2CAP conformance scenarios to BleConformanceTest

- [ ] **Architecture documentation**: Add ARCHITECTURE.md with module map, component interaction diagrams, data flow for scan->connect->discover->operate lifecycle.

## Medium (quality / polish)
- [x] [#238] fix: em-dash typography violation in BleConformanceTest.kt
  - Fixed in commit e90e3da. Closed on GitHub.

- [x] [KMP-322] Parity: Cross-platform conformance test harness
  - Merged in PR #236 (80f75fd): BleConformanceTest abstract class + JvmBleConformanceTest runner

- [x] [#230] arch: extract duplicated try-catch pattern in BleBenchmark.kt
  - Merged in PR #237 (7fd2005): benchmarkTimed helper — 31 insertions, 51 deletions

- [x] [KMP-319] RELEASE: v0.8.5 prep
  - Merged in PR #229 (adbed00)

- [x] [KMP-315] Add automated benchmark utilities (PR #225)

- [x] [Dependabot] Bump gradle/actions to 6.2.0 (PR #215)

- [x] [CI] Fix changelog baseline (PR #232)

## Low (nice-to-have)
- [x] Docs v0.8.2 release (PR #214)

- [x] **Close stale issue #238**: Closed on GitHub.

## Tech Debt (non-user-facing)
- [x] @Volatile for observationsSnapshot in ObservationRegistry — kept for non-suspend callback context, documented

- [x] FakePeripheral.getObservationManagerForTest() exposure for debugging
  - Current state: Added in PR #211 fix; implemented in FakePeripheral.kt:278

- [x] [KMP-314] Qualified kotlinx.coroutines names in platform code body
  - Fixed in PR #223 (0055c50) + follow-up (44a68d0)

