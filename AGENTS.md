# Agent guidelines

Guidance for AI coding agents and humans alike.

## Typography

ASCII only in source, docs, and CI. The pre-commit hook in `.githooks/pre-commit`
blocks these characters in staged content:

| Char | Codepoint | Use instead |
|------|-----------|-------------|
| em-dash      | U+2014 | `-` |
| en-dash      | U+2013 | `-` |
| left single  | U+2018 | `'` |
| right single | U+2019 | `'` |
| left double  | U+201C | `"` |
| right double | U+201D | `"` |
| ellipsis     | U+2026 | `...` |
| NBSP         | U+00A0 | regular space |

If a violation is genuinely required (regex sample, test fixture, verbatim
quote from a spec), append `typo-ok` anywhere on that line to suppress.
For bulk cases, paths matching `**/testdata/**` or `**/*.fixture.*` are
already excluded; extend `EXCLUDE_PATHS` in `.githooks/pre-commit` if you
need more.

The hook uses `git grep -P` and requires git compiled with PCRE2. Apple Git,
Homebrew git, and most Linux distros ship with it; if `git grep -P` fails
on your build, install one of those.

## Commits and branches

Conventional Commits, strictly. Type prefix on both branch name and commit
subject. Allowed types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`,
`test`, `build`, `ci`, `chore`, `revert`.
