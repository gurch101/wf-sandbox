# SQLQueryBuilder Specification

## 1. Summary

- Problem: Repository code needs dynamic SQL composition for complex queries without scattered conditional logic and without sacrificing `NamedParameterJdbcTemplate` safety.
- Business objective: Provide a Java-only `SQLQueryBuilder` that deterministically builds SQL + named parameters for complex, composable query generation.
- In scope:
  - Fluent builder that returns `BuiltQuery` via `build()`.
  - SQL constructs: `SELECT`, `FROM`, `JOIN`, `WHERE` (`AND` + grouped `OR`), `GROUP BY`, `ORDER BY`, `LIMIT`, `OFFSET`, subqueries, and multiple CTEs.
  - Null-skipping behavior for `where(...)`.
  - Explicit null predicates via `whereNull(...)` and `whereNotNull(...)`.
  - Deterministic/stable parameter naming across identical builder calls.
  - Alias enforcement for tables in `FROM` and `JOIN`.
  - Escape hatch for raw where fragments.
  - Pagination helper and safe sort whitelist helper.
  - Query logging helper with parameter redaction support.
  - Add and use the `requests` table definition from `specs/camunda.md` for SQLQueryBuilder integration tests.
- Out of scope:
  - Full SQL parser or semantic SQL validation beyond builder rules.
  - ORM/entity mapping behavior.
  - Automatic index creation/migration tooling.
  - Thread-safe builder implementation.

## 2. Confirmed Decisions

- Decision: Java-only implementation.
- Decision: Output contract is `build(): BuiltQuery` where `BuiltQuery` contains SQL string and named parameter map.
- Decision: Primary design is DB-agnostic SQL composition with optional PostgreSQL-compatible semantics where needed.
- Decision: `where(column, operator, value)` omits clause when `value == null`.
- Decision: Explicit null checks are via `whereNull(column)` and `whereNotNull(column)`.
- Decision: `whereOr(WhereClause...)` forms `(c1 OR c2 ...)` groups and each group is `AND`-combined with other predicates.
- Decision: If every clause in a `whereOr` group is omitted/null-equivalent, the full OR group is omitted.
- Decision: Table aliases are mandatory for `FROM` and `JOIN` sources.
- Decision: Subqueries/CTEs are builder-composed (not string-only) and support multiple CTEs.
- Decision: Parameter names must be deterministic and stable for identical builder call sequences.
- Decision: Builder is mutable and not thread-safe.
- Decision: Validation on invalid builder state is required and fails fast.
- Decision: Provide raw where escape hatch.
- Decision: Include pagination helper, safe sort whitelist helper, and query logging helper.

## 3. Blocked Items (Must Resolve Before Build)

- None.

## 4. Feature Breakdown

### 4.1 Core Builder and Query Output

- Description: Implement fluent `SQLQueryBuilder` that accumulates query parts and emits immutable `BuiltQuery`.
- API impact:
  - `SQLQueryBuilder.select(...)`
  - `from(table, alias)`
  - `join(type, table, alias, onClause)`
  - `where(column, operator, value)`
  - `whereOr(WhereClause... clauses)`
  - `whereNull(column)`, `whereNotNull(column)`
  - `groupBy(...)`, `orderBy(...)`, `limit(int)`, `offset(int)`
  - `with(cteName, cteBuilder)` for CTEs
  - subquery methods (e.g., `whereInSubquery(column, subBuilder)`)
  - `rawWhere(sqlFragment, params)` escape hatch
  - `page(pageNumber, pageSize)`
  - `safeOrderBy(requestedSort, SortWhitelist whitelist)`
  - `build(): BuiltQuery`
- Data impact: None (library-level only).
- Workflow impact: None (Camunda not used).
- Security impact: Parameterized execution by default, raw fragment restricted by validation.
- Observability impact: Query logger outputs structured SQL + redacted params.

### 4.2 Deterministic Parameterization

- Description: Ensure stable parameter naming and ordering for repeatable tests and diagnostics.
- API impact: `BuiltQuery.params()` map preserves insertion order; naming strategy is deterministic.
- Data impact: None.
- Workflow impact: None.
- Security impact: Prevent value interpolation in generated SQL paths.
- Observability impact: Deterministic logs simplify diffing and incident analysis.

