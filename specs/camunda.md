# Request + Camunda Approval Foundation Specification

## 1. Summary

- Problem: The app needs a durable request lifecycle that can run and track a Camunda 7 approval workflow.
- Business objective: Establish a minimal but production-structured foundation for future multi-step workflows.
- In scope: Request CRUD subset (create/search/submit), Camunda 7 process start, one manual approval task, task APIs (list/claim/assign/complete), status synchronization, idempotency, validation/errors, observability baseline.
- Out of scope: Complex multi-step BPMN, external system integrations, authentication/authorization, UI.

## 2. Confirmed Decisions

- API base paths are `/api/requests` and `/api/tasks`.
- API versioning is header-based via `X-API-VERSION`.
- Current request input includes only `name`.
- Name search is case-insensitive partial match and combinable with status filters.
- Two submit paths are required:
- `POST /api/requests/{id}/submit` for existing drafts.
- `POST /api/requests/submit` for create-and-submit in one call.
- Create and submit operations must be idempotent.
- Request statuses: `DRAFT`, `IN_PROGRESS`, `COMPLETED`, `REJECTED`.
- Task APIs must include claim, assign, complete, and list tasks by request.
- Task completion must require workflow variable `approved` (boolean).
- Request terminal status updates (`COMPLETED`/`REJECTED`) are driven by Camunda process end listener.
- No auth in this phase.
- Errors use RFC7807 with custom `errors` array entries `{name, code, reason}`.
- PostgreSQL is the persistence target.
- Include all adjacent features listed in discovery.

## 3. Blocked Items (Must Resolve Before Build)

- None.

## 4. Feature Breakdown

### 4.1 Request Lifecycle API

- Description: Create draft, create+submit, submit existing draft, and search requests.
- API impact: New request endpoints with header versioning and idempotency behavior.
- Data impact: New `requests` table plus idempotency/audit support tables.
- Workflow impact: Submit operations start Camunda process and bind process instance to request.
- Security impact: No auth now; endpoints remain open in this phase.
- Observability impact: Structured logs and request/process correlation IDs.

### 4.2 Camunda One-Step Manual Approval

- Description: BPMN with one user task and approve/reject outcome.
- API impact: Task operations and request-task lookup endpoint.
- Data impact: Request status transitions updated from workflow lifecycle.
- Workflow impact: Process variables include `requestId`, `requestName`, `approved`.
- Security impact: Task claim/assign without auth restrictions in this phase.
- Observability impact: Metrics for task lifecycle and workflow completion outcome.

### 4.3 Reliability/Operational Baseline

- Description: Idempotency, validation, concurrency control, incidents, and metrics.
- API impact: `Idempotency-Key` support and RFC7807 error payload.
- Data impact: Idempotency record table and indexes for search paths.
- Workflow impact: Incident handling policy for listener/engine failures.
- Security impact: Audit events persisted for operational accountability.
- Observability impact: Metrics and logs suitable for SLO monitoring.

## 5. User Stories

### US-001: Create Draft Request

- As a `request initiator`
- I want to create a request in draft
- So that I can submit later after review
- Priority: `P0`

### US-002: Create And Submit In One Call

- As a `request initiator`
- I want to create and submit a request in one API call
- So that I can skip draft when ready
- Priority: `P0`

### US-003: Submit Existing Draft

- As a `request initiator`
- I want to submit an existing draft request
- So that manual approval starts
- Priority: `P0`

### US-004: Search Requests

- As an `operator`
- I want to search requests by status list and partial name
- So that I can find in-flight or completed work quickly
- Priority: `P0`

### US-005: Work Approval Tasks

- As an `approver`
- I want to list, claim, assign, and complete tasks
- So that approval decisions are executed through workflow
- Priority: `P0`

### US-006: Reliable Status Synchronization

- As a `system maintainer`
- I want request terminal status to be driven by process completion events
- So that DB state reflects workflow outcome reliably
- Priority: `P0`

