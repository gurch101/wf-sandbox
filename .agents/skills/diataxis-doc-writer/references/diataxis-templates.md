# Diátaxis Templates

## Quadrant Picker

Use this quick classifier:

- User says "teach me" or "learn": Tutorial.
- User says "how do I do X": How-to.
- User says "what are the fields/options/endpoints": Reference.
- User says "why does this work this way" or "tradeoffs": Explanation.

If content mixes intents, split by quadrant.

## Tutorial Template

```md
# Tutorial: <topic>

## Outcome
What the learner will build/achieve.

## Prerequisites
Minimal setup and prior knowledge.

## Step 1: <first action>
...

## Step 2: <next action>
...

## Verify
Expected result and quick checks.

## Next Steps
Links to how-to/reference for deeper tasks.
```

## How-to Template

```md
# How-to: <task>

## Goal
Single operational outcome.

## Prerequisites
Required permissions, tools, or state.

## Procedure
1. ...
2. ...
3. ...

## Validation
How to confirm success.

## Troubleshooting
Common failure modes and fixes.
```

## Reference Template

```md
# Reference: <component>

## Summary
One-paragraph scope.

## API / Interface
- Field/Param: type, required, default, constraints
- ...

## Examples
Minimal valid examples.

## Errors / Edge Cases
Enumerated, factual behavior.
```

## Explanation Template

```md
# Explanation: <concept>

## Context
Why this area matters.

## Design
Core model, architecture, boundaries.

## Tradeoffs
What was optimized and what was sacrificed.

## Alternatives Considered
Brief comparison.

## Related References
Links to corresponding tutorial/how-to/reference docs.
```

## Anti-patterns

- Tutorial that is only conceptual explanation.
- How-to that contains broad architecture history.
- Reference missing constraints/defaults.
- Explanation written as command-only runbook.
