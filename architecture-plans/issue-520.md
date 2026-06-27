# Issue #520: Add GATT notification batching for high-frequency sensor data
- Severity: medium
- Impact: high
- Approach: Add GATT notification batching: (1) batch multiple notifications before dispatching, (2) configurable batch size and timeout, (3) emit batched updates. Platform-specific: Android/iOS handle notification batching differently. Use coroutine-based batching with configurable window.
- Files to touch: commonMain/kmpble/gatt/NotificationBatcher.kt (new), commonMain/kmpble/client/Peripheral.kt (add batching support), iosMain/kmpble/gatt/NotificationBatcher.ios.kt, androidMain/kmpble/gatt/NotificationBatcher.android.kt
- Risks: Must handle rapid notification rates. Must not block the main GATT callback loop. Must provide cancellation support. Must handle platform-specific notification timing.