## 6. API Specification

All endpoints require header `X-API-VERSION: 1`.

### Endpoint: `POST /api/requests`

- Purpose: Create draft request.
- Auth: None.
- Request schema:
```json
{
  "name": "string (1..200)"
}
```
- Response schema (`201`):
```json
{
  "id": "uuid",
  "name": "string",
  "status": "DRAFT",
  "processInstanceId": null,
  "createdAt": "timestamp",
  "updatedAt": "timestamp"
}
```
- Errors: `400`, `409`, `422`, `500` with RFC7807 + `errors`.
- Idempotency: Supports `Idempotency-Key` header. Same key + same normalized payload returns original `201` response. Same key + different payload returns `409`.

### Endpoint: `POST /api/requests/submit`

- Purpose: Create new request and immediately start workflow.
- Auth: None.
- Request schema:
```json
{
  "name": "string (1..200)"
}
```
- Response schema (`201`):
```json
{
  "id": "uuid",
  "name": "string",
  "status": "IN_PROGRESS",
  "processInstanceId": "string",
  "createdAt": "timestamp",
  "updatedAt": "timestamp"
}
```
- Errors: `400`, `409`, `422`, `500`.
- Idempotency: Supports `Idempotency-Key` with same semantics as create.

### Endpoint: `POST /api/requests/{id}/submit`

- Purpose: Submit existing request.
- Auth: None.
- Request schema: Empty body.
- Response schema (`200`):
```json
{
  "id": "uuid",
  "name": "string",
  "status": "IN_PROGRESS",
  "processInstanceId": "string",
  "createdAt": "timestamp",
  "updatedAt": "timestamp"
}
```
- Errors: `404` if request missing, `409` for illegal transition, `500`.
- Idempotency: Repeated submit on same request returns current representation. If already `IN_PROGRESS`, returns `200` unchanged. If terminal (`COMPLETED`/`REJECTED`), returns `409`.

### Endpoint: `GET /api/requests`

- Purpose: Search requests.
- Auth: None.
- Query params:
- `statuses` (optional, repeated or comma-separated values from enum)
- `nameQuery` (optional, case-insensitive partial)
- `page` (default `0`)
- `size` (default `20`, max `100`)
- `sort` (default `createdAt,desc`)
- Response schema (`200`): paginated list of request DTOs.
- Errors: `400`, `500`.
- Idempotency: Read-only.

### Endpoint: `GET /api/requests/{id}/tasks`

- Purpose: List active tasks for request’s process instance.
- Auth: None.
- Response schema (`200`):
```json
[
  {
    "taskId": "string",
    "name": "Manual Approval",
    "assignee": "string|null",
    "createdAt": "timestamp",
    "processInstanceId": "string"
  }
]
```
- Errors: `404`, `500`.

### Endpoint: `POST /api/tasks/{taskId}/claim`

- Purpose: Claim task.
- Auth: None.
- Request schema:
```json
{
  "assignee": "string (1..100)"
}
```
- Response: `204`.
- Errors: `404`, `409` (already claimed), `500`.

### Endpoint: `POST /api/tasks/{taskId}/assignee`

- Purpose: Set or reassign task assignee.
- Auth: None.
- Request schema:
```json
{
  "assignee": "string (1..100)"
}
```
- Response: `204`.
- Errors: `404`, `500`.

### Endpoint: `POST /api/tasks/{taskId}/complete`

- Purpose: Complete manual approval task.
- Auth: None.
- Request schema:
```json
{
  "variables": {
    "approved": true
  }
}
```
- Response: `204`.
- Errors: `400` if `approved` missing/not boolean, `404`, `409`, `500`.
- Idempotency: If task already completed, return `409` with problem type `task-state-conflict`.

### Error payload format (all non-2xx)

