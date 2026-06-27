# Issue #335: Add Android Scanner integration tests
- Severity: medium
- Impact: high
- Approach: Create comprehensive Android-specific Scanner integration tests covering: (1) all scan modes (LOW_LATENCY, BALANCED, LOW_POWER, OPPORTUNISTIC), (2) filter behaviors (name, service UUID, device name), (3) edge cases (rapid mode changes, filter resets, concurrent operations). Use androidHostTest runner with real Android APIs.
- Files to touch: src/androidMain/kotlin/com/atruedev/kmpble/scanner/AndroidScanner.kt (create AndroidScannerHostTest.kt), src/androidHostTest/kotlin/com/atruedev/kmpble/scanner/AndroidScannerTest.kt
- Risks: Must handle Android permission checks. Must not break existing Android Scanner tests. Must use androidHostTest runner.
