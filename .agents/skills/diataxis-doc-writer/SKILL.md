---
name: diataxis-doc-writer
description: Write or refactor project technical documentation into Markdown that follows the Diátaxis framework (Tutorials, How-to Guides, Reference, Explanations). Use when users ask to document features, APIs, workflows, architecture, operations, onboarding, or existing docs that need clearer structure and purpose.
---

# Diataxis Doc Writer

## Workflow

1. Identify documentation intent and audience from the request and source material.
2. Map requested content to one or more Diátaxis quadrants:
- Tutorial: learning-oriented, step-by-step introduction.
- How-to: goal-oriented task instructions.
- Reference: factual lookup of interfaces, options, contracts, schemas.
- Explanation: conceptual rationale, tradeoffs, architecture.
3. Produce Markdown sections/files with explicit quadrant labels and avoid mixing intents in one section.
4. Validate structure and quality before finalizing.

## Output Rules

- Use clear Markdown headings and short paragraphs.
- Prefer concrete examples over abstract prose.
- Keep commands and paths copy-pasteable.
- Keep reference content precise and complete; avoid narrative there.
- Keep tutorials progressive; do not assume expert context in tutorial steps.
- Keep how-to guides minimal and outcome-focused.
- Keep explanations free of procedural step lists unless absolutely needed.

## Required Checks

Before final output, verify:

- Each section has a single Diátaxis intent.
- Prerequisites and assumptions are explicit.
- Steps are testable and in runnable order.
- Reference data is internally consistent (names, defaults, constraints).
- Cross-links between related quadrants are present when useful.

## File Strategy

- For a small request, return one Markdown file with quadrant headings.
- For larger docs, split by quadrant into separate files:
- `tutorials/`
- `how-to/`
- `reference/`
- `explanation/`

When splitting files, add a short index section linking them.

## Use References

Read [references/diataxis-templates.md](references/diataxis-templates.md) when you need:

- ready-to-use Markdown templates,
- quadrant classification heuristics,
- anti-pattern checks.
