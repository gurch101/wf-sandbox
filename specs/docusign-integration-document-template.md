# DocuSign Integration for Document Template Requests Specification

## 1. Summary

- Problem: Requests can generate documents, but there is no integrated e-sign capability for multi-signer in-person (embedded) or remote signing flows.
- Business objective: Add a tenant-aware DocuSign-backed e-sign service that can be started from backend APIs now, and later wired to workflow-triggered submission events.
- In scope:
  - Start e-sign from one API using a single contract that supports `EMBEDDED` and `REMOTE` modes.
  - Support multiple signers, ordered routing, and CC recipients.
  - Per-tenant DocuSign configuration stored in database.
  - Webhook-driven envelope status sync with signature verification and replay protection.
  - Automatic outbound retry (3 attempts, exponential backoff) for DocuSign API calls.
  - Lock template-derived data edits after envelope creation.
  - Void/cancel API, decline reason capture, reminder/expiration tenant defaults, signed artifact + certificate download/storage.
  - Audit event tracking.
- Out of scope:
  - Camunda integration and workflow-triggered auto-start in this phase.
  - Per-envelope override of reminder/expiration policy.
  - Metrics/alerts definition.
  - Data retention policy definition.

## 2. Confirmed Decisions

- Envelope start is API/service-only in this phase; no Camunda process integration now.
- No `workflowInstanceId` persistence field in this phase.
- Embedded flow supports multiple in-person signers.
- Remote flow supports multiple recipients with routing order.
- CC recipients are supported.
- Single start endpoint; mode selected by request field.
- Start API returns recipient view URL for embedded mode.
- Envelope status updates are webhook-driven.
- Tenant-scoped DocuSign configuration is required and stored in DB.
- Template data edits are blocked after envelope creation.
- Authorization:
  - Envelope creation happens through backend request submission flow.
  - Users can void only envelopes they created.
  - Users can fetch artifacts only for requests they submitted.
- Reliability:
  - Automatic retries enabled for DocuSign outbound calls.
  - Retry policy: 3 attempts, exponential backoff.
- Webhook handling:
  - Track all envelope statuses.
  - Verify DocuSign webhook signature.
  - Replay protection required.
  - Latest-state-wins conflict policy.
- Reminder/expiration policy:
  - Tenant-defaulted only.
  - Not overrideable per envelope.
- Include now:
  - Void/cancel API.
  - Reminder schedules.
  - Decline/reject reason capture.
  - Signed artifacts + certificate download.
  - Audit event tracking.
- Exclude now:
  - Metrics/alerts section.
  - Data retention requirements.

## 3. Blocked Items (Must Resolve Before Build)

- None.

## 4. Feature Breakdown

### 4.1 Tenant DocuSign Configuration

- Description:
  - Add per-tenant configuration and credentials used to authorize DocuSign API calls and webhook verification.
- API impact:
  - Admin/internal configuration API may be added separately; not required in this feature contract if seed/migration path is used.
- Data impact:
  - New tenant-scoped config table with encrypted credential fields and status flags.
- Workflow impact:
  - None in this phase.
- Security impact:
  - Secrets stored encrypted; access limited to service layer.
- Observability impact:
  - Audit events for config create/update/activate/deactivate.

### 4.2 Envelope Lifecycle Service

- Description:
  - Create envelopes for request-generated documents using one start endpoint with `signatureMode`.
  - Support signer routing and CC recipients.
  - Return embedded recipient view URL when mode is `EMBEDDED`.
  - Track and expose status for all envelope states.
- API impact:
  - New request-scoped e-sign endpoints for start/status/void/artifact retrieval.
- Data impact:
  - New envelope, recipient, and event tables linked to request and tenant.
- Workflow impact:
  - Future hook point from request submission; not implemented now.
- Security impact:
  - Request ownership checks on sensitive operations.
- Observability impact:
  - Lifecycle audit events for create/send/view/complete/decline/void.

### 4.3 Webhook Ingestion and Reconciliation

- Description:
  - Ingest DocuSign Connect events, verify signatures, dedupe events, and apply latest state update.
  - Persist decline reasons and artifact availability.
