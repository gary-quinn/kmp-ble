# TODOS

Tracked items from CEO plan review (2026-03-20).

---

## P1 — Blocks implementation

### Investigate SIG YAML vs GATT Specification Supplement for codegen data source
**What:** Determine whether `bluetooth-SIG/public` YAML files contain characteristic payload format definitions (field layouts, flag-dependent parsing rules) or only UUID/name mappings. If formats are PDF-only, hand-authored format definitions are needed.
**Why:** Directly determines Profile Codegen architecture and effort estimate. If hand-authoring is required, effort increases from ~10-14h to ~16-20h CC.
**Context:** The SIG has been migrating from XML to YAML. The `assigned_numbers/` directory has YAML files. The GATT Specification Supplement (PDF) has the detailed field layouts. Need to check which source is machine-readable and sufficient.
**Effort:** S (CC: ~1h research)
**Depends on:** Nothing — can be done immediately
**Blocks:** Profile Codegen Gradle Plugin (v0.3a)

---

## P2 — Design decisions for v0.2

### Design ConnectionRecipe return type
**What:** `ConnectionOptions` has no scan-related fields. Recipes want to specify scan interval/window alongside connection params. Decide: `ConnectionOptions` only (scan settings documented but separate), or a new composite type like `RecipeConfig(connectionOptions, scanSettings)`.
**Why:** Affects public API surface of Connection Recipes feature.
**Context:** Current `ConnectionOptions` fields: autoConnect, timeout, transportType, phyMask, mtuRequest, bondingPreference, reconnectionStrategy. No scan-related fields.
**Effort:** S (CC: ~30min design)
**Depends on:** Nothing
**Blocks:** Connection Recipes implementation (v0.2)

### Fix changelog generation for squash-merged PRs
**What:** Replace `grep -oP 'Merge pull request'` in `update-docs-on-release.yml` with GitHub API approach (`gh pr list --search 'merged:>PREV_TAG_DATE'`) to find PRs regardless of merge strategy.
**Why:** The auto-merge step uses `--squash`, but the changelog generator only finds merge-commit PRs. Squash-merged PRs fall through to the commit-message fallback, losing label-based categorization (added/changed/fixed).
**Context:** Current workflow: line 42-43 of `update-docs-on-release.yml`. Fallback on lines 74-89 works but loses PR label categorization.
**Effort:** S (CC: ~15min)
**Depends on:** Nothing
**Blocks:** Accurate automated changelogs

### Define `peripheral.whenReady{}` edge case behavior
**What:** Specify behavior for: (a) already-connected peripheral — skip connect or throw? (b) connection drops mid-block — throw CancellationException? retry? (c) `close()` in `finally` block — always, or only if `whenReady` created the connection?
**Why:** Without specification, the extension will have surprising behavior in common edge cases.
**Context:** This is a convenience extension (`suspend fun Peripheral.whenReady(options, block)`) for the "one quick read" pattern. Should be obvious and unsurprising.
**Effort:** S (CC: ~15min design)
**Depends on:** Nothing
**Blocks:** Delight Bundle item 5 (v0.2)
