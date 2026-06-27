# Issue #303: Add AndroidPeripheral GATT event handling integration tests

## Scope
Create integration tests for AndroidPeripheral GATT event handling:
- Notification/indication callbacks
- Characteristic read/write events
- Service discovery completion
- Connection state changes
- CCCD configuration for observations

## Files to touch
- `src/androidHostTest/kotlin/com/atruedev/kmpble/peripheral/AndroidPeripheralGATTTest.kt` (new)
- `src/androidMain/kmpble/peripheral/AndroidPeripheral.kt` (read-only)

## Risks
- Must match real Android BluetoothGatt callback timing
- Platform-specific event ordering must be preserved
- Test must use androidHostTest runner with mocked BluetoothGatt callbacks

## Implementation Strategy
1. Create AndroidPeripheralGATTTest.kt with test scenarios
2. Use androidHostTest runner with mocked Android APIs
3. Test notification/indication callbacks
4. Test characteristic read/write events
5. Test service discovery completion
6. Test connection state changes
7. Verify CCCD configuration for observations
8. Run tests: `./gradlew testAndroidDebugUnitTest`
9. Verify all tests pass
