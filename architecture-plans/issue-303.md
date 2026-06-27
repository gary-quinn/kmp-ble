# Issue #303: Add AndroidPeripheral GATT event handling integration tests
- Severity: medium
- Impact: high
- Approach: Create AndroidPeripheral integration tests for GATT event handling: (1) notification/indication callbacks, (2) characteristic read/write events, (3) service discovery completion, (4) connection state changes. Use androidHostTest with mocked BluetoothGatt callbacks.
- Files to touch: src/androidHostTest/kmpble/peripheral/AndroidPeripheralGATTTest.kt (new), src/androidMain/kmpble/peripheral/AndroidPeripheral.kt (read-only)
- Risks: Must match real Android BluetoothGatt callback timing. Platform-specific event ordering must be preserved.
