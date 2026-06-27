# Issue #523: Add PHY layer metrics collection for connection quality monitoring
- Severity: medium
- Impact: medium
- Approach: Add PHY metrics collection: (1) collect PHY-specific metrics (RSSI, SNR, etc.), (2) expose as Flow<PHYMetrics>, (3) compute quality score. Platform-specific: Android/iOS provide PHY metrics differently. Use coroutine-based collection with configurable interval.
- Files to touch: commonMain/kmpble/phy/PHYMetrics.kt (new), commonMain/kmpble/phy/PHYMetricsCollector.kt (new), iosMain/kmpble/phy/PHYMetricsCollector.ios.kt, androidMain/kmpble/phy/PHYMetricsCollector.android.kt
- Risks: Must handle platform-specific PHY metric availability. Must not block the main connection loop. Must provide cancellation support.