- API impact:
  - New webhook endpoint.
- Data impact:
  - Webhook raw event log + dedupe keys + envelope status timeline.
- Workflow impact:
  - None in this phase.
- Security impact:
  - Signature verification mandatory.
- Observability impact:
  - Audit entries for externally driven status transitions.

### 4.4 Document Lock and Artifact Storage

- Description:
  - Prevent edits to template-derived request data once envelope is created.
  - Download and persist signed document package and certificate.
- API impact:
  - Request update endpoints return conflict/error when locked.
  - Artifact download endpoint returns stored bytes/metadata.
- Data impact:
  - Request-level lock marker and artifact metadata records.
- Workflow impact:
  - None in this phase.
- Security impact:
  - Artifact access restricted to request submitter (and service internal paths).
- Observability impact:
  - Audit lock/unlock and artifact retrieval events.

## 5. User Stories

### US-001: Start Embedded E-Sign Envelope

- As a `request submitter`
- I want to start an embedded signing envelope for generated request documents
- So that in-person signers can sign on a managed device
- Priority: `P0`

### US-002: Start Remote E-Sign Envelope

- As a `request submitter`
- I want to start a remote signing envelope with ordered routing and CC recipients
- So that recipients can sign asynchronously by email
- Priority: `P0`

### US-003: Enforce Request Data Lock

- As a `platform operator`
- I want template data edits blocked after envelope creation
- So that the signed payload cannot drift from envelope content
- Priority: `P0`

### US-004: Process Webhook Events Safely

- As a `system`
- I want webhook events verified, deduplicated, and reconciled with latest-state-wins
- So that envelope status is correct despite duplicate or out-of-order delivery
- Priority: `P0`

### US-005: Void Envelope with Ownership Guard

- As a `request submitter`
- I want to void an in-flight envelope that I created
- So that I can cancel an invalid signing flow
- Priority: `P1`

### US-006: Access Signed Artifacts with Ownership Guard

- As a `request submitter`
- I want to download signed documents and the completion certificate for my request
- So that I can retain signed outputs
- Priority: `P1`

### US-007: Audit E-Sign Lifecycle

- As a `compliance reviewer`
- I want complete lifecycle audit events for envelope actions and status changes
- So that signing history is traceable
- Priority: `P1`

## 6. API Specification

Base path (new): `/api/requests`

### Endpoint: `POST /api/requests/{requestId}/esign`

- Purpose:
  - Start a DocuSign envelope for a request-generated document package.
- Auth:
  - Authenticated user must be allowed to submit the target request.
  - Service enforces tenant scope and request ownership rules.
- Request schema:
  - `signatureMode: "EMBEDDED" | "REMOTE"` (required)
  - `emailSubject: string` (optional)
  - `emailMessage: string` (optional)
  - `signers: Signer[]` (required, non-empty)
  - `ccRecipients: CcRecipient[]` (optional)
  - `idempotencyKey: string` (required)
  - `clientContext: object` (optional, audit/debug metadata)
  - `Signer`:
    - `recipientId: string` (required, unique within request)
    - `name: string` (required)
    - `email: string` (required for `REMOTE`; optional for `EMBEDDED` if host-controlled identity policy permits)
    - `routingOrder: int` (required; supports ordered routing)
    - `role: string` (optional)
  - `CcRecipient`:
    - `recipientId: string` (required, unique within request)
    - `name: string` (required)
    - `email: string` (required)
    - `routingOrder: int` (required)
- Response schema:
  - `201 Created`
  - `requestId: long`
  - `envelopeId: string`
  - `status: string`
  - `signatureMode: string`
  - `recipientViewUrl: string|null` (non-null for embedded flow)
  - `recipientViewExpiresAt: instant|null`
  - `createdAt: instant`
- Errors:
  - `400` invalid payload (missing recipients, duplicate routing ids, mode-recipient mismatch)
  - `401/403` unauthorized/forbidden
  - `404` request not found or out of tenant scope
  - `409` request already has active envelope or request data locked state violation
  - `422` request not in signable state
  - `503` DocuSign unavailable after retries exhausted
