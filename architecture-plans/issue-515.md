# Issue #515: Add connection quality history and RSSI trend analysis for proactive reconnection
- Severity: medium
- Impact: high
- Approach: Add connection quality monitoring: (1) collect RSSI samples over time, (2) compute trend (improving/degrading/stable), (3) expose quality history as Flow. Platform-specific: Android/iOS provide RSSI readings differently. Use sliding window for trend analysis. Provide reconnection recommendation based on trend.
- Files to touch: commonMain/kmpble/client/ConnectionQuality.kt (new), commonMain/kmpble/client/ConnectionQualityHistory.kt (new), iosMain/kmpble/client/ConnectionQuality.ios.kt, androidMain/kmpble/client/ConnectionQuality.android.kt
- Risks: Must handle rapid RSSI changes. Must not block the main connection loop. Must provide cancellation support. Must handle platform-specific RSSI reporting differences.
