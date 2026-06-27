# Issue #442: Add LE Coded PHY awareness to scanner results for long-range discovery
- Severity: medium
- Impact: medium
- Approach: Extend Scanner results to include LE Coded PHY information: (1) detect coded PHY support per advertisement, (2) expose PHY type in ScanResult, (3) platform-specific implementation (Android: scanResult.phy, iOS: CBAdvertisementDataTXPowerLevel). Enable long-range discovery with proper PHY selection.
- Files to touch: src/commonMain/kmpble/scanner/ScanResult.kt (add phy field), src/androidMain/kmpble/scanner/AndroidScanner.kt (implement), src/iosMain/kmpble/scanner/IOSScanner.kt (implement)
- Risks: Platform-specific PHY reporting differs. Must handle cases where PHY is not reported. Compatibility with existing scan result consumers.
