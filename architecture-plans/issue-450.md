# Issue #450: Add prepareWrite/executeWrite reliable long write API for large characteristic values
- Severity: medium
- Impact: high
- Approach: Implement reliable long write API: (1) prepareWrite to queue large writes, (2) executeWrite to commit batch, (3) handle write failures with re-queue logic, (4) platform-specific implementation (Android: writePrepareCharacteristic, iOS: writePrepareCharacteristic). Simplify large data transfers.
- Files to touch: src/commonMain/kmpble/gatt/ReliableLongWrite.kt (new), src/commonMain/kmpble/client/Characteristic.kt (add prepareWrite/executeWrite), src/androidMain/kmpble/gatt/Characteristic.android.kt (implement), src/iosMain/kmpble/gatt/Characteristic.ios.kt (implement)
- Risks: Must handle write queue management correctly. Error recovery for failed prepareWrite. Platform-specific long write limits.