### 4.3 Validation and Guardrails

- Description: Enforce minimum valid SQL structure and safe helper behavior.
- API impact:
  - `build()` throws `IllegalStateException` for invalid state.
  - `IllegalArgumentException` for bad API inputs (blank aliases, invalid pagination values, unknown sort fields).
- Data impact: None.
- Workflow impact: None.
- Security impact: Safe-sort whitelist and alias enforcement reduce injection/misuse risk.
- Observability impact: Validation failures logged with reason and builder state metadata.

## 5. User Stories

### US-001: Compose Dynamic Queries Without If-Chains

- As a `repository developer`
- I want `where(...)` to ignore null values
- So that I can chain filters without conditional branching in Java code
- Priority: `P0`

### US-002: Build Complex SQL Shapes

- As a `repository developer`
- I want joins, grouped OR conditions, subqueries, and multiple CTEs
- So that advanced retrieval logic stays in a reusable, typed builder API
- Priority: `P0`

### US-003: Execute Safely with Named Parameters

- As a `backend engineer`
- I want generated SQL and parameter maps compatible with `NamedParameterJdbcTemplate`
- So that queries remain parameterized and safe by default
- Priority: `P0`

### US-004: Guarantee Predictable Query Output

- As a `test engineer`
- I want deterministic parameter names and output ordering
- So that integration and snapshot tests are stable
- Priority: `P1`

### US-005: Apply Controlled Sorting and Pagination

- As an `API developer`
- I want pagination helpers and sort-field whitelisting
- So that list endpoints can safely expose user-driven sorting/paging
- Priority: `P1`

### US-006: Diagnose Query Behavior in Production

- As an `operator`
- I want query logging with redaction
- So that I can troubleshoot failures without exposing sensitive values
- Priority: `P2`

## 6. API Specification

### Public Java API (Library)

- Purpose: Build SQL + parameters for repository execution.
- Auth: N/A (in-process library).
- Request schema: Fluent method calls.
- Response schema: `BuiltQuery` object.
- Errors: Throws runtime validation exceptions with actionable messages.
- Idempotency: `build()` is deterministic for identical builder call sequences.

### Required Types

- `final class BuiltQuery`
  - `String sql()`
  - `Map<String, Object> params()`
- `final class WhereClause`
  - `static WhereClause create(String column, Operator operator, Object value)`
- `enum Operator`
  - Includes common operators: `=`, `!=`, `>`, `>=`, `<`, `<=`, `LIKE`, `IN`, `BETWEEN`, plus extensible operator token support.
- `final class SortWhitelist`
  - Maps client-visible sort keys to SQL-safe column expressions.

### Behavioral Contract

- `where(column, operator, null)` omits predicate.
- `whereOr(...)`:
  - Each non-omitted child clause is OR-composed inside parentheses.
  - Whole group is AND-composed with outer predicates.
  - Empty effective group is omitted.
- `whereNull(column)` and `whereNotNull(column)` always emit `IS NULL` / `IS NOT NULL`.
- Aliases required for `from` and `join`; missing/blank alias is invalid.
- `page(page, size)` derives `LIMIT size OFFSET page*size` (0-based page index).
- `safeOrderBy` rejects non-whitelisted sort keys.
- `rawWhere(fragment, params)`:
  - Allowed for advanced edge cases.
  - Fragment must not contain statement delimiters (`;`) or comment tokens (`--`, `/*`) to reduce abuse.
  - Provided params merged with deterministic collision-safe naming.

## 7. Data Model and Persistence

- Tables/entities affected:
  - `requests` (required integration-test backing table; schema must match `specs/camunda.md`).
- `requests` columns:
  - `id GENERATED ALWAYS AS IDENTITY PK`
  - `name VARCHAR(200) NOT NULL`
  - `status VARCHAR(32) NOT NULL` (`DRAFT|IN_PROGRESS|COMPLETED|REJECTED`)
  - `process_instance_id VARCHAR(64) NULL`
  - `created_at TIMESTAMPTZ NOT NULL`
  - `updated_at TIMESTAMPTZ NOT NULL`
  - `version BIGINT NOT NULL`
