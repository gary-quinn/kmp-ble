# Issue #489: refactor(android): decompose AndroidPeripheral (277 lines) into focused modules
- Severity: medium
- Impact: low
- Approach: Decompose AndroidPeripheral into: (1) AndroidPeripheralConnection.kt, (2) AndroidPeripheralGatt.kt, (3) AndroidPeripheralAdvertising.kt. Isolate @Volatile fields to dedicated class with atomic operations. Add concurrency tests.
- Files to touch: androidMain/client/AndroidPeripheral.kt, androidMain/client/AndroidPeripheralConnection.kt (new), androidMain/client/AndroidPeripheralGatt.kt (new), androidMain/client/AndroidPeripheralAdvertising.kt (new)
- Risks: Must preserve all existing peripheral behavior. Decomposition must not introduce circular dependencies.
