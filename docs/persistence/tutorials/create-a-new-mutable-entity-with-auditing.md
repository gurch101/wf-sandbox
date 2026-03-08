# Tutorial: Create a New Mutable Entity with Auditing

## Outcome

Create a new entity that tracks create/update audit fields and optimistic-lock versioning using
`MutableEntity<ID>`.

## Prerequisites

- Spring Data JDBC entity in `backend/app`
- Backing table with audit/version columns
- Authenticated principal available (for `@CreatedBy`/`@LastModifiedBy`)

## Step 1: Extend MutableEntity

```java
@Table("widgets")
@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class WidgetEntity extends MutableEntity<Long> {
  private String name;
  private boolean active;
}
```

## Step 2: Add table columns

Add columns expected by `MutableEntity` + `BaseEntity`:

- `id`
- `created_by`
- `created_at`
- `updated_by`
- `updated_at`
- `version`

## Step 3: Save and update through repository

```java
WidgetEntity created = repository.save(WidgetEntity.builder().name("alpha").active(true).build());
WidgetEntity updated = repository.save(created.toBuilder().name("beta").version(created.getVersion()).build());
```

Spring Data JDBC auditing fills audit fields automatically; optimistic locking uses `version`.

## Verify

- `createdBy` and `createdAt` are set on initial insert.
- `updatedBy`, `updatedAt`, and `version` change on update.
- Concurrent stale updates fail with optimistic locking conflict.

## Next Steps

- For selection rules, see [Choose Between BaseEntity and MutableEntity](../how-to/choose-between-baseentity-and-mutableentity.md).
- For runtime population details, see [Persistence Reference](../reference/persistence-reference.md).
