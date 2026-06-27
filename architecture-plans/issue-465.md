# Issue #465: refactor(common): decompose Peripheral.kt (373 lines) into focused handler modules
- Severity: medium
- Impact: low
- Approach: Duplicate of #441, #483, and #474. Consolidate into single PR. Decompose Peripheral.kt into: (1) PeripheralConnectHandler.kt, (2) PeripheralDiscoverHandler.kt, (3) PeripheralOperationHandler.kt. Extract platform-specific callback routing to dedicated adapter classes.
- Files to touch: commonMain/kmpble/client/Peripheral.kt, commonMain/kmpble/client/PeripheralConnectHandler.kt (new), commonMain/kmpble/client/PeripheralDiscoverHandler.kt (new), commonMain/kmpble/client/PeripheralOperationHandler.kt (new), commonMain/kmpble/client/PeripheralCallbackAdapter.kt (new)
- Risks: High refactoring risk due to file size and cross-platform abstraction layer. Must verify no expect/actual contract changes.
