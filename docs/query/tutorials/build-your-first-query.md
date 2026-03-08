# Tutorial: Build Your First Query

## Outcome

Build a parameterized SQL query using `SQLQueryBuilder` and inspect the generated SQL + named params.

## Prerequisites

- Java 25 toolchain (project default)
- Access to the project source under `backend/query-lib`
- Familiarity with basic SQL (`SELECT`, `WHERE`, `ORDER BY`)

## Step 1: Create a simple builder chain

```java
BuiltQuery query =
    SQLQueryBuilder.select("r.id", "r.status")
        .from("requests", "r")
        .where("r.status", Operator.EQ, "IN_PROGRESS")
        .orderBy("-r.created_at")
        .page(0, 20)
        .build();
```

## Step 2: Inspect generated SQL and params

```java
System.out.println(query.sql());
System.out.println(query.params());
```

Expected shape:

- SQL contains `WHERE r.status = :p1`
- SQL contains descending `ORDER BY`
- SQL contains pagination clause (ANSI by default)
- Params map contains `p1=IN_PROGRESS`

## Step 3: Add safe dynamic sort

```java
SortWhitelist whitelist =
    SortWhitelist.create()
        .allow("createdAt", "r.created_at")
        .allow("status", "r.status");

BuiltQuery sorted =
    SQLQueryBuilder.select("r.id", "r.status")
        .from("requests", "r")
        .safeOrderBy("-createdAt", whitelist)
        .build();
```

This allows user-provided sort keys without exposing raw SQL expressions.

## Verify

Run query-lib tests:

```bash
cd backend
./gradlew :query-lib:test
```

## Next Steps

- For end-to-end app integration, see [Build a Paginated Search Endpoint with SearchExecutor](build-paginated-search-endpoint.md).
- For task-focused usage, see [How-to: Build a Safe Search Query](../how-to/build-safe-search-query.md).
- For full API details, see [API Reference](../reference/api-reference.md).
- For rationale and design tradeoffs, see [Design and Tradeoffs](../explanation/design-and-tradeoffs.md).
