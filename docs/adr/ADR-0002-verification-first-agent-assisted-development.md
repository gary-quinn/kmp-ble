# ADR-0002: Verification-First Agent-Assisted Development

| Field | Value |
| --- | --- |
| Status | **Accepted** |
| Date | 2026-09-08 |
| Deciders | kmp-ble maintainers |

## Context

kmp-ble is a Kotlin Multiplatform BLE library (Android + iOS) with optional extension modules (`kmp-ble-profiles`, `kmp-ble-dfu`, `kmp-ble-codec`, `kmp-ble-codec-serialization`, `kmp-ble-quirks`) and sample apps. The team uses Cursor and Cloud Agents for implementation.

The repo already has merge-time CI (typography, ktlint, Android host tests, JVM Lincheck, sample compile, Android instrumented AVD, iOS compile + `iosSimulatorArm64Test`, Dokka on main). That does not answer the core question for agent-assisted work:

> **Is this change correct -- and how do we prove it?**

Prompts and Cursor rules alone cannot answer that. Agents can ignore rules. CI only runs after a PR exists.

This ADR adapts the four-layer verification-first model (same shape as Castly ADR-0001) to a **library** scope: L1 is Fake-backed unit/host/concurrency tests and instrumented Android framework tests -- not app UI flows (no mobile-mcp cast-flow, no Maestro as core).

## Decision

### Four layers -- L1 is the center

| Layer | Owner | Hardness | kmp-ble examples |
| --- | --- | --- | --- |
| **L0 -- Architecture** | Human | Human-only | `ARCHITECTURE.md` (commonMain owns state machine / GATT queue / observations; thin expect/actual bridges), module seams (extensions depend one-way on core; quirks via SPI), public factories over platform concretes, Fake* testing surface |
| **L1 -- Verification** | Human designs; agent executes | Required before "done" | Matrix commands in [`docs/verification/matrix.md`](../verification/matrix.md): host tests, JVM Lincheck, iOS common tests, extension-module tests, sample compile when samples touched |
| **L2 -- CI / compiler** | Automated | Strict -- blocks merge | `.github/workflows/ci.yml` jobs: typography, android, jvm, sample, android-instrumented, ios (+ docs/Dokka on main) |
| **L3 -- Rules / skills** | Team | Soft -- routing only | `.cursor/rules/verification-first.mdc`, `docs/verification/matrix.md`, pointer in `AGENTS.md` |

**Principle:** L3 routes agents to L1. L2 gates merge. L0 bounds the problem. Quality lives in evidence, not prompts.

### Runtime overlay (deferred)

Castly-style Cursor hooks / secret ignore are **deferred** for kmp-ble (library has no FAL/inference secret surface like Castly). Add later only if secret or policy-file risk appears.

Architecture boundaries and test skipping are **not** enforced by rules -- use L0 structure and L2 CI.

### L0 -- Human-only decisions

Agents operate inside architecture humans have already decided. Canonical doc: [`ARCHITECTURE.md`](../../ARCHITECTURE.md).

Invariants (summary):

- Shared logic in `commonMain`; platform code bridges OS callbacks into coroutines only
- Extension modules depend on core; core does not compile-depend on profiles/dfu/codec
- Public API prefers factories (`Scanner { }`, `Advertisement.toPeripheral()`, etc.) over platform concrete classes
- Physical BLE / GATT Lab E2E remains human-owned (see `TESTING.md` manual checklist)

One-way doors need a new ADR or Gary approval **before** agent implementation:

- Public API / binary-breaking changes
- New published module or changing which modules publish
- New or relocated expect/actual boundaries
- Moving Fake* out of the main artifact / changing the testing package contract

### L1 -- Verification contracts

Canonical matrix: [`docs/verification/matrix.md`](../verification/matrix.md).

Agents run matrix commands for every touched surface and attach evidence (test output, build log, or short note) before marking work done.

**Not L1 for agents (human-only):** physical BLE hardware E2E from `TESTING.md` (scanner/connect/GATT/server/L2CAP/DFU on real devices). Agents must never claim hardware verification completed.

**Deferred L1/L2 (explicitly out of this ADR's day-one scope):** Maestro, mobile-mcp app-UI flows, Castly-style secret hooks. Sample apps may later grow UI verification; core library verification stays Fake/host/instrumented/Lincheck.

### L2 -- CI (document existing; do not expand required checks in this change)

| Job | Runner | Purpose |
| --- | --- | --- |
| `typography` | `ubuntu-latest` | ASCII typography check |
| `android` | `ubuntu-latest` | ktlint, `compileAndroidMain`, `testAndroidHostTest`, sample-quickstart Android unit tests |
| `jvm` | `ubuntu-latest` | `jvmTest` (Lincheck / concurrency) |
| `sample` | `ubuntu-latest` | Compile sample + quickstart (common + Android) |
| `android-instrumented` | `ubuntu-latest` + AVD | `connectedAndroidDeviceTest` |
| `ios` | `macos-26` | `compileKotlinIosSimulatorArm64`, sample iOS compile, `iosSimulatorArm64Test` |
| `docs` | `macos-26` (main push only) | `dokkaGenerate` |

Required checks on `main` today include at least: typography, android, ios, android-instrumented. **Do not** make every CI job required on day one of this ADR. Optional later: promote `jvm` / `sample` to required when stable.

Agents do **not** edit `.github/workflows/**` or other protected policy files without human review.

### L3 -- Rules

`.cursor/rules/verification-first.mdc` (`alwaysApply: true`) tells agents which L1 commands to run for touched surfaces. It does not duplicate CI policy.

## Consequences

### Positive

- Clear "agent done?" contract based on evidence for a multi-module KMP library
- Physical BLE stays human-owned; agents cannot greenwash hardware claims
- Aligns agent workflow with existing CI without copying Castly app-UI tooling

### Negative / deferred

- No Maestro / mobile-mcp for sample apps yet
- Not every CI job is required on day one
- Extension-module test coverage in CI should match the matrix (keep matrix and CI in sync when either changes)
- No Cursor secret hooks (deferred)

### Reversal

Remove this ADR, `docs/verification/`, `.cursor/rules/verification-first.mdc`, and the verification pointer in `AGENTS.md`. Existing tests and CI may remain as ordinary code.

## References

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`TESTING.md`](../../TESTING.md)
- [`docs/verification/matrix.md`](../verification/matrix.md)
- Castly ADR-0001 (shape reference; do not copy app-UI L1)
