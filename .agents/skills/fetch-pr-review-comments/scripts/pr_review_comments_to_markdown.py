#!/usr/bin/env python3
"""Fetch GitHub pull request review comments and print a Markdown table.

Usage:
  python pr_review_comments_to_markdown.py <pull_request_url>

Environment variables:
  GITHUB_TOKEN: optional personal access token (recommended to avoid rate limits,
                required for private repositories).
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from typing import Dict, List, Tuple


PR_URL_RE = re.compile(
    r"^https?://github\.com/(?P<owner>[^/]+)/(?P<repo>[^/]+)/pull/(?P<number>\d+)(?:/.*)?$"
)


def parse_pr_url(pr_url: str) -> Tuple[str, str, int]:
    match = PR_URL_RE.match(pr_url.strip())
    if not match:
        raise ValueError(
            "Invalid GitHub pull request URL. Expected format: "
            "https://github.com/<owner>/<repo>/pull/<number>"
        )

    owner = match.group("owner")
    repo = match.group("repo")
    number = int(match.group("number"))
    return owner, repo, number


def build_headers() -> Dict[str, str]:
    headers = {
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "pr-review-comments-to-markdown-script",
    }
    token = os.getenv("GITHUB_TOKEN") or github_token_from_gh_cli()
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return headers


def github_token_from_gh_cli() -> str | None:
    """Best-effort token discovery for users already authenticated with GitHub CLI."""
    try:
        proc = subprocess.run(
            ["gh", "auth", "token"],
            check=False,
            capture_output=True,
            text=True,
        )
    except OSError:
        return None

    if proc.returncode != 0:
        return None

    token = proc.stdout.strip()
    return token or None


def resolve_canonical_pr_url(pr_url: str) -> str:
    """Follow redirects so moved/transferred repos still resolve correctly."""
    request = urllib.request.Request(pr_url.strip(), method="GET")
    try:
        with urllib.request.urlopen(request) as response:
            return response.geturl()
    except urllib.error.URLError:
        # Fall back to original URL in offline/limited-network environments.
        return pr_url.strip()


def fetch_json(url: str, headers: Dict[str, str]) -> List[dict]:
    request = urllib.request.Request(url, headers=headers, method="GET")
    try:
        with urllib.request.urlopen(request) as response:
            body = response.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"GitHub API request failed ({exc.code}): {detail}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Network error while calling GitHub API: {exc}") from exc

    data = json.loads(body)
    if not isinstance(data, list):
        raise RuntimeError(f"Unexpected GitHub API response: expected list, got {type(data).__name__}")
    return data


def fetch_all_review_comments(owner: str, repo: str, pr_number: int) -> List[dict]:
    headers = build_headers()
    comments: List[dict] = []
    page = 1

    while True:
        endpoint = (
            f"https://api.github.com/repos/{owner}/{repo}/pulls/{pr_number}/comments"
            f"?per_page=100&page={page}"
        )
        batch = fetch_json(endpoint, headers)
        comments.extend(batch)
        if len(batch) < 100:
            break
        page += 1

    return comments


def sanitize_cell(text: str) -> str:
    # Keep markdown table rows valid by flattening newlines and escaping pipes.
    return text.replace("\r", " ").replace("\n", " <br> ").replace("|", "\\|").strip()


def comment_line_number(comment: dict) -> str:
    # GitHub review comments may expose either `line` or legacy `original_line`.
    line = comment.get("line")
    if line is None:
        line = comment.get("original_line")
    return "" if line is None else str(line)


def comment_sort_key(comment: dict) -> Tuple[str, int]:
    filename = str(comment.get("path", ""))
    line = comment.get("line")
    if line is None:
        line = comment.get("original_line")
    if not isinstance(line, int):
        line = sys.maxsize
    return filename, line


def print_markdown_table(comments: List[dict]) -> None:
    print("| filename | line number | comment |")
    print("| --- | --- | --- |")

    for comment in sorted(comments, key=comment_sort_key):
        filename = sanitize_cell(str(comment.get("path", "")))
        line = sanitize_cell(comment_line_number(comment))
        body = sanitize_cell(str(comment.get("body", "")))
        print(f"| {filename} | {line} | {body} |")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Extract review comments from a GitHub pull request and output a Markdown table."
    )
    parser.add_argument("pull_request_url", help="GitHub pull request URL")
    args = parser.parse_args()

    try:
        canonical_url = resolve_canonical_pr_url(args.pull_request_url)
        owner, repo, pr_number = parse_pr_url(canonical_url)
        comments = fetch_all_review_comments(owner, repo, pr_number)
        print_markdown_table(comments)
        return 0
    except Exception as exc:  # pragma: no cover
        print(f"Error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
