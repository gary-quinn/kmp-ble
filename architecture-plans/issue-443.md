# Issue #443: Add scanAndConnect convenience combining Scanner and Peripheral lifecycle
- Severity: low
- Impact: medium
- Approach: Create scanAndConnect extension function: (1) start scan with provided filter, (2) find matching peripheral, (3) connect automatically, (4) cancel scan on connect. Provide sensible defaults and custom filter support. Simplify common use case of scan-then-connect.
- Files to touch: src/commonMain/kmpble/scanner/ScannerExtensions.kt (new), src/commonMain/kmpble/client/Scanner.kt (read-only), src/commonMain/kmpble/client/Peripheral.kt (read-only)
- Risks: Must handle scan cancellation correctly. Connection timeout handling. Filter validation. Platform-specific scan behaviors.
