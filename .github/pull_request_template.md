# PR Checklist

## Architecture Review
- [ ] Architecture plan exists: `architecture-plans/issue-<N>.md`
- [ ] New public interfaces have: (a) imports, (b) implementations, (c) cross-platform parity
- [ ] `./gradlew compileKotlinJvm` passes before request for review
- [ ] No broken interface extraction (see architectural-regression-detection.md)

## Self-Review
- [ ] Typography: ASCII only (no em-dash, en-dash, smart quotes, ellipsis, NBSP)
- [ ] Compile: `./gradlew compileKotlinJvm` passes
- [ ] Lint: `./gradlew ktlintCheck` passes
- [ ] Tests: New code has tests (kotlin.test + Turbine + Lincheck for concurrency)

## Process
- [ ] No direct pushes to main (create feature branch: `git checkout -b <type>/<desc>`)
- [ ] Commit messages follow Conventional Commits
- [ ] PR title matches commit subject