- Idempotency:
  - Required `idempotencyKey`.
  - Unique constraint by `(tenant_id, request_id, idempotency_key)`.
  - Replayed key returns original successful response; conflicting payload with same key returns `409`.

### Endpoint: `GET /api/requests/{requestId}/esign`

- Purpose:
  - Get current envelope summary for the request.
- Auth:
  - Request-scoped access check.
- Response:
  - `200 OK` with envelope summary and current status.
  - `404` when none exists or inaccessible.

### Endpoint: `POST /api/requests/{requestId}/esign/void`

- Purpose:
  - Void/cancel an active envelope.
- Auth:
  - Allowed only for envelope creator (same user who created envelope record).
- Request schema:
  - `reason: string` (required; audited)
- Response:
  - `202 Accepted` with updated envelope status projection.
- Errors:
  - `403` caller is not envelope creator
  - `409` envelope already terminal (`COMPLETED`, `VOIDED`, `DECLINED`, `EXPIRED`)

### Endpoint: `GET /api/requests/{requestId}/esign/artifacts`

- Purpose:
  - Download signed artifact bundle and completion certificate metadata.
- Auth:
  - Allowed only for request submitter.
- Response:
  - `200 OK`
  - Includes links or streamed files for:
    - final signed document bundle
    - completion certificate
  - `404` if artifacts not yet available
  - `403` if request ownership fails

### Endpoint: `POST /api/webhooks/docusign`

- Purpose:
  - Receive DocuSign Connect webhook events for envelope lifecycle updates.
- Auth:
  - Signature-verified webhook endpoint; no user auth context.
- Request schema:
  - Raw DocuSign event payload + signature headers.
- Response:
  - `200 OK` when accepted/applied or accepted/already processed.
  - `401` invalid signature.
- Processing guarantees:
  - Replay protection via event ID + envelope ID dedupe store.
  - Latest-state-wins by event timestamp, with deterministic tie-break using ingestion timestamp and event ID.

## 7. Data Model and Persistence

- Tables/entities affected:
  - `tenant_docusign_config`
  - `request_esign_envelopes`
  - `request_esign_recipients`
  - `request_esign_events`
  - `request_esign_artifacts`
  - `request_esign_webhook_events`
  - `requests` (add lock marker columns)
- New columns/constraints/indexes:
  - `tenant_docusign_config`
    - `tenant_id` (unique)
    - account/integration identifiers
    - encrypted credential fields
    - reminder defaults, expiration defaults
    - `active` flag
  - `request_esign_envelopes`
    - `id`, `tenant_id`, `request_id`, `envelope_id`, `created_by`, `signature_mode`
    - `status`, `status_updated_at`, `idempotency_key`, `decline_reason`
    - unique `(tenant_id, request_id, idempotency_key)`
    - unique `(tenant_id, envelope_id)`
  - `request_esign_recipients`
    - `envelope_row_id`, `recipient_id`, `recipient_type(SIGNER|CC)`, `routing_order`
  - `request_esign_events`
    - normalized envelope timeline entries for API/audit
  - `request_esign_webhook_events`
    - dedupe key columns (`event_id`, `envelope_id`)
    - raw payload, signature check result, event timestamp, processed flag
    - unique `(tenant_id, event_id, envelope_id)`
  - `request_esign_artifacts`
    - artifact type (`SIGNED_BUNDLE`, `CERTIFICATE`)
    - storage path/provider/checksum/content-type
  - `requests`
    - `esign_locked_at`, `esign_locked_by_envelope_row_id`
- Transaction boundaries:
  - Envelope creation transaction:
    - Validate request state and ownership.
    - Create local envelope record in `CREATING`.
    - Call DocuSign with retries.
    - Persist final creation result (`SENT`/initial status), recipients, and lock request record.
  - Webhook transaction:
    - Verify signature.
    - Insert webhook event row (dedupe check).
    - If new and fresher than current state, update envelope status + event timeline.
    - Persist decline reason/artifact availability pointers if present.
