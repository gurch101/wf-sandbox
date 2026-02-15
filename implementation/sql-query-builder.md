# SQL Query Builder Implementation Notes

## Summary

Implemented a Java-first SQL query builder for Spring Data JDBC / `NamedParameterJdbcTemplate` usage with deterministic SQL and named parameter output. The implementation includes integration-first tests using Spring Boot + Testcontainers and a Flyway-backed `requests` table fixture.

## Implemented Components

- `SQLQueryBuilder`
- `BuiltQuery`
- `Operator`
- `WhereClause`
- `JoinType`
- `SortWhitelist`
- `QueryLoggingHelper`
- `internal/ParameterRewriter`
- Flyway migration: `V1__create_requests_table.sql`
- Integration test base: `AbstractJdbcIntegrationTest`
- Query + logging tests:
  - `SqlQueryBuilderTest`
  - `QueryLoggingHelperTest`

## Implemented Behavior

- Core query construction:
  - `select(...)` (full clause string and varargs columns)
  - `from(table, alias)` with alias validation
  - `join(type, table, alias, onClause)` with alias validation
  - `where(...)` with null-skipping
  - `whereOr(...)` grouped OR under AND
  - `whereNull(...)` / `whereNotNull(...)`
  - `whereInSubquery(...)`
  - `with(name, cteBuilder)` with multi-CTE support and duplicate-name rejection
  - `groupBy(...)`
  - `orderBy(...)`
  - `limit(...)`, `offset(...)`, `page(page, size)`
  - `rawWhere(fragment, params)` with token guard and parameter rewrite
  - `safeOrderBy(sortToken, whitelist)` with signed token parsing

- Determinism:
  - Stable `pN` parameter naming by call sequence
  - Deterministic SQL and parameter key order validated by tests

- Logging helper:
  - Query log formatting with selected param-key redaction

## Important Design Decisions

- Mutable builder + immutable output:
  - `SQLQueryBuilder` is mutable and chainable.
  - `BuiltQuery` defensively copies parameter map and exposes unmodifiable view.

- Internalized parameter remapping:
  - Extracted placeholder rewrite and collision-safe merge into `internal/ParameterRewriter`.
  - Reused by CTE composition, subquery embedding, and raw-where remapping.

- Signed sort tokens:
  - Removed explicit sort direction enum from public sort APIs.
  - Direction inferred via token prefix:
    - `-field` => `DESC`
    - `+field` / `field` => `ASC`

## Trade-offs

- Builder currently stores rendered clause fragments (`String`) rather than typed clause objects.
  - Faster to implement and easy to inspect in tests.
  - Less strict than a typed AST-style internal model.

- Query logging helper currently returns a formatted string.
  - Simple and testable.
  - Not yet integrated with structured logger bindings or MDC conventions.

- Validation is mostly localized to builder methods.
  - Works well for current scope.
  - Could be centralized for consistency as feature surface grows.

## Changes From Original Spec

- `safeOrderBy` signature differs from original draft:
  - Implemented as `safeOrderBy(sortToken, whitelist)` where `sortToken` supports `-field`, `+field`, `field`.
  - This replaced explicit direction parameter per implementation direction.

- `where(..., Operator.IN, emptyCollection)` behavior differs:
  - Implemented as clause omission (same behavior class as null-skipping), not exception.

- `orderBy(...)` similarly uses signed token direction parsing (no `SortDirection` enum).

## Future Considerations / Nice-to-Haves

- Move more non-API internals into `query.internal` (rendering/validation helpers).
- Introduce typed clause/value objects to reduce stringly-typed internals.
- Add stricter raw SQL guardrails (broader token validation policy).
- Add explicit benchmark/perf test for large predicate/join query assembly.
- Add structured logging object + logger integration for production diagnostics.
- Add module-level API exposure constraints for Spring Modulith to formalize public vs internal classes.
