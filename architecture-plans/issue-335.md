# Issue #335: Add Android Scanner integration tests for scan modes, filters, and edge cases
- Severity: medium
- Impact: high
- Approach: Create comprehensive Android-specific Scanner integration tests covering: (1) all scan modes (LOW_LATENCY, BALANCED, LOW_POWER, OPPORTUNISTIC), (2) filter behaviors (name, service UUID, device name), (3) edge cases (rapid mode changes, filter resets, concurrent operations). Use androidHostTest runner with real Android APIs.
- Files to touch: src/androidHostTest/kmpble/scanner/AndroidScannerIntegrationTest.kt (new), src/androidMain/kmpble/client/AndroidScanner.kt (read-only)
- Risks: Android Bluetooth API restrictions. Permission requirements. Must handle platform-specific scan mode behaviors correctly.
