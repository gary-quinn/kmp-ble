#!/usr/bin/env bash
set -euo pipefail

# Generates a Keep-a-Changelog-style markdown section from merged PRs
# between two git tags. Falls back to commit messages when no PRs are found.
#
# Usage: generate-changelog.sh <prev_tag> <current_tag>
# Output: writes changelog entries to /tmp/changelog_entries.txt

PREV_TAG="${1:?Usage: generate-changelog.sh <prev_tag> <current_tag>}"
TAG="${2:?Usage: generate-changelog.sh <prev_tag> <current_tag>}"

ADDED=""
CHANGED=""
FIXED=""
OTHER=""

is_automated() {
  echo "$1" | grep -qiE "^docs: update (README|docs) for"
}

categorize() {
  local title="$1"
  local labels="${2:-}"

  if echo "$labels" | grep -qi "added" || echo "$title" | grep -qiE "^(feat|add):"; then
    ADDED="${ADDED}- ${title}\n"
  elif echo "$labels" | grep -qi "fixed" || echo "$title" | grep -qiE "^fix:"; then
    FIXED="${FIXED}- ${title}\n"
  elif echo "$labels" | grep -qi "changed" || echo "$title" | grep -qiE "^(refactor|perf|chore):"; then
    CHANGED="${CHANGED}- ${title}\n"
  else
    OTHER="${OTHER}- ${title}\n"
  fi
}

PR_NUMBERS=$(git log "${PREV_TAG}..HEAD" --oneline --grep="Merge pull request" \
  | grep -oE '#[0-9]+' | tr -d '#' || true)

for PR in $PR_NUMBERS; do
  PR_DATA=$(gh pr view "$PR" --json title,labels --jq '{title: .title, labels: [.labels[].name]}' 2>/dev/null) || continue
  TITLE=$(echo "$PR_DATA" | jq -r '.title')
  LABELS=$(echo "$PR_DATA" | jq -r '.labels[]' 2>/dev/null)

  is_automated "$TITLE" && continue
  categorize "$TITLE" "$LABELS"
done

if [ -z "$ADDED" ] && [ -z "$CHANGED" ] && [ -z "$FIXED" ] && [ -z "$OTHER" ]; then
  while IFS= read -r MSG; do
    [ -z "$MSG" ] && continue
    is_automated "$MSG" && continue
    categorize "$MSG"
  done <<< "$(git log "${PREV_TAG}..HEAD" --no-merges --format="%s" || true)"
fi

ENTRIES=""
[ -n "$ADDED" ]   && ENTRIES="${ENTRIES}\n### Added\n${ADDED}"
[ -n "$CHANGED" ] && ENTRIES="${ENTRIES}\n### Changed\n${CHANGED}"
[ -n "$FIXED" ]   && ENTRIES="${ENTRIES}\n### Fixed\n${FIXED}"
[ -n "$OTHER" ]   && ENTRIES="${ENTRIES}\n### Other\n${OTHER}"

if [ -z "$ENTRIES" ]; then
  ENTRIES="\n_No notable changes._\n"
fi

echo -e "$ENTRIES" > /tmp/changelog_entries.txt
