# Issue #518: Add PHY read/write API for Bluetooth 5.x physical layer control
- Severity: medium
- Impact: medium
- Approach: Add PHY control API: (1) read current PHY, (2) write/select PHY, (3) query PHY capabilities. Platform-specific: Android uses BluetoothGatt.setPHY/getPHY; iOS uses CBPeripheral writeValue for PHY control. Handle platform capability checks gracefully.
- Files to touch: commonMain/kmpble/phy/PHY.kt (new), commonMain/kmpble/phy/PHYControl.kt (new), iosMain/kmpble/phy/PHYControl.ios.kt, androidMain/kmpble/phy/PHYControl.android.kt
- Risks: PHY control is Bluetooth 5.0+ only -- not all chips support it. Must handle platform capability checks gracefully.
