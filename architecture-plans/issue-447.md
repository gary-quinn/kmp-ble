# Issue #447: Add LE Connection Subrating integration and conformance tests
- Severity: medium
- Impact: high
- Approach: Create tests for LE Connection Subrating feature: (1) successful subrating negotiation, (2) failure scenarios (peer rejection, unsupported), (3) parameter validation, (4) impact on connection stability. Use FakePeripheral with subrating capabilities enabled.
- Files to touch: src/jvmTest/kmpble/connection/LEConnectionSubratingTest.kt (new), src/commonMain/kmpble/client/Connection.kt (read-only), src/commonMain/kmpble/common/ConnectionSubrating.kt (read-only)
- Risks: Must match Bluetooth 5.3 specification exactly. Peer behavior simulation must be accurate. Platform differences in subrating support.
