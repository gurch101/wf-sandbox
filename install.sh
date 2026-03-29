#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
envrc_example="$repo_root/.envrc.example"
envrc_file="$repo_root/.envrc"

if [[ ! -f "$envrc_example" ]]; then
  echo "ERROR: Missing $envrc_example"
  exit 1
fi

if [[ -f "$envrc_file" ]]; then
  echo ".envrc already exists. Leaving it unchanged."
else
  cp "$envrc_example" "$envrc_file"
  echo "Created .envrc from .envrc.example."
fi

if ! command -v direnv >/dev/null 2>&1; then
  echo "ERROR: direnv is not installed or not on PATH."
  exit 1
fi

direnv allow "$repo_root"
echo "Allowed direnv for $repo_root."

"$repo_root/scripts/install-git-hooks.sh"
