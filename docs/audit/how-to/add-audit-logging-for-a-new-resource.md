# How-to: Add Audit Logging for a New Resource

## Goal

Integrate audit logging for a new writable resource so create/update/delete operations are captured
consistently.

## Prerequisites

- Resource service with transactional create/update/delete methods
- Stable `resource_type` identifier string
- Entity/DTO object suitable for JSON snapshotting

## Procedure

1. Add `AuditLogApi` dependency to the service.
2. Define a stable `RESOURCE_TYPE` constant (for example `"widgets"`).
3. On create, call `recordCreate(...)` after successful save.
4. On update, keep `beforeState`, persist update, call `recordUpdate(...)`.
5. On delete, load existing state, delete it, call `recordDelete(...)`.
6. Keep audit writes in the same transaction as the domain write.

## Correlation ID Guidance

- If a higher-level orchestrator already has a correlation id, pass it explicitly with the
  overloads that accept `correlationId`.
- Otherwise, use the 3-arg methods; service-level resolver fallback will populate correlation id
  from request context when available.

## Implementation Checklist

- [ ] `resource_type` constant added and reused consistently
- [ ] create/update/delete all emit audit events
- [ ] update event uses real before/after snapshots
- [ ] delete path throws `NotFoundException` on missing resource (if following strict REST)
- [ ] integration tests assert action sequence and key fields

## Test Pattern

Use integration tests to assert action order for a resource id:

```java
assertThat(auditActionsFor("widgets", widgetId.toString()))
    .containsExactly("DELETE", "UPDATE", "CREATE");
```

## Troubleshooting

- Missing update audit rows:
  - Ensure before/after states are actually different; unchanged updates can produce no diff.
- Incorrect actor/tenant values:
  - Ensure security principal context is available (`CurrentUserProvider`).
- Missing correlation ids:
  - Pass explicit correlation id from orchestrator, or ensure request context has one.
