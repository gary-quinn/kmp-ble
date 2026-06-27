# Issue #446: Add RSSI streaming via Connection.rssiFlow for connection quality monitoring
- Severity: medium
- Impact: high
- Approach: Add rssiFlow property to Connection: (1) collect RSSI samples over time, (2) expose as Flow<Float>, (3) platform-specific implementation (Android: BluetoothGattCallback.onReadRssi, iOS: peripheral RSSI property). Provide connection quality monitoring for proactive reconnection decisions.
- Files to touch: src/commonMain/kmpble/client/Connection.kt (add rssiFlow), src/androidMain/kmpble/client/Connection.android.kt (implement), src/iosMain/kmpble/client/Connection.ios.kt (implement)
- Risks: Must handle platform-specific RSSI reporting. Must not block connection operations. Cancellation support required.
