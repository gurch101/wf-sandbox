#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $(basename "$0") <github_pr_url>" >&2
  exit 64
fi

exec python3 "pr_review_comments_to_markdown.py" "$1"
