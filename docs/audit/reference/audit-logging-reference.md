# Reference: Audit Logging

## Public API

`AuditLogApi` methods:

- `recordCreate(String resourceType, Object resourceId, Object afterState)`
- `recordCreate(String resourceType, Object resourceId, Object afterState, String correlationId)`
- `recordUpdate(String resourceType, Object resourceId, Object beforeState, Object afterState)`
- `recordUpdate(String resourceType, Object resourceId, Object beforeState, Object afterState, String correlationId)`
- `recordDelete(String resourceType, Object resourceId, Object beforeState)`
- `recordDelete(String resourceType, Object resourceId, Object beforeState, String correlationId)`

## Persisted Model

Table: `audit_log_events`

Core columns:

- `resource_type` (`VARCHAR(100)`, required)
- `resource_id` (`VARCHAR(100)`, required)
- `action` (`CREATE|UPDATE|DELETE`, required)
- `actor_user_id` (`INTEGER`, nullable)
- `tenant_id` (`INTEGER`, nullable)
- `correlation_id` (`VARCHAR(128)`, nullable)
- `before_state` (`JSONB`, nullable)
- `after_state` (`JSONB`, nullable)
- `created_by` (`INTEGER`, required)
- `created_at` (`TIMESTAMPTZ`, required)

## Action State Rules

Enforced by DB check constraints:

- `CREATE`: `before_state IS NULL`, `after_state IS NOT NULL`
- `UPDATE`: `before_state IS NOT NULL`, `after_state IS NOT NULL`
- `DELETE`: `before_state IS NOT NULL`, `after_state IS NULL`

## Update Diff Behavior

`recordUpdate(...)` computes a changed-field diff before persisting. If there are no effective
changes, no update audit event is written.

## Actor and Tenant Resolution

- `actor_user_id` comes from `CurrentUserProvider.currentUserId()`
- `tenant_id` comes from `CurrentUserProvider.currentTenantId()`
- `created_by` falls back to system user id `1` when no authenticated actor exists

## Correlation Resolution

Stored correlation id is resolved through `CorrelationIdResolver`, optionally using an explicit
`correlationId` passed to API overloads.
