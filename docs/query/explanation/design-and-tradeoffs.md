# Explanation: Query Design and Tradeoffs

## Context

The query stack exists to standardize both SQL construction and paged execution while reducing
injection risk and query-assembly bugs.

## Core Design

- `SQLQueryBuilder` composes SQL in logical clauses.
- SQL text and params are produced separately via `BuiltQuery`.
- Param names are deterministic and collision-safe through rewriting.
- Safe sorting is explicit via `SortWhitelist` instead of raw sort expressions.
- `SearchExecutor` centralizes paging execution strategy and total-count behavior.
- `SearchCriteria` gives all search DTOs a shared pagination contract.
- `QueryLoggingHelper` standardizes query observability with opt-in parameter redaction.

## Why This Shape

- Keep SQL visibility high: generated SQL remains readable and debuggable.
- Avoid ORM complexity for custom search/read paths.
- Keep query construction reusable (`query-lib`) while letting execution stay app-aware (`app`).

## Tradeoffs

- Pros:
  - Safer dynamic query construction with named parameters.
  - Consistent pagination and sorting patterns.
  - Reusable across modules with minimal dependencies.
  - Uniform paging behavior across endpoints via `SearchExecutor`.
  - Consistent debug log format for data/count query phases.
- Cons:
  - Not a full SQL AST or ORM; some raw SQL responsibility remains.
  - Caller must supply table/column strings correctly.
  - Advanced database-specific behavior remains opt-in via dialect and raw fragments.
  - Query execution strategy (window vs lookahead) is abstracted, so SQL cost profile is less
    explicit at call sites.
  - Redaction depends on correctly choosing parameter keys for sensitive values.

## Alternatives Considered

- Writing raw SQL per feature: simpler initially, but repeated safety/consistency issues.
- Full ORM criteria builders: richer abstractions, but less explicit SQL control and higher complexity for custom queries.

## Relationship to Other Docs

- Learn usage sequence: [Tutorial](../tutorials/build-your-first-query.md)
- Solve concrete implementation tasks: [How-to](../how-to/build-safe-search-query.md)
- Look up exact APIs/constraints: [Reference](../reference/api-reference.md)
