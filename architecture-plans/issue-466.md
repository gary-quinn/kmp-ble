# Issue #466: refactor(common): decompose ConnectionOptions (231 lines) into focused modules
- Severity: medium
- Impact: low
- Approach: Duplicate of #511, #484, and #475. Consolidate into single PR. Decompose ConnectionOptions into: (1) ConnectionOptionsTimeout.kt, (2) ConnectionOptionsSecurity.kt, (3) ConnectionOptionsRetry.kt. Extract builder DSL for each config group.
- Files to touch: commonMain/kmpble/client/ConnectionOptions.kt, commonMain/kmpble/client/ConnectionOptionsTimeout.kt (new), commonMain/kmpble/client/ConnectionOptionsSecurity.kt (new), commonMain/kmpble/client/ConnectionOptionsRetry.kt (new)
- Risks: API break if public API changes. Must maintain backward compatibility.
