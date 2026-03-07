# Audit Log and Activity Timeline (v2)

## Scope implemented

- Added a reusable audit-log persistence model for create/update/delete tracking across resources.
- Wired request lifecycle operations to emit audit events.
- Expanded audit logging coverage to other writable resources:
  - `users`
  - `tenants`
  - `forms`
  - `request_types`
  - `request_type_versions` (create events for version snapshots)
- Added integration tests covering request draft create/update/delete audit writes.
- Populated `correlation_id` for request-driven audit writes.
- Switched `UPDATE` audit writes to changed-field diffs.
- Added domain-specific `request_activity_events` storage and `/api/requests/{id}/activity` endpoint.

## Data model

- New Flyway migration: `V9__create_audit_log_events_table.sql`
- New table: `audit_log_events`
  - Core identity: `id`
  - Generic resource keys: `resource_type`, `resource_id`
  - Operation: `action` (`CREATE|UPDATE|DELETE`)
  - Actor/context: `actor_user_id`, `tenant_id`, `correlation_id`, `reason`
  - State snapshots: `before_state`, `after_state`
  - Extensible metadata: `metadata` (`JSONB`, default `{}`)
  - Audit columns aligned with project conventions: `created_by`, `created_at`
- Constraints:
  - Valid action constraint.
  - Action-to-state-shape constraint (`CREATE` requires only `after`, `DELETE` only `before`, `UPDATE` both).
- Indexes:
  - `(resource_type, resource_id, created_at DESC)`
  - `(actor_user_id, created_at DESC)`
  - `(tenant_id, created_at DESC)`
  - Partial index on `correlation_id IS NOT NULL`
- New Flyway migration: `V10__create_request_activity_events_table.sql`
- New table: `request_activity_events`
  - `request_id`, `event_type`, `actor_user_id`, `correlation_id`, `payload`, `created_by`, `created_at`
  - Indexed by `(request_id, created_at DESC)` and `event_type`

## Application structure

- New public module API:
  - `com.gurch.sandbox.audit.AuditLogApi`
- New internal implementation:
  - `com.gurch.sandbox.audit.DefaultAuditLogService`
  - `com.gurch.sandbox.audit.internal.AuditLogEventEntity`
  - `com.gurch.sandbox.audit.internal.AuditLogEventRepository`
- New request activity internals:
  - `RequestActivityEventEntity`
  - `RequestActivityEventRepository`
  - `RequestActivityService`
  - `RequestActivityEventType`
- `requests` module now depends on `audit` public API (keeps Modulith boundaries valid).

## Request lifecycle integration

- `DefaultRequestService` now records audit events for:
  - `createDraft` -> `CREATE`
  - `updateDraft` -> `UPDATE`
  - `deleteById` -> `DELETE`
  - `createAndSubmit` and `submitDraft` status transitions -> `UPDATE`
- `UPDATE` events now persist changed-field diffs (`before_state`/`after_state`) rather than full snapshots.
- Correlation IDs come from request headers (`X-Correlation-Id`, `X-Request-Id`, fallback `Idempotency-Key`) and workflow process-instance IDs when request headers are absent.
- `RequestProcessCompletionListener` and `RequestProcessRejectedListener` now also write audit updates.

## Other resource audit integration

- `DefaultUserService` now writes audit events on create/update/delete.
- `DefaultTenantService` now writes audit events on create/update/delete.
- `DefaultDocumentTemplateService` now writes audit events on upload(create)/delete.
- `DefaultRequestTypeService` now writes:
  - create event for `request_types` and initial `request_type_versions`
  - update event for `request_types` on type change
  - create event for each new `request_type_versions` on type change
  - delete event for `request_types` on delete

## Activity endpoint

- Added `GET /api/requests/{id}/activity`
- Added request API contract support:
  - `RequestActivitySearchCriteria`
  - `RequestActivityEventResponse`
- Event type is now a strong enum (`RequestActivityEventType`) rather than free-form strings.
- Added typed payload variants for activity responses:
  - `RequestStatusChangedActivityPayload`
  - `RequestTaskAssignedActivityPayload`
  - `RequestTaskCompletedActivityPayload`
- Activity source is `request_activity_events` with domain event types:
  - `STATUS_CHANGED`
  - `TASK_ASSIGNED`
  - `TASK_COMPLETED`
- Endpoint filters:
  - `eventTypes` (enum list)
  - `createdAtFrom` (inclusive lower bound)
  - `createdAtTo` (inclusive upper bound)
- Event writers:
  - Request status transitions (service + process listeners)
  - Task assignment/completion (task lifecycle listener)

## Testing and verification

- Added integration tests in `DefaultRequestServiceIntegrationTest`:
  - `shouldWriteAuditEventForDraftCreate`
  - `shouldWriteAuditEventForDraftUpdate`
  - `shouldWriteAuditEventForDelete`
  - `shouldStoreDiffForUpdateAuditEvent`
- Added integration tests in `RequestModuleIntegrationTest`:
  - `shouldPopulateCorrelationIdFromIdempotencyKeyForAuditEvents`
  - `shouldExposeRequestActivityEvents`
  - `shouldFilterRequestActivityByEventTypeAndDateRange`
- Added/updated integration assertions for other resources:
  - `UserModuleIntegrationTest` verifies `CREATE -> UPDATE -> DELETE` audit sequence.
  - `TenantModuleIntegrationTest` verifies `CREATE -> UPDATE -> DELETE` audit sequence.
  - `DocumentTemplateModuleIntegrationTest` verifies `CREATE -> DELETE` audit sequence.
  - `RequestTypeModuleIntegrationTest` verifies `CREATE -> UPDATE -> DELETE` audit sequence for `request_types`.
- Verification run:
  - `./gradlew test --tests com.gurch.sandbox.requests.internal.DefaultRequestServiceIntegrationTest --tests com.gurch.sandbox.requests.RequestModuleIntegrationTest`
  - `./gradlew check`

## Notes / follow-ups

- `tenant_id` remains schema-ready on `audit_log_events` and is currently left `NULL`.
- Activity payloads are intentionally flexible JSON; if UI requirements harden, fields can be promoted to dedicated columns/indexes.
