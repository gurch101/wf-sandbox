---
name: feature-snippet-to-spec
description: Convert short product or engineering feature snippets into implementation-ready markdown specifications with explicit clarifying questions, adjacent feature exploration, user stories, and integration-test-ready acceptance criteria. Use when a user provides partial requirements and wants a complete spec for a developer agent, especially for API-first backends using Spring Boot, JDBC repositories/templates, PostgreSQL, and Camunda 7 workflows.
---

# Feature Snippet To Spec

Generate developer-ready specifications from vague feature requests without guessing.

## Workflow

1. Parse the user snippet into candidate requirement areas:
- Business objective
- User roles
- API behavior
- Data model
- Workflow/process behavior
- Non-functional expectations

2. Run a mandatory clarification phase before writing the final spec:
- Ask concise, grouped questions for every ambiguity.
- Do not assume defaults for missing business rules.
- Ask the user to choose between options when tradeoffs exist.
- Keep asking until blocking ambiguities are resolved.

3. Propose adjacent features that are commonly needed but not explicitly requested:
- Validation and error handling
- Idempotency and retry behavior
- Authorization and audit trail
- Pagination/filtering/sorting
- Concurrency and conflict handling
- Observability (logs/metrics/traces)
- Operational workflows and compensation paths in Camunda
- Backfill/migration considerations for database changes
- Ask the user which suggested adjacent features to include.

4. Produce the final markdown specification using `assets/spec-template.md`.

5. Verify output quality before finalizing:
- No unresolved ambiguity remains in required behavior.
- All features map to user stories.
- All user stories map to acceptance tests.
- Acceptance tests are integration-test-ready and executable against API + Postgres + Camunda.

## Clarification Rules

- Ask questions first when requirements are incomplete.
- Mark items as `Blocked` if the user refuses to decide and the item materially affects behavior.
- Separate `Confirmed` decisions from `Open Questions`.
- Never invent business rules to fill gaps.

## Stack-Specific Guidance

When the target is Spring Boot + JDBC + Postgres + Camunda 7, load and apply:
- `references/spring-jdbc-postgres-camunda-checklist.md`

Use that checklist to ensure the spec covers:
- API contract and HTTP semantics
- Transaction boundaries and consistency strategy
- Repository/query behavior and indexing concerns
- Camunda process definitions, variables, retries, and incidents
- Integration test scenarios across service + database + workflow runtime

## Output Requirements

Produce one markdown document with:
- Feature breakdown with scope boundaries
- Detailed user stories (`As <role>, I want <capability>, so that <outcome>`)
- Detailed acceptance tests suitable for integration tests
- Non-functional requirements (performance, reliability, security, observability)
- Edge cases and failure scenarios
- Explicit out-of-scope section

If ambiguity remains, include a top `Blocked Items` section and stop short of pretending the spec is complete.

Save the markdown document to `/specs/{feature-name}.md` and return the file path as output.
