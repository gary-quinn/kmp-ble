# Issue #452: Add ConnectionFailure class for categorized BLE connection error handling
- Severity: low
- Impact: medium
- Approach: Create ConnectionFailure hierarchy: (1) TimeoutFailure, (2) SecurityFailure, (3) DisconnectionFailure, (4) ConnectionRefusedFailure. Provide typed errors instead of generic exceptions. Platform-specific error mapping. Improve error handling and debugging.
- Files to touch: src/commonMain/kmpble/common/ConnectionFailure.kt (new), src/commonMain/kmpble/common/ConnectionFailureFactory.kt (new), src/androidMain/kmpble/client/Connection.android.kt (map errors), src/iosMain/kmpble/client/Connection.ios.kt (map errors)
- Risks: Must match platform-specific error semantics. Backward compatibility with existing error handling. Error mapping accuracy.
