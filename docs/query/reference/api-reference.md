# Reference: Query API

## Summary

`query-lib` provides query construction types. `app` provides execution and pagination plumbing
through `SearchExecutor` and `SearchCriteria`.

## Construction Types (`query-lib`)

- `SQLQueryBuilder`: fluent builder for SQL statement assembly
- `BuiltQuery`: immutable output (`sql`, `params`)
- `Operator`: supported WHERE operators
- `JoinType`: SQL join keywords
- `SqlDialect`: pagination syntax strategy (`ANSI`, `POSTGRES`)
- `SortWhitelist`: safe mapping from client sort keys to SQL expressions
- `WhereClause`: value object for grouped OR clauses
- `QueryLoggingHelper`: formatted query logging with redaction support

## Execution Types (`app`)

- `SearchExecutor`: executes paged searches from `SQLQueryBuilder` + `SearchCriteria`
- `SearchCriteria` (abstract): shared pagination contract for search criteria DTOs

## SQLQueryBuilder Key Methods

- `newBuilder()`
- `select(String... columns)` / `select(String selectClause)`
- `from(String table, String alias)`
- `join(JoinType type, String table, String alias, String onClause)`
- `with(String cteName, SQLQueryBuilder cteBuilder)`
- `where(String column, Operator operator, Object value)`
- `whereOr(WhereClause... clauses)`
- `whereInSubquery(String column, SQLQueryBuilder subqueryBuilder)`
- `whereNull(String column)` / `whereNotNull(String column)`
- `rawWhere(String fragment, Map<String, Object> params)`
- `groupBy(String... expressions)`
- `orderBy(String token)`
- `safeOrderBy(String token, SortWhitelist whitelist)`
- `limit(Integer limit)` / `offset(Integer offset)` / `page(Integer page, Integer size)`
- `dialect(SqlDialect dialect)`
- `build()`
- `buildCount()`
- `buildWithTotalCountWindow(String alias)`

## QueryLoggingHelper

Method:

- `format(String queryId, BuiltQuery query, Set<String> redactKeys)`

Output shape includes:

- `queryId=<id>`
- raw SQL text
- parameter keys
- `redactedParams={...}` where configured keys are replaced with `***`

## SearchExecutor Methods

- `<T> execute(SQLQueryBuilder builder, SearchCriteria criteria, Class<T> rowType)`
- `<T> execute(SQLQueryBuilder builder, SearchCriteria criteria, RowMapper<T> rowMapper)`

Execution strategy:

- Uses window-count strategy when the built query supports it.
- Falls back to lookahead (`size + 1`) plus conditional count query.
- Emits debug logs with query IDs: `search.data.window`, `search.data.lookahead`, `search.count`.

## SearchCriteria Fields and Defaults

- `Integer page` with validation `@Min(0)`; default from getter: `0` when null
- `Integer size` with validation `@Min(1)` and `@Max(100)`; default from getter: `25` when null

## Parameter Behavior

- Generated params are named sequentially (`p1`, `p2`, ...).
- Enum values are normalized to persisted string names.
- `null` values in `where(...)` are omitted.
- Empty collections for `Operator.IN` are omitted.
- CTE/subquery/raw fragment params are rewritten to avoid collisions.

## Validation and Errors

- Missing/blank aliases in `from(...)` or `join(...)` -> `IllegalArgumentException`
- Missing `SELECT` or `FROM` at build time -> `IllegalStateException`
- Negative limit/offset or invalid page/size -> `IllegalArgumentException`
- Unsafe `rawWhere(...)` fragments (e.g., delimiters/comments) -> `IllegalArgumentException`
- Unknown `safeOrderBy(...)` field -> `IllegalArgumentException`
- Invalid `SearchCriteria.page` / `size` values fail validation at API layer
