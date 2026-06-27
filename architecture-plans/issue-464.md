# Issue #464: refactor(ios): decompose IosPeripheral (286 lines) into focused modules
- Severity: medium
- Impact: low
- Approach: Duplicate of #367, #482, and #473. Consolidate into single PR. Decompose IosPeripheral into: (1) IosPeripheralConnection.kt, (2) IosPeripheralGatt.kt, (3) IosPeripheralAdvertising.kt. Extract platform-specific callback routing to dedicated adapter classes.
- Files to touch: iosMain/kmpble/client/IosPeripheral.kt, iosMain/kmpble/client/IosPeripheralConnection.kt (new), iosMain/kmpble/client/IosPeripheralGatt.kt (new), iosMain/kmpble/client/IosPeripheralAdvertising.kt (new)
- Risks: Must preserve all existing peripheral behavior. Decomposition must not introduce circular dependencies.
