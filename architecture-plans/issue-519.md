# Issue #519: Add ObservationPersistence SharedPreferences Android roundtrip tests
- Severity: medium
- Impact: medium
- Approach: Add Android-specific tests for ObservationPersistence: (1) write CCC state to SharedPreferences, (2) read back after process restart, (3) verify state persistence across app lifecycle. Use Android instrumented tests with mock SharedPreferences. Test edge cases: empty state, corrupted data.
- Files to touch: androidMain/test/ObservationPersistenceAndroidTest.kt (new), androidMain/persistence/ObservationPersistence.android.kt (read-only)
- Risks: Instrumented tests require Android environment. Must handle SharedPreferences lifecycle carefully. Must test both success and failure paths.
