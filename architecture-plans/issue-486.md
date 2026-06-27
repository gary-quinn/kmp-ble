# Issue #486: refactor(common): decompose FakePeripheral (373 lines) into focused modules
- Severity: medium
- Impact: low
- Approach: Decompose FakePeripheral into: (1) FakePeripheralConnection.kt, (2) FakePeripheralGatt.kt, (3) FakePeripheralAdvertising.kt. Isolate test helper methods to dedicated classes. Maintain test API.
- Files to touch: commonMain/test/FakePeripheral.kt, commonMain/test/FakePeripheralConnection.kt (new), commonMain/test/FakePeripheralGatt.kt (new), commonMain/test/FakePeripheralAdvertising.kt (new)
- Risks: Must preserve all existing test behavior. Decomposition must not break existing tests.
