# Issue #494: refactor(common): decompose L2CAPChannel (287 lines) into focused modules
- Severity: medium
- Impact: low
- Approach: Decompose L2CAPChannel into: (1) L2CAPChannelConnection.kt, (2) L2CAPChannelData.kt, (3) L2CAPChannelError.kt. Isolate @Volatile fields to dedicated class with atomic operations. Add concurrency tests.
- Files to touch: commonMain/l2cap/L2CAPChannel.kt, commonMain/l2cap/L2CAPChannelConnection.kt (new), commonMain/l2cap/L2CAPChannelData.kt (new), commonMain/l2cap/L2CAPChannelError.kt (new)
- Risks: Must preserve all existing channel behavior. Decomposition must not introduce circular dependencies.