- Concurrency strategy:
  - Optimistic locking on envelope row `version`.
  - Unique constraints for idempotency and dedupe.
  - Request update endpoints reject changes when `esign_locked_at` is non-null.

## 8. Camunda 7 Workflow Specification

- Process key/name:
  - Deferred.
- Trigger:
  - Deferred to future phase (`request submitted` trigger target).
- Variables:
  - Deferred.
- Service tasks and retry strategy:
  - Not applicable in this phase (service-level retries only).
- Incidents/escalations:
  - Not applicable in this phase.
- Compensation/cancellation behavior:
  - Implemented via API void endpoint only in this phase.

## 9. Non-Functional Requirements

- Performance targets:
  - `POST /api/requests/{requestId}/esign` p95 <= 3s excluding external DocuSign latency spikes.
  - Webhook ingestion p95 <= 500ms server-side processing time.
- Reliability/SLA expectations:
  - Outbound DocuSign calls retried up to 3 attempts with exponential backoff.
  - Webhook processing is idempotent and safe for duplicates/out-of-order events.
- Security controls:
  - Tenant isolation for all read/write paths.
  - Signature verification required for webhook acceptance.
  - DocuSign credentials encrypted at rest in tenant-scoped table.
  - Ownership checks for void and artifact retrieval.
- Observability (logs/metrics/traces):
  - Structured logs include `tenantId`, `requestId`, `envelopeId`, `eventId`, `idempotencyKey`, `actorUserId`.
  - Audit events required for create, void, status transitions, decline, artifact retrieval.

## 10. Acceptance Tests (Integration)

### AT-001: Start Embedded Envelope with Multiple In-Person Signers

- Covers user story: `US-001`
- Preconditions:
  - Tenant has active DocuSign config.
  - Request exists in signable submitted state; caller is request submitter.
- Test steps:
  - Call `POST /api/requests/{requestId}/esign` with `signatureMode=EMBEDDED`, 2 signers, routing orders.
- Expected API result:
  - `201` with `envelopeId` and non-null `recipientViewUrl`.
- Expected DB state:
  - Envelope row created with `signature_mode=EMBEDDED`, lock columns set on request, recipients persisted.
- Expected Camunda state:
  - None (deferred).

### AT-002: Start Remote Envelope with Ordered Recipients and CC

- Covers user story: `US-002`
- Preconditions:
  - Same as AT-001.
- Test steps:
  - Call start endpoint with `signatureMode=REMOTE`, multiple signers and CC with routing order.
- Expected API result:
  - `201`; `recipientViewUrl` may be null.
- Expected DB state:
  - Recipients include both signer and CC entries with preserved routing order.
- Expected Camunda state:
  - None.

### AT-003: Idempotent Envelope Start

- Covers user story: `US-001`, `US-002`
- Preconditions:
  - Existing successful start call with idempotency key `K1`.
- Test steps:
  - Repeat same request with same key/payload.
  - Repeat with same key but changed payload.
- Expected API result:
  - First replay returns original success projection.
  - Changed payload returns `409`.
- Expected DB state:
  - Single envelope row for key `K1`.
- Expected Camunda state:
  - None.

### AT-004: Block Request Edits After Envelope Creation

- Covers user story: `US-003`
- Preconditions:
  - Envelope created and request lock set.
- Test steps:
  - Attempt request payload edit via draft/update endpoint applicable to request state.
- Expected API result:
  - `409` (or configured conflict code) with lock reason.
- Expected DB state:
  - Request payload unchanged; lock remains.
- Expected Camunda state:
  - None.

### AT-005: Webhook Signature Verification Failure

- Covers user story: `US-004`
- Preconditions:
  - Envelope exists.
- Test steps:
  - POST webhook payload with invalid signature header.
- Expected API result:
  - `401`.
- Expected DB state:
  - Event not applied to envelope status; rejection optionally logged.
- Expected Camunda state:
  - None.

### AT-006: Webhook Replay Protection