```json
{
  "type": "https://example.com/problems/validation-error",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request has invalid fields",
  "instance": "/api/requests",
  "errors": [
    {
      "name": "name",
      "code": "NOT_BLANK",
      "reason": "name must not be blank"
    }
  ]
}
```

## 7. Data Model and Persistence

- Tables/entities affected:
- `requests`
- `api_idempotency_records`
- `request_audit_events`
- `requests` columns:
- `id UUID PK`
- `name VARCHAR(200) NOT NULL`
- `status VARCHAR(32) NOT NULL` (`DRAFT|IN_PROGRESS|COMPLETED|REJECTED`)
- `process_instance_id VARCHAR(64) NULL`
- `created_at TIMESTAMPTZ NOT NULL`
- `updated_at TIMESTAMPTZ NOT NULL`
- `version BIGINT NOT NULL` (optimistic locking)
- `api_idempotency_records` columns:
- `id UUID PK`
- `idempotency_key VARCHAR(128) NOT NULL`
- `operation VARCHAR(64) NOT NULL` (`CREATE_DRAFT`, `CREATE_AND_SUBMIT`, `SUBMIT_EXISTING`)
- `request_hash VARCHAR(128) NOT NULL`
- `response_status INT NOT NULL`
- `response_body JSONB NOT NULL`
- `created_at TIMESTAMPTZ NOT NULL`
- Unique constraint on `(idempotency_key, operation)`.
- `request_audit_events` columns:
- `id UUID PK`
- `request_id UUID NOT NULL`
- `event_type VARCHAR(64) NOT NULL`
- `event_payload JSONB NOT NULL`
- `created_at TIMESTAMPTZ NOT NULL`
- New constraints/indexes:
- Unique partial index on `requests(process_instance_id)` where not null.
- Index on `requests(status)`.
- Index on `lower(requests.name)` for `ILIKE`/functional search.
- Composite index on `(status, created_at desc)`.
- Index on `request_audit_events(request_id, created_at desc)`.
- Transaction boundaries:
- Create draft: one DB transaction (request + audit + idempotency record).
- Create+submit: one transaction with request insert + Camunda process start + process_instance_id update + audit + idempotency record.
- Submit existing: one transaction with status transition, process start, process id binding, audit, idempotency update.
- Concurrency strategy:
- Optimistic locking via `version`.
- Submit paths enforce legal transitions with compare-and-set update (`WHERE version=? AND status in (...)`).
- Idempotency key prevents duplicate create/submit effects on retries.
- Retention/archive:
- Keep requests and audit indefinitely for now.
- Keep idempotency records 30 days (scheduled cleanup).

## 8. Camunda 7 Workflow Specification

- Process key/name: `request_manual_approval_v1` / “Request Manual Approval v1”.
- Trigger: Request submit APIs.
- BPMN flow:
- Start event.
- User task `manualApprovalTask`.
- Exclusive gateway on `${approved}`.
- End event `approvedEnd`.
- End event `rejectedEnd`.
- Variables:
- `requestId` (string UUID)
- `requestName` (string)
- `approved` (boolean, required at complete)
- `decisionBy` (string, optional)
- `decisionAt` (datetime, optional)
- Service tasks and retry strategy:
- No service task in v1.
- End listener execution failures are retried by Camunda job executor defaults.
- Incidents/escalations:
- If listener/update fails, Camunda incident is created.
- Request remains `IN_PROGRESS` until incident resolved and listener succeeds.
- Add operations metric for incident count and age.
- Compensation/cancellation behavior:
- No compensation path in v1.
- Cancellation not exposed via API in v1.
- Process-to-request synchronization:
- End listener on each terminal end event updates `requests.status`:
- `approvedEnd` -> `COMPLETED`
- `rejectedEnd` -> `REJECTED`
- Listener writes audit event for terminal transition.

## 9. Non-Functional Requirements

