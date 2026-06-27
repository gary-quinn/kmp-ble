# Issue #513: Add iOS support for scan response data reading in Scanner API
- Severity: medium
- Impact: medium
- Approach: Add iOS-specific implementation for scan response data reading. Platform-specific: iOS uses CBScanResponseDataKey. Verify parity with Android implementation. Add tests for iOS scan response data retrieval.
- Files to touch: iosMain/kmpble/client/Scanner.ios.kt, commonMain/kmpble/client/ScanResult.kt (read-only)
- Risks: iOS Bluetooth API restrictions -- may require specific iOS version. Must verify parity with Android implementation.
