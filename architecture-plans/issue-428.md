# Issue #428: Add FakePeripheral integration tests for dataLengthParameters property
- Severity: medium
- Impact: medium
- Approach: Create integration tests validating dataLengthParameters property behavior in FakePeripheral. Test setter/getter, validation, and impact on connection setup. Verify platform-specific behavior matches real peripheral implementations.
- Files to touch: src/jvmTest/kmpble/fake/FakePeripheralDataLengthTest.kt (new), src/commonMain/kmpble/peripheral/FakePeripheral.kt (read-only)
- Risks: Must match real peripheral data length negotiation semantics. Platform differences in data length extension may affect test expectations.
