# Issue #449: Migrate remaining @Volatile fields to atomicfu
- Severity: critical
- Impact: high
- Approach: Identify all remaining @Volatile fields in iosMain and androidMain, migrate each to atomicfu AtomicInt/AtomicLong/AtomicReference as appropriate. Coordinate with #514, #528, #530 which handle specific subsets. Add concurrency stress tests covering migrated fields.
- Files to touch: iosMain/*.kt, androidMain/*.kt (platform-specific source files containing @Volatile declarations)
- Risks: Must ensure thread-safety is preserved. Race conditions may exist in concurrent usage patterns. Platform-specific synchronization semantics must match.
