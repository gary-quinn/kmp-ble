# Issue #441: Decompose Peripheral.kt (373 lines) into focused handler modules
- Severity: medium
- Impact: high
- Approach: Decompose Peripheral.kt (373 lines) into: (1) PeripheralConnection.kt (connection lifecycle), (2) PeripheralGATT.kt (GATT operations), (3) PeripheralEventHandler.kt (event dispatch), (4) PeripheralState.kt (state management). Extract interfaces for testability. Target: <200 lines per file.
- Files to touch: src/commonMain/kmpble/peripheral/Peripheral.kt (split), src/commonMain/kmpble/peripheral/PeripheralConnection.kt (new), src/commonMain/kmpble/peripheral/PeripheralGATT.kt (new), src/commonMain/kmpble/peripheral/PeripheralEventHandler.kt (new), src/commonMain/kmpble/peripheral/PeripheralState.kt (new)
- Risks: Interface compatibility must be maintained. Callback ordering must be preserved. State management semantics must match exactly.
