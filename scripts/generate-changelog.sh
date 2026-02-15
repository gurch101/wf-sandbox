#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

output_file="CHANGELOG.md"
from_ref=""
to_ref="HEAD"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --from)
      from_ref="${2:?missing value for --from}"
      shift 2
      ;;
    --to)
      to_ref="${2:?missing value for --to}"
      shift 2
      ;;
    --output)
      output_file="${2:?missing value for --output}"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1"
      echo "Usage: $0 [--from <git-ref>] [--to <git-ref>] [--output <file>]"
      exit 1
      ;;
  esac
done

if [[ -z "$from_ref" ]]; then
  from_ref="$(git describe --tags --abbrev=0 2>/dev/null || true)"
fi

if [[ -n "$from_ref" ]]; then
  range="${from_ref}..${to_ref}"
  range_label="${from_ref}..${to_ref}"
else
  range="$to_ref"
  range_label="start..${to_ref}"
fi

declare -A section_titles=(
  [feat]="Features"
  [fix]="Bug Fixes"
  [perf]="Performance"
  [refactor]="Refactoring"
  [docs]="Documentation"
  [build]="Build"
  [ci]="CI"
  [test]="Tests"
  [chore]="Chores"
  [style]="Style"
  [revert]="Reverts"
)

declare -a order=(feat fix perf refactor docs build ci test chore style revert)
declare -A sections
breaking=""

while IFS=$'\x1e' read -r record; do
  [[ -z "$record" ]] && continue

  IFS=$'\x1f' read -r full_hash subject body <<<"$record"
  short_hash="${full_hash:0:7}"

  if [[ "$subject" =~ ^(Merge|Revert)\  ]]; then
    continue
  fi

  if [[ "$subject" =~ ^(build|chore|ci|docs|feat|fix|perf|refactor|revert|style|test)(\(([a-z0-9._/-]+)\))?(!)?:[[:space:]]+(.+)$ ]]; then
    type="${BASH_REMATCH[1]}"
    scope="${BASH_REMATCH[3]:-}"
    bang="${BASH_REMATCH[4]:-}"
    description="${BASH_REMATCH[5]}"

    if [[ -n "$scope" ]]; then
      line="- **${scope}**: ${description} (${short_hash})"
    else
      line="- ${description} (${short_hash})"
    fi

    sections["$type"]+="${line}"$'\n'

    if [[ -n "$bang" || "$body" == *"BREAKING CHANGE:"* ]]; then
      breaking+="${line}"$'\n'
    fi
  fi
done < <(git log "$range" --pretty=format:'%H%x1f%s%x1f%b%x1e')

today="$(date +%F)"

{
  echo "# Changelog"
  echo
  echo "Generated from \`${range_label}\` on ${today}."
  echo

  if [[ -n "$breaking" ]]; then
    echo "## Breaking Changes"
    echo
    printf "%s" "$breaking"
    echo
  fi

  for type in "${order[@]}"; do
    section_content="${sections[$type]-}"
    [[ -z "$section_content" ]] && continue
    echo "## ${section_titles[$type]}"
    echo
    printf "%s" "$section_content"
    echo
  done
} >"$output_file"

echo "Wrote ${output_file}"
