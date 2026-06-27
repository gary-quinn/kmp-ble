# Issue #367: Refactor IosPeripheral regrowth (300 lines) back below 250 lines
- Severity: medium
- Impact: medium
- Approach: Decompose IosPeripheral into focused modules: (1) IosPeripheralConnection.kt (connection management), (2) IosPeripheralGATT.kt (GATT operations), (3) IosPeripheralPeripheral.kt (peripheral-specific logic). Extract helper classes and reduce coupling. Target: 250 lines total across modules.
- Files to touch: src/iosMain/kmpble/peripheral/IosPeripheral.kt (split into 3+ files), src/iosMain/kmpble/peripheral/IosPeripheralConnection.kt (new), src/iosMain/kmpble/peripheral/IosPeripheralGATT.kt (new), src/iosMain/kmpble/peripheral/IosPeripheralPeripheral.kt (new)
- Risks: Must preserve all existing behavior. Callback semantics must remain identical. Platform-specific initialization order must be maintained.
