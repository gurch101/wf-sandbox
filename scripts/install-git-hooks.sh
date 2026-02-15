#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
hooks_dir="$repo_root/.githooks"

if [[ ! -d "$hooks_dir" ]]; then
  echo "ERROR: Hooks directory not found at $hooks_dir"
  exit 1
fi

chmod +x "$hooks_dir"/*
git -C "$repo_root" config core.hooksPath .githooks

echo "Git hooks installed."
echo "Configured core.hooksPath=.githooks"
