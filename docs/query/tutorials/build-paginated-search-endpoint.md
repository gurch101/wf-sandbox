# Tutorial: Build a Paginated Search Endpoint with SearchExecutor

## Outcome

Implement an endpoint-level search flow that combines:

- a `SearchCriteria` subclass for pagination + filters,
- an `SQLQueryBuilder` for safe SQL assembly,
- and `SearchExecutor` for consistent paged execution.

## Prerequisites

- Java 25 toolchain (project default)
- Familiarity with Spring service patterns in `backend/app`
- Basic understanding of `SQLQueryBuilder`

## Step 1: Define a criteria type

Create a criteria class that extends `SearchCriteria` so you inherit page/size defaults and
validation.

```java
@Getter
@Setter
public class RequestSummarySearchCriteria extends SearchCriteria {
  private List<String> statuses;
  private String sort;
}
```

`SearchCriteria` defaults when unset:

- `page = 0`
- `size = 25`

## Step 2: Build the query in your service

```java
SortWhitelist whitelist =
    SortWhitelist.create()
        .allow("createdAt", "r.created_at")
        .allow("status", "r.status");

SQLQueryBuilder builder =
    SQLQueryBuilder.newBuilder().select("r.id", "r.status", "r.created_at AS createdAt")
        .from("requests", "r")
        .where("r.status", Operator.IN, criteria.getStatuses())
        .safeOrderBy(criteria.getSort(), whitelist);
```

Notes:

- `where(..., IN, null)` and empty collections are omitted automatically.
- `safeOrderBy(...)` rejects unknown fields.

## Step 3: Execute with SearchExecutor

```java
PagedResponse<RequestSummaryRow> response =
    searchExecutor.execute(builder, criteria, RequestSummaryRow.class);
```

`SearchExecutor` handles pagination strategy:

- Uses window-count mode when supported by query shape.
- Uses lookahead mode (`size + 1`) plus count when required.

## Step 4: Expose through your API

Return the `PagedResponse<T>` directly from the service/controller layer.

```java
@GetMapping("/api/requests/search")
public PagedResponse<RequestSummaryRow> search(RequestSummarySearchCriteria criteria) {
  return requestSearchService.search(criteria);
}
```

## Verify

1. Call endpoint without `page`/`size` and confirm defaults are applied.
2. Call endpoint with invalid `size` (for example `0` or `101`) and confirm validation error.
3. Call endpoint with unknown sort token and confirm failure.
4. Call endpoint with filters and verify returned page and `totalElements` behavior.

## Next Steps

- For SQL construction basics, read [Build Your First Query](build-your-first-query.md).
- For concise implementation guidance, read [How-to: Build a Safe Search Query](../how-to/build-safe-search-query.md).
- For exact API details, read [Reference: Query API](../reference/api-reference.md).
