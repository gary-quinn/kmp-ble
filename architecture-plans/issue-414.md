# Issue #414: Fix KDoc link registerIosProvider -> IosQuirkProviders.register
- Severity: low
- Impact: low
- Approach: Update KDoc comment in QuirkRegistry.ios.kt to reference correct function name IosQuirkProviders.register instead of registerIosProvider. Verify no other stale references exist. Run documentation build to confirm fix.
- Files to touch: src/iosMain/kmpble/quirks/QuirkRegistry.ios.kt (update KDoc), src/commonMain/kmpble/quirks/QuirkRegistry.kt (verify)
- Risks: Minimal - documentation update only. Verify no other files reference registerIosProvider.
