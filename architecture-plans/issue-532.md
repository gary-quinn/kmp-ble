# Issue #532: Add iOS scan response data API to match Android Scanner
- Severity: medium
- Impact: medium
- Approach: Add iOS-specific scan response data API: (1) query scan response data from peripheral, (2) parse data according to Bluetooth spec, (3) expose as Scanner property. Platform-specific: iOS uses CBScanResponseDataKey. Verify parity with Android implementation.
- Files to touch: iosMain/kmpble/client/Scanner.ios.kt, commonMain/kmpble/client/Scanner.kt (read-only)
- Risks: iOS Bluetooth API restrictions -- may require specific iOS version. Must verify parity with Android implementation.
