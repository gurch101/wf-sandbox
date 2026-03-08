# How-to: Ensure Audit Fields Populate Correctly

## Goal

Ensure `createdBy`, `createdAt`, `updatedBy`, and `updatedAt` are populated automatically for
entities extending `BaseEntity`/`MutableEntity`.

## Prerequisites

- `PersistenceConfig` active with `@EnableJdbcAuditing`
- Entity extends the correct base class
- Table has matching columns

## Procedure

1. Keep `PersistenceConfig` wiring enabled:
   - `@EnableJdbcAuditing(auditorAwareRef = "jdbcAuditorAware", dateTimeProviderRef = "jdbcAuditingDateTimeProvider")`
2. Ensure `CurrentUserProvider.currentUserId()` resolves a user id from security context.
3. Save entities through Spring Data JDBC repositories.
4. In tests, provide principal context (for example `@WithMockUser(username = "1")`).

## Important Behavior

- Timestamps come from `Clock.systemUTC()` via `DateTimeProvider`.
- Auditor id comes from `CurrentUserProvider` through `AuditorAware<Integer>`.
- If no current user id is available, auditor fields can remain null; this may violate NOT NULL DB
  constraints depending on table design.

## Validation

- Insert sets `createdBy`/`createdAt`.
- Update sets `updatedBy`/`updatedAt` and increments `version` for mutable entities.
- Serialization for JSONB fields works through configured converters.

## Troubleshooting

- `created_by` null constraint failures:
  - Ensure authenticated user context is present when persisting.
- `updated_at` not changing:
  - Confirm entity extends `MutableEntity` and update occurs via repository save.
- JSONB conversion errors:
  - Verify converters in `PersistenceConfig` are loaded and data is valid JSON.
