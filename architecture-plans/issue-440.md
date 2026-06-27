# Issue #440: Add AoA/AoD Direction Finding integration and conformance tests
- Severity: medium
- Impact: medium
- Approach: Add integration tests for AoA/AoD Direction Finding: (1) angle estimation, (2) direction calculation, (3) accuracy validation. Use mock BLE stack with direction finding support. Test both AoA and AoD modes. Verify conformance with Bluetooth 5.1 spec.
- Files to touch: commonMain/test/DirectionFindingTest.kt (new), commonMain/kmpble/aoa/DirectionFinding.kt (read-only)
- Risks: Mock BLE stack may not fully simulate direction finding. Cross-platform parity: ensure iOS mock also exercises direction finding paths.
