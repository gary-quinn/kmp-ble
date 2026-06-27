# Issue #448: Add ScanRegion and proximity-based zone entry/exit detection
- Severity: low
- Impact: medium
- Approach: Implement ScanRegion API: (1) define proximity zones (near/far/unknown), (2) monitor RSSI trends, (3) detect zone transitions, (4) provide callbacks for zone entry/exit. Use sliding window RSSI analysis. Platform-specific RSSI collection via Connection.rssiFlow.
- Files to touch: src/commonMain/kmpble/client/ScanRegion.kt (new), src/commonMain/kmpble/client/ScanRegionConfig.kt (new), src/commonMain/kmpble/client/Connection.kt (integrate with rssiFlow)
- Risks: Must handle rapid RSSI changes. Threshold tuning for zone detection. Platform-specific RSSI reporting differences.