- Covers user story: `US-004`
- Preconditions:
  - Valid webhook event `E1` for envelope `D1`.
- Test steps:
  - Deliver same `E1` twice.
- Expected API result:
  - Both return `200`, second marked duplicate internally.
- Expected DB state:
  - One applied transition only; dedupe row present.
- Expected Camunda state:
  - None.

### AT-007: Out-of-Order Webhooks Apply Latest State

- Covers user story: `US-004`
- Preconditions:
  - Envelope in `SENT`.
- Test steps:
  - Deliver newer `COMPLETED` event, then older `DELIVERED` event.
- Expected API result:
  - Both accepted.
- Expected DB state:
  - Final status remains `COMPLETED`; timeline records both events with ordering metadata.
- Expected Camunda state:
  - None.

### AT-008: Decline Reason Capture

- Covers user story: `US-004`
- Preconditions:
  - Envelope exists.
- Test steps:
  - Deliver valid `DECLINED` webhook with reason.
- Expected API result:
  - `200`.
- Expected DB state:
  - Envelope status `DECLINED`; decline reason persisted.
- Expected Camunda state:
  - None.

### AT-009: Void Authorization Enforcement

- Covers user story: `US-005`
- Preconditions:
  - Active envelope created by user A.
- Test steps:
  - User B attempts `POST /api/requests/{requestId}/esign/void`.
- Expected API result:
  - `403`.
- Expected DB state:
  - Envelope status unchanged.
- Expected Camunda state:
  - None.

### AT-010: Void by Creator

- Covers user story: `US-005`
- Preconditions:
  - Active envelope created by request submitter.
- Test steps:
  - Creator calls void endpoint with reason.
- Expected API result:
  - `202` with terminal `VOIDED` (or transitional then terminal via webhook reconciliation).
- Expected DB state:
  - Void reason/status transition recorded in events/audit tables.
- Expected Camunda state:
  - None.

### AT-011: Artifact Retrieval Access Control

- Covers user story: `US-006`
- Preconditions:
  - Completed envelope with stored signed bundle/certificate.
- Test steps:
  - Non-submitter requests artifacts; then submitter requests artifacts.
- Expected API result:
  - Non-submitter `403`; submitter `200`.
- Expected DB state:
  - Artifact metadata unchanged; access audit event written for successful retrieval.
- Expected Camunda state:
  - None.

### AT-012: Outbound Retry Behavior on Transient DocuSign Failure

- Covers user story: `US-001`, `US-002`
- Preconditions:
  - Stub DocuSign to fail transiently twice then succeed.
- Test steps:
  - Start envelope request.
- Expected API result:
  - `201` if success on third attempt.
- Expected DB state:
  - Retry attempt metadata recorded in logs/audit context; single envelope record.
- Expected Camunda state:
  - None.

## 11. Edge Cases and Failure Scenarios

- Case: Tenant has no active DocuSign config.
- Expected behavior: Start endpoint returns `422` with actionable error code.

- Case: Duplicate recipient IDs or invalid routing order.
- Expected behavior: `400` validation error; nothing persisted.

- Case: Active envelope already exists for request.
- Expected behavior: `409` to prevent concurrent signing sessions.

- Case: Terminal envelope receives stale non-terminal event.
- Expected behavior: Ignore state regression; record event as stale.

- Case: DocuSign outage for all 3 retry attempts.
- Expected behavior: `503` and envelope record marked failed-to-create with diagnostics.

- Case: Artifact download requested before completion.
- Expected behavior: `404` artifacts not available yet.

## 12. Adjacent Feature Recommendations

- Recommendation: Configurable resend/reminder trigger API for operators.
- Reason: Tenant defaults exist, but operator-triggered resend often needed for stalled envelopes.
- Include now? `no`

- Recommendation: Admin search endpoint for envelopes by status/date/requestType.
- Reason: Operational support and reconciliation needs grow quickly after launch.
- Include now? `no`

- Recommendation: Metrics and alerts package (success %, webhook lag, retry exhaustions).
- Reason: Important for production operations but explicitly deferred by stakeholder.
- Include now? `no`
