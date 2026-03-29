# How-to: Build a Safe Search Query with Filtering, Sorting, and Pagination

## Goal

Create a dynamic query flow that supports optional filters, validated sort input, paging, and
consistent `PagedResponse` execution via `SearchExecutor`.

## Prerequisites

- A table alias strategy (examples use `r`)
- A `SortWhitelist` for all client-sortable fields
- Incoming search parameters (status, sort, page, size)

## Procedure

1. Start with `SELECT` + `FROM`.
2. Add optional filters using `where(...)`.
3. Add whitelisted sort with `safeOrderBy(...)`.
4. Add pagination with `page(page, size)`.
5. Execute through `SearchExecutor` with a `SearchCriteria` subclass.

```java
SortWhitelist whitelist =
    SortWhitelist.create()
        .allow("createdAt", "r.created_at")
        .allow("status", "r.status");

BuiltQuery query =
    SQLQueryBuilder.newBuilder().select("r.id", "r.status", "r.created_at")
        .from("requests", "r")
        .where("r.status", Operator.IN, statuses)
        .safeOrderBy(sortToken, whitelist)
        .page(page, size)
        .build();
```

```java
public class RequestSearchCriteria extends SearchCriteria {
  // module-specific filters
}

PagedResponse<RequestSearchRow> pageResult =
    searchExecutor.execute(
        SQLQueryBuilder.newBuilder().select("r.id", "r.status", "r.created_at")
            .from("requests", "r")
            .where("r.status", Operator.IN, statuses)
            .safeOrderBy(sortToken, whitelist),
        criteria,
        RequestSearchRow.class);
```

## Validation

Check:

- Unknown sort keys fail fast (`IllegalArgumentException`)
- Null filters are skipped automatically
- Empty `IN` collections are skipped automatically
- Params are named (`:p1`, `:p2`, ...) and separated from SQL
- Default paging comes from `SearchCriteria` (`page=0`, `size=25`) when unset

## Troubleshooting

- Error: `FROM clause is required before build()`
  - Ensure `.from(table, alias)` is set before `.build()`.
- Error: invalid sort token
  - Add the field to `SortWhitelist` and use field key, not raw SQL.
- Unexpected pagination syntax
  - Switch dialect with `.dialect(SqlDialect.POSTGRES)` if needed.
