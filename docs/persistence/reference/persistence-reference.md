# Reference: Persistence Package

## Package Overview

`backend/app/src/main/java/com/gurch/sandbox/persistence`

Core classes:

- `BaseEntity<ID>`
- `MutableEntity<ID>`
- `PersistenceExceptionUtils`
- `persistence.internal.PersistenceConfig`

## BaseEntity<ID>

Fields:

- `@Id ID id`
- `@CreatedBy Integer createdBy`
- `@CreatedDate Instant createdAt`

Use for insert-only/supporting records where last-modified tracking is unnecessary.

## MutableEntity<ID>

Extends `BaseEntity<ID>` and adds:

- `@LastModifiedBy Integer updatedBy`
- `@LastModifiedDate Instant updatedAt`
- `@Version Long version`

Use for mutable aggregates requiring optimistic locking and update audit fields.

## Auditing Configuration

Configured in `PersistenceConfig`:

- `@EnableJdbcAuditing`
- `jdbcAuditorAware()` -> `CurrentUserProvider::currentUserId`
- `jdbcAuditingDateTimeProvider()` -> UTC `Instant.now(...)`

## JSONB Converters

`JdbcCustomConversions` registers:

- `JsonNodeToPgObjectConverter` (writing)
- `PgObjectToJsonNodeConverter` (reading)

Used for JSONB columns mapped as Jackson `JsonNode`.

## Current Entity Usage Pattern

`BaseEntity` examples:

- `AuditLogEventEntity`
- `RequestActivityEventEntity`
- `IdempotencyRecordEntity`

`MutableEntity` examples:

- `RequestEntity`
- `RequestTaskEntity`
- `UserEntity`
- `TenantEntity`
- `DocumentTemplateEntity`
- `RequestTypeEntity`
- `RequestTypeVersionEntity`

## Exception Utility

`PersistenceExceptionUtils.fullMessage(Throwable)` flattens nested persistence exception messages,
including `DataIntegrityViolationException` causes.
