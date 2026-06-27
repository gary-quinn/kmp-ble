# Issue #342: Follow-up for @Volatile to atomicfu migration
- Severity: critical
- Impact: high
- Approach: Track and verify completion of @Volatile to atomicfu migration. Ensure all remaining fields (9 fields per scan results) are migrated. Add concurrency tests for migrated fields. Verify no regressions.
- Files to touch: androidMain/*.kt, iosMain/*.kt (various)
- Risks: Must ensure all fields are migrated. Must verify no race conditions are introduced.
