# How-to: Enable Query Logging with Redaction

## Goal

Log generated SQL and parameters for debugging while redacting sensitive values.

## Prerequisites

- SLF4J logging available in your service/component
- A `BuiltQuery` produced by `SQLQueryBuilder`
- A set of sensitive parameter keys to redact

## Procedure

1. Build the query as normal.
2. Define sensitive parameter keys.
3. Format the log line with `QueryLoggingHelper.format(...)`.
4. Emit the message at debug level.

```java
BuiltQuery query =
    SQLQueryBuilder.newBuilder().select("u.id", "u.email")
        .from("users", "u")
        .where("u.email", Operator.EQ, email)
        .build();

Set<String> redactKeys = Set.of("p1");
log.debug(QueryLoggingHelper.format("users.lookup", query, redactKeys));
```

## Validation

Check the log output contains:

- `queryId=<your-id>`
- full SQL text
- `paramKeys=[...]`
- redacted values as `***` for configured keys

## Troubleshooting

- Sensitive value still appears:
  - Ensure the actual generated parameter key is included in `redactKeys`.
- No logs emitted:
  - Confirm logger level includes `DEBUG` for the relevant package/class.
- Logs too noisy:
  - Use stable query IDs and only log expensive or high-risk queries.

## Notes for SearchExecutor

`SearchExecutor` already logs its internal query phases:

- `search.data.window`
- `search.data.lookahead`
- `search.count`

When extending `SearchExecutor`, keep the same pattern to preserve observability consistency.
