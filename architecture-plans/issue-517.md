# Issue #517: Add observation persistence cancellation and recovery tests
- Severity: medium
- Impact: medium
- Approach: Add tests for observation persistence: (1) cancellation during write, (2) recovery after failure, (3) state consistency. Use Android instrumented tests with mock SharedPreferences. Test edge cases: partial writes, corruption.
- Files to touch: androidMain/test/ObservationPersistenceCancellationTest.kt (new), androidMain/persistence/ObservationPersistence.android.kt (read-only)
- Risks: Instrumented tests require Android environment. Must handle SharedPreferences lifecycle carefully. Must test cancellation scenarios.