- New columns/constraints/indexes:
  - Index on `requests(status)`.
  - Index on `upper(requests.name)` for case-insensitive search scenarios.
  - Composite index on `(status, created_at desc)` for pagination/sort scenarios.
- Transaction boundaries: Managed by caller (`@Transactional` service/repository methods).
- Concurrency strategy:
  - Builder instances are mutable and not thread-safe.
  - `BuiltQuery` is immutable and safe to share.

## 8. Camunda 7 Workflow Specification

- Process key/name: N/A.
- Trigger: N/A.
- Variables: N/A.
- Service tasks and retry strategy: N/A.
- Incidents/escalations: N/A.
- Compensation/cancellation behavior: N/A.

## 9. Non-Functional Requirements

- Performance targets:
  - Build time for query with up to 100 predicates and 10 joins: p95 <= 5 ms in local integration tests.
  - Avoid superlinear operations in predicate assembly.
- Reliability/SLA expectations:
  - Deterministic output for same call sequence across JVM runs.
  - Fail-fast validation for invalid states.
- Security controls:
  - Parameterized value binding by default.
  - Safe sort whitelist mandatory for user-driven sort paths.
  - Raw fragments constrained and explicitly opt-in.
- Observability (logs/metrics/traces):
  - Query logger emits structured fields: `queryId`, `sql`, `paramKeys`, `redactedParams`, duration (if measured by caller).
  - Redaction policy supports masking selected parameter keys.

## 10. Acceptance Tests (Integration)

Global test fixture requirement:
- Apply migration/DDL that creates the `requests` table exactly as defined in `specs/camunda.md` before running integration tests.

### AT-001: Null-Skipping AND Predicates

- Covers user story: US-001, US-003.
- Preconditions:
  - Postgres test DB with sample `requests r` rows.
- Test steps:
  1. Build query with `.where("r.status", EQ, "IN_PROGRESS")` and `.where("r.process_instance_id", EQ, null)`.
  2. Execute with `NamedParameterJdbcTemplate`.
- Expected API result:
  - SQL contains only `r.status = :...` in `WHERE`; no `process_instance_id` predicate from the null-valued `where`.
- Expected DB state:
  - Read-only; unchanged.
- Expected Camunda state:
  - N/A.

### AT-002: Grouped OR Combined With AND

- Covers user story: US-002.
- Preconditions:
  - Seeded `requests r` table with mixed `status` and `name` values.
- Test steps:
  1. Add `where("r.version", GT, 0)`.
  2. Add `whereOr(create("r.status", EQ, "IN_PROGRESS"), create("r.status", EQ, "COMPLETED"))`.
  3. Build and execute.
- Expected API result:
  - `WHERE r.version > :... AND (r.status = :... OR r.status = :...)`.
- Expected DB state:
  - Read-only; unchanged.
- Expected Camunda state:
  - N/A.

### AT-003: Empty OR Group Omitted

- Covers user story: US-001, US-002.
- Preconditions:
  - Same seeded data.
- Test steps:
  1. Call `whereOr(create("r.status", EQ, null), create("r.name", LIKE, null))`.
  2. Build with one valid `where` predicate.
- Expected API result:
  - No empty parentheses; OR group absent.
- Expected DB state:
  - Read-only.
- Expected Camunda state:
  - N/A.

### AT-004: Explicit Null Predicates

- Covers user story: US-001.
- Preconditions:
  - `requests` rows where `process_instance_id` is both null and non-null.
- Test steps:
  1. Use `whereNull("r.process_instance_id")`.
  2. Execute query.
- Expected API result:
  - SQL includes `r.process_instance_id IS NULL`.
- Expected DB state:
  - Read-only.
- Expected Camunda state:
  - N/A.

### AT-005: Multiple CTE Composition

- Covers user story: US-002.
- Preconditions:
  - Seeded tables for aggregation.
- Test steps:
  1. Compose two dependent CTE builders via `with("base", ...)` and `with("agg", ...)`.
  2. Build final select from CTE alias.
- Expected API result:
  - SQL starts with `WITH base AS (...), agg AS (...)` preserving declared order.
- Expected DB state:
  - Read-only.
- Expected Camunda state:
  - N/A.

