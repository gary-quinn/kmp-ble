# Issue #526: Implement iOS scan response data reading to match Android functionality
- Severity: medium
- Impact: medium
- Approach: Implement iOS-specific scan response data reading: (1) read scan response data from peripheral, (2) parse data according to Bluetooth spec, (3) expose as ScanResult property. Platform-specific: iOS uses CBScanResponseDataKey. Verify parity with Android implementation.
- Files to touch: iosMain/kmpble/client/Scanner.ios.kt, commonMain/kmpble/client/ScanResult.kt (read-only)
- Risks: iOS Bluetooth API restrictions -- may require specific iOS version. Must verify parity with Android implementation.
