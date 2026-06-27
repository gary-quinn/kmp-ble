# Issue #527: Add stress tests for ObservationPersistence SharedPreferences writes
- Severity: medium
- Impact: medium
- Approach: Add stress tests for ObservationPersistence: (1) high-frequency write operations, (2) concurrent read/write, (3) persistence under pressure. Use Android instrumented tests with mock SharedPreferences. Verify no data loss or corruption under load.
- Files to touch: androidMain/test/ObservationPersistenceStressTest.kt (new), androidMain/persistence/ObservationPersistence.android.kt (read-only)
- Risks: Instrumented tests require Android environment. Must handle SharedPreferences lifecycle carefully. Must test under high load conditions.