### AT-006: Alias Enforcement Validation

- Covers user story: US-002.
- Preconditions:
  - None.
- Test steps:
  1. Invoke `from("requests", "")` then `build()`.
- Expected API result:
  - Throws `IllegalArgumentException` with alias-related message.
- Expected DB state:
  - N/A.
- Expected Camunda state:
  - N/A.

### AT-007: Deterministic Parameter Names

- Covers user story: US-004.
- Preconditions:
  - None.
- Test steps:
  1. Build two queries with identical call order and values.
  2. Compare SQL text and param key order.
- Expected API result:
  - Exact equality of SQL and parameter key sequence.
- Expected DB state:
  - N/A.
- Expected Camunda state:
  - N/A.

### AT-008: Pagination Helper

- Covers user story: US-005.
- Preconditions:
  - Table with >50 rows.
- Test steps:
  1. Call `page(2, 10)` with deterministic `orderBy`.
  2. Execute.
- Expected API result:
  - SQL contains `LIMIT 10 OFFSET 20`.
- Expected DB state:
  - Read-only.
- Expected Camunda state:
  - N/A.

### AT-009: Safe Sort Whitelist Rejection

- Covers user story: US-005.
- Preconditions:
  - Whitelist contains only `createdAt`, `status`.
- Test steps:
  1. Request sort key `dropTable` via `safeOrderBy`.
- Expected API result:
  - Throws `IllegalArgumentException` (unknown sort key).
- Expected DB state:
  - N/A.
- Expected Camunda state:
  - N/A.

### AT-010: Raw Where Escape Hatch

- Covers user story: US-002, US-003.
- Preconditions:
  - None.
- Test steps:
  1. Add `rawWhere("upper(r.name) like :namePattern", Map.of("namePattern", "%app%"))`.
  2. Build and execute against Postgres test DB.
- Expected API result:
  - Raw fragment present; params merged without collisions.
- Expected DB state:
  - Read-only.
- Expected Camunda state:
  - N/A.

### AT-011: Query Logging With Redaction

- Covers user story: US-006.
- Preconditions:
  - Logger test appender configured.
- Test steps:
  1. Build query containing sensitive param key (e.g., `email`).
  2. Log through query logging helper with `email` redacted.
- Expected API result:
  - Log contains SQL and masked email value.
- Expected DB state:
  - N/A.
- Expected Camunda state:
  - N/A.

## 11. Edge Cases and Failure Scenarios

- Case: `build()` called without `FROM`.
- Expected behavior: throw `IllegalStateException` describing missing mandatory clause.

- Case: `IN` operator with empty collection.
- Expected behavior: throw `IllegalArgumentException` (invalid SQL shape) unless caller uses explicit raw where.

- Case: Duplicate CTE names.
- Expected behavior: throw `IllegalArgumentException`.

- Case: Negative `limit`, `offset`, `page`, or non-positive `pageSize`.
- Expected behavior: throw `IllegalArgumentException`.

- Case: `where` with blank column or unsupported operator token.
- Expected behavior: throw `IllegalArgumentException`.

- Case: Raw where contains disallowed tokens (`;`, comment markers).
- Expected behavior: reject with `IllegalArgumentException`.

- Case: Subquery builder invalid (missing FROM).
- Expected behavior: propagate subquery validation exception during parent `build()`.

## 12. Adjacent Feature Recommendations

- Recommendation: Validation + structured exceptions with error codes.
- Reason: Improves client handling and test assertions.
- Include now? `yes`

- Recommendation: Deterministic SQL and parameter ordering contract tests.
- Reason: Prevents regressions in diagnostics and snapshot tests.
- Include now? `yes`

- Recommendation: Pagination helper abstraction.
- Reason: Reduces endpoint-level duplication.
- Include now? `yes`

- Recommendation: Safe sort whitelist helper.
- Reason: Prevents dynamic order-by misuse.
- Include now? `yes`

- Recommendation: Query logging helper with pluggable redaction.
- Reason: Balances operability and data protection.
- Include now? `yes`

- Recommendation: Repository migration examples from handwritten SQL.
- Reason: Accelerates adoption and de-risks rollout.
- Include now? `no`
