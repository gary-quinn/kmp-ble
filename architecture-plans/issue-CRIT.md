# Issue #CRIT: Add architecture review gate for public API additions
- Severity: critical
- Impact: high
- Approach: Add compileKotlinJvm check to PR checklist, require architecture plan review before public API additions, add linter rule to detect new public interfaces without implementations.
- Files to touch: .github/pull_request_template.md, .githooks/pre-commit, architecture-plan.md
- Risks: May slow down feature development if gate is too restrictive. Must balance safety with velocity.
