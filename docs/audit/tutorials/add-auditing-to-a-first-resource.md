# Tutorial: Add Auditing to a First Resource

## Outcome

Add create/update/delete audit events to a service that manages a writable resource.

## Prerequisites

- Service class with create/update/delete operations
- Access to post-persistence state objects
- `AuditLogApi` available for injection

## Step 1: Inject AuditLogApi

```java
@RequiredArgsConstructor
public class DefaultWidgetService {
  private static final String WIDGETS_RESOURCE_TYPE = "widgets";

  private final WidgetRepository repository;
  private final AuditLogApi auditLogApi;
}
```

## Step 2: Record create after persistence

```java
WidgetEntity created = repository.save(newEntity);
auditLogApi.recordCreate(WIDGETS_RESOURCE_TYPE, created.getId(), created);
```

## Step 3: Record update with before/after snapshots

```java
WidgetEntity beforeState = existing;
WidgetEntity updated = repository.save(existing.toBuilder().name(command.getName()).build());
auditLogApi.recordUpdate(WIDGETS_RESOURCE_TYPE, updated.getId(), beforeState, updated);
```

## Step 4: Record delete with pre-delete snapshot

```java
WidgetEntity existing =
    repository.findById(id).orElseThrow(() -> new NotFoundException("Widget not found"));
repository.delete(existing);
auditLogApi.recordDelete(WIDGETS_RESOURCE_TYPE, id, existing);
```

## Verify

- Create emits `CREATE`
- Update emits `UPDATE` (changed-field diff)
- Delete emits `DELETE`
- `resource_type` and `resource_id` values are correct

## Next Steps

- Use the full checklist in [How-to: Add Audit Logging for a New Resource](../how-to/add-audit-logging-for-a-new-resource.md).
- See exact schema and action rules in [Audit Logging Reference](../reference/audit-logging-reference.md).
