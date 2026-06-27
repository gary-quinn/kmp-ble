# Issue #453: Add L2CAP channel error recovery and graceful close
- Severity: medium
- Impact: high
- Approach: Implement L2CAP channel error recovery: (1) detect channel errors (disconnection, timeout), (2) provide graceful close with cleanup, (3) reconnection support, (4) error callbacks for application handling. Platform-specific L2CAP error handling.
- Files to touch: src/commonMain/kmpble/l2cap/L2CAPChannel.kt (add error recovery), src/androidMain/kmpble/l2cap/L2CAPChannel.android.kt (implement), src/iosMain/kmpble/l2cap/L2CAPChannel.ios.kt (implement)
- Risks: Must handle platform-specific L2CAP error semantics. Connection state management. Cleanup order must be correct.