- Performance targets:
- `POST /api/requests`, `POST /api/requests/{id}/submit`, `POST /api/requests/submit`, and task endpoints: p95 <= 300ms, p99 <= 800ms under baseline load.
- `GET /api/requests`: p95 <= 400ms, p99 <= 1000ms for page size <= 100.
- Reliability/SLA expectations:
- Monthly availability target: 99.5% for API endpoints.
- Idempotent retries must not create duplicate requests/processes.
- Security controls:
- TLS in transit.
- Least-privilege DB credentials.
- No endpoint auth in this phase.
- Observability:
- Structured logs include `requestId` (HTTP), `requestEntityId`, `processInstanceId`, `taskId`, `idempotencyKey`.
- Metrics:
- Request create/submit success/failure counts.
- Task claim/assign/complete success/failure counts.
- Workflow completed vs rejected counts.
- Incident count and oldest incident age.
- API latency histograms by endpoint/status.
- Trace span chain: API -> DB -> Camunda runtime/history operations.

## 10. Acceptance Tests (Integration)

### AT-001: Create Draft Request

- Covers user story: `US-001`
- Preconditions: Empty DB.
- Test steps: `POST /api/requests` with valid name and `X-API-VERSION: 1`.
- Expected API result: `201`, status `DRAFT`, null process instance.
- Expected DB state: One `requests` row with `DRAFT`.
- Expected Camunda state: No process instance created.

### AT-002: Idempotent Draft Create Retry

- Covers user story: `US-001`
- Preconditions: None.
- Test steps: Repeat same create request with same `Idempotency-Key`.
- Expected API result: Same `201` body as first response.
- Expected DB state: Single request row; one idempotency record.
- Expected Camunda state: No process instance created.

### AT-003: Create And Submit

- Covers user story: `US-002`
- Preconditions: Empty DB/Camunda runtime.
- Test steps: `POST /api/requests/submit` with valid name.
- Expected API result: `201`, status `IN_PROGRESS`, non-null process instance id.
- Expected DB state: Request row linked to process instance.
- Expected Camunda state: One active process instance with one user task.

### AT-004: Submit Existing Draft

- Covers user story: `US-003`
- Preconditions: Existing `DRAFT` request.
- Test steps: `POST /api/requests/{id}/submit`.
- Expected API result: `200`, status `IN_PROGRESS`, process instance id populated.
- Expected DB state: Same request transitioned to `IN_PROGRESS`.
- Expected Camunda state: Active process instance + user task exists.

### AT-005: Submit Existing Idempotency

- Covers user story: `US-003`
- Preconditions: Request already `IN_PROGRESS`.
- Test steps: Repeat `POST /api/requests/{id}/submit`.
- Expected API result: `200` unchanged request representation.
- Expected DB state: No duplicate process instance; unchanged process id.
- Expected Camunda state: Single active process instance for request.

### AT-006: Search By Status And Name

- Covers user story: `US-004`
- Preconditions: Seed requests across `DRAFT`, `IN_PROGRESS`, `COMPLETED`; varied names.
- Test steps: `GET /api/requests?statuses=IN_PROGRESS,COMPLETED&nameQuery=app`.
- Expected API result: `200` paged results matching both filters, case-insensitive partial name.
- Expected DB state: None changed.
- Expected Camunda state: None changed.

### AT-007: List Tasks By Request

- Covers user story: `US-005`
- Preconditions: Request with active process and active user task.
- Test steps: `GET /api/requests/{id}/tasks`.
- Expected API result: `200` with manual approval task details.
- Expected DB state: None changed.
- Expected Camunda state: Task remains active.

### AT-008: Claim And Assign Task

- Covers user story: `US-005`
- Preconditions: Active unclaimed task.
- Test steps: `POST /api/tasks/{taskId}/claim`, then `POST /api/tasks/{taskId}/assignee`.
- Expected API result: `204` for both.
- Expected DB state: Audit events recorded.
- Expected Camunda state: Task assignee updated accordingly.

