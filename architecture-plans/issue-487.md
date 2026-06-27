# Issue #487: refactor(android): decompose AndroidExtendedAdvertiser (260 lines) into focused modules
- Severity: medium
- Impact: low
- Approach: Decompose AndroidExtendedAdvertiser into: (1) AndroidExtendedAdvertiserConfig.kt, (2) AndroidExtendedAdvertiserAdvertising.kt, (3) AndroidExtendedAdvertiserControl.kt. Isolate @Volatile fields to dedicated class with atomic operations. Add concurrency tests.
- Files to touch: androidMain/advertising/AndroidExtendedAdvertiser.kt, androidMain/advertising/AndroidExtendedAdvertiserConfig.kt (new), androidMain/advertising/AndroidExtendedAdvertiserAdvertising.kt (new), androidMain/advertising/AndroidExtendedAdvertiserControl.kt (new)
- Risks: Must preserve all existing advertiser behavior. Decomposition must not introduce circular dependencies.
