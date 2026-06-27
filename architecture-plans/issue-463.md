# Issue #463: refactor(android): decompose AndroidGattBridge (287 lines) into focused modules
- Severity: medium
- Impact: low
- Approach: Duplicate of #501, #521, #481, and #472. Consolidate into single PR. Decompose AndroidGattBridge into: (1) AndroidGattBridgeConnection.kt, (2) AndroidGattBridgeGatt.kt, (3) AndroidGattBridgeCallback.kt. Isolate @Volatile fields in callback channel to dedicated class with atomic operations.
- Files to touch: androidMain/gatt/AndroidGattBridge.kt, androidMain/gatt/AndroidGattBridgeConnection.kt (new), androidMain/gatt/AndroidGattBridgeGatt.kt (new), androidMain/gatt/AndroidGattBridgeCallback.kt (new)
- Risks: Must preserve all existing callback semantics. Callback channel migration must be thoroughly tested.
