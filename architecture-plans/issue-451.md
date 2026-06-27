# Issue #451: Add BLE Channel Assessment for interference detection and adaptive PHY selection
- Severity: medium
- Impact: medium
- Approach: Implement BLE Channel Assessment API: (1) scan channel map, (2) measure interference per channel, (3) provide channel quality metrics, (4) optional adaptive PHY selection based on channel quality. Platform-specific implementation: Android uses AdvertiseSettings, iOS uses CBAdvertisementData.
- Files to touch: src/commonMain/kmpble/phy/ChannelAssessment.kt (new), src/androidMain/kmpble/phy/ChannelAssessment.android.kt (new), src/iosMain/kmpble/phy/ChannelAssessment.ios.kt (new), src/commonMain/kmpble/client/Connection.kt (add channel quality API)
- Risks: Platform-specific BLE APIs differ. Must handle channel mapping correctly. Performance impact of continuous channel scanning.
