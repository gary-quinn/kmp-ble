# Issue #533: Add PHY layer metrics collection benchmarks
- Severity: medium
- Impact: medium
- Approach: Add benchmarks for PHY layer metrics collection: (1) collection overhead, (2) memory usage, (3) CPU impact. Use Android benchmarks. Verify no performance regression. Compare with baseline.
- Files to touch: androidMain/benchmark/PHYMetricsBenchmark.kt (new), commonMain/kmpble/phy/PHYMetrics.kt (read-only)
- Risks: Must handle platform-specific benchmark requirements. Must verify no performance regression. Must compare with baseline.