### AT-009: Complete Approved Path

- Covers user story: `US-005`, `US-006`
- Preconditions: Active task for request in `IN_PROGRESS`.
- Test steps: `POST /api/tasks/{taskId}/complete` with `variables.approved=true`.
- Expected API result: `204`.
- Expected DB state: End listener updates request to `COMPLETED`, audit terminal event written.
- Expected Camunda state: Process instance ended via `approvedEnd`.

### AT-010: Complete Rejected Path

- Covers user story: `US-005`, `US-006`
- Preconditions: Active task for request in `IN_PROGRESS`.
- Test steps: `POST /api/tasks/{taskId}/complete` with `variables.approved=false`.
- Expected API result: `204`.
- Expected DB state: End listener updates request to `REJECTED`, audit terminal event written.
- Expected Camunda state: Process instance ended via `rejectedEnd`.

### AT-011: Validation Error Contract

- Covers user story: `US-001`, `US-002`, `US-005`
- Preconditions: None.
- Test steps: Send invalid request (blank name or missing approved variable).
- Expected API result: `400` RFC7807 payload with populated `errors[]`.
- Expected DB state: No writes.
- Expected Camunda state: No new process or task changes.

### AT-012: Listener Failure Incident

- Covers user story: `US-006`
- Preconditions: Inject transient DB failure during end listener.
- Test steps: Complete task, force listener failure, then recover and retry.
- Expected API result: Task completion accepted (`204`) and later terminal status visible after retry.
- Expected DB state: Request remains `IN_PROGRESS` during incident, then transitions to terminal state after retry.
- Expected Camunda state: Incident created then resolved.

## 11. Edge Cases and Failure Scenarios

- Case: `POST /api/requests/{id}/submit` for missing request.
- Expected behavior: `404` problem response.
- Case: Submit for terminal request.
- Expected behavior: `409` with transition conflict details.
- Case: Idempotency key reused with different payload.
- Expected behavior: `409` with idempotency conflict problem type.
- Case: Claim already claimed task.
- Expected behavior: `409`.
- Case: Complete task without `approved`.
- Expected behavior: `400` validation error.
- Case: Concurrent submit attempts for same draft.
- Expected behavior: One transition starts process; others return idempotent success representation.
- Case: Search with empty filters.
- Expected behavior: Returns all requests paginated and sorted default.
- Case: Camunda start failure after request state mutation.
- Expected behavior: Single transaction rollback; request not left `IN_PROGRESS` without process id.
- Case: Stale update with optimistic lock conflict.
- Expected behavior: `409` conflict, no partial state.

## 12. Adjacent Feature Recommendations

- Recommendation: Validation + standardized RFC7807 error model.
- Reason: Predictable client handling and integration test stability.
- Include now? `yes`
- Recommendation: Idempotency for create/submit.
- Reason: Safe retries under network failures.
- Include now? `yes`
- Recommendation: Optimistic locking.
- Reason: Prevent race-induced duplicate workflow starts.
- Include now? `yes`
- Recommendation: Pagination/sorting for search.
- Reason: Predictable performance and scalable listing.
- Include now? `yes`
- Recommendation: Audit trail events.
- Reason: Operational traceability and future compliance.
- Include now? `yes`
- Recommendation: Metrics/logging/tracing correlation fields.
- Reason: Fast diagnosis across API, DB, and workflow runtime.
- Include now? `yes`
- Recommendation: Camunda incident handling baseline.
- Reason: Clear recovery behavior for listener failures.
- Include now? `yes`
- Recommendation: DB migration/index strategy.
- Reason: Stable query performance and safe rollout.
- Include now? `yes`

## Explicit Out Of Scope

- AuthN/AuthZ and RBAC enforcement.
- Workflow cancellation endpoint.
- Multi-step approval chains, SLAs/escalation timers, and delegation rules.
- UI/task inbox front-end.
- External notifications and downstream system orchestration.
