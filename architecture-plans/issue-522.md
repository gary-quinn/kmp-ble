# Issue #522: Add Connection.reconnect() convenience for single-tap reconnection
- Severity: medium
- Impact: high
- Approach: Add `Connection.reconnect()` suspend function: (1) disconnect current connection, (2) reconnect with same parameters, (3) return new Connection. Handle timeout and failure. Reuse existing connect/disconnect APIs. Provide reconnection status flow.
- Files to touch: commonMain/kmpble/client/Connection.kt (add reconnect method), commonMain/kmpble/client/Reconnect.kt (new, internal)
- Risks: Must handle reconnection timing. Must not leak connection resources. Must handle platform-specific reconnection behavior.
