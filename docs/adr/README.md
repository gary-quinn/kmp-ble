# Architecture Decision Records (ADRs)

Human-owned decisions for kmp-ble. Agents may implement within these bounds; they do not replace verification or CI.

| ADR | Title | Status |
| --- | --- | --- |
| [ADR-0001](ADR-0001-jvm-linux-bluez.md) | JVM Linux BlueZ scanner (M1) | Proposed (#672) |
| [ADR-0002](ADR-0002-verification-first-agent-assisted-development.md) | Verification-first agent-assisted development | Accepted |

## Template

New ADRs use the next sequential number: `ADR-NNNN-short-title.md`.

One-way doors (public API shape, new module, expect/actual boundary, publication strategy) need a new ADR or explicit Gary approval before agent implementation.
