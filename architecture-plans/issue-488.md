# Issue #488: refactor(common): decompose IsochronousStream (282 lines) into focused modules
- Severity: medium
- Impact: low
- Approach: Duplicate of #516 and #490. Consolidate into single PR. Decompose IsochronousStream into: (1) IsochronousStreamConfig.kt, (2) IsochronousStreamPlayback.kt, (3) IsochronousStreamMetadata.kt.
- Files to touch: commonMain/leaudio/IsochronousStream.kt, commonMain/leaudio/IsochronousStreamConfig.kt (new), commonMain/leaudio/IsochronousStreamPlayback.kt (new), commonMain/leaudio/IsochronousStreamMetadata.kt (new)
- Risks: Same as #516 and #490.
