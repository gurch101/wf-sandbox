---
name: fetch-pr-review-comments
description: Fetch GitHub pull request review comments from a provided PR URL and output a Markdown table sorted by filename and line number. Use when a user shares a GitHub PR link and asks to list, export, summarize, or process review comments.
---

# Fetch PR Review Comments

Use this skill to retrieve review comments for a GitHub pull request and print a Markdown table.

## Run

1. Execute:

```bash
.agents/skills/fetch-pr-review-comments/scripts/fetch_pr_review_comments.sh <github_pr_url>
```

2. Capture stdout as the final table output.

## Input

- Accept one argument: a full GitHub PR URL in the format `https://github.com/<owner>/<repo>/pull/<number>`.

## Output

- Print a Markdown table with columns:
- `filename`
- `line number`
- `comment`
- Ensure rows are sorted by filename, then line number.

## Troubleshoot

- If `GITHUB_TOKEN` is unavailable, rely on `gh auth token` if GitHub CLI is configured.
- If GitHub API requests fail due to permissions or rate limits, set `GITHUB_TOKEN` and rerun.
