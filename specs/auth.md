# AuthN/AuthZ + Auditing Specification

## 1. Summary

- Problem: The platform currently has no authentication or authorization controls and needs to support both a future React SPA and machine-to-machine integrations.
- Business objective: Introduce standards-based app-owned auth using OAuth2/OIDC, enforce RBAC + workflow-group access, support per-client data scope customization, and populate auditing fields (`created_by`, `updated_by`) from authenticated identity.
- In scope:
  - Spring Authorization Server in the same backend app.
  - JWT access tokens and rotating refresh tokens.
  - SPA login (`authorization_code + PKCE`) and system clients (`client_credentials`).
  - Users, roles, permissions, workflow groups, and client data scopes.
  - Admin APIs for managing roles/permissions and user assignments.
  - Dedicated system user row per machine client.
  - Spring Data JDBC auditing integration using current authenticated user UUID.
  - Schema updates to add auditing columns on domain tables.
- Out of scope:
  - External identity providers.
  - SAML.
  - Social login.
  - UI implementation for login/admin screens.

## 2. Confirmed Decisions

- Decision: Use JWT access tokens.
- Decision: Access token TTL is `15 minutes`.
- Decision: Refresh token TTL is `30 days`, rotating on each refresh; reuse causes token family revocation.
- Decision: Run Authorization Server in the same backend app.
- Decision: Support only:
  - React SPA via OAuth2 `authorization_code + PKCE`.
  - System clients via OAuth2 `client_credentials`.
- Decision: Login supports username or email.
- Decision: User IDs are UUID.
- Decision: Users can hold multiple roles.
- Decision: Fluxnova user-task execution requires both:
  - Permission to complete user tasks.
  - Membership in a workflow group.
- Decision: Permissions are string codes.
- Decision: Data access customization is required (example: user can access only clients A/B/C).
- Decision: For data scope, keep JWT minimal and enforce client scope from DB at request time.
- Decision: Roles/permissions must be manageable via admin APIs.
- Decision: DB is assumed empty; no legacy backfill required during migration.
- Decision: Machine clients map to dedicated system user rows (one per client).
- Decision: Bootstrap first admin via environment-driven startup initialization.

## 3. Blocked Items (Must Resolve Before Build)

- None.

## 4. Feature Breakdown

### 4.1 OAuth2/OIDC Server and Resource Server

- Description: Add Spring Authorization Server and configure existing APIs as resource server validating local JWTs.
- API impact:
  - OIDC discovery and authorization server endpoints.
  - Token issuance and refresh behavior.
  - Existing API endpoints require Bearer authentication except explicit public endpoints.
- Data impact:
  - Tables for users, credentials, clients, consent/authorizations, refresh token families.
- Workflow impact:
  - Fluxnova task APIs consume authenticated principal and enforce group membership.
- Security impact:
  - Standards-based flows for SPA and machine clients.
  - No password grant.
- Observability impact:
  - Metrics/logs for token issue/refresh/revoke and failed auth events.

### 4.2 RBAC + Permission + Workflow Group Authorization

- Description: Enforce role-derived permissions and task-group membership checks.
- API impact:
  - Permission checks on request and task endpoints.
  - Admin APIs for role/permission/user-role/group assignments.
- Data impact:
  - Role, permission, mapping, and workflow-group tables.
- Workflow impact:
  - Task claim/complete/assign authorization includes workflow group rule.
- Security impact:
  - Least privilege via explicit permission codes.
- Observability impact:
  - Denied access events include subject, permission, resource, and group context.

### 4.3 Client Data Scope Authorization

- Description: Support per-user and per-machine-client allowed client scopes (A/B/C style) resolved from DB.
- API impact:
  - Query filters and write guards include `client_id` scope checks.
- Data impact:
  - Mapping of principal to allowed business client IDs.
- Workflow impact:
  - Request/task operations scoped to allowed clients.
- Security impact:
  - Prevent cross-client data exposure.
- Observability impact:
  - Auditable authorization failures for out-of-scope client attempts.

### 4.4 Auditing Columns and Auditor Resolution

- Description: Add `created_by` and `updated_by` columns and populate via `@EnableJdbcAuditing` + `AuditorAware<UUID>`.
- API impact:
  - None in request/response payloads by default.
- Data impact:
  - Additional columns on domain tables.
- Workflow impact:
  - Background/system actions attributed to dedicated system user IDs.
- Security impact:
  - Strong auditability of actor identity.
- Observability impact:
  - Trace logs correlate principal ID with entity-level changes.

## 5. User Stories

### US-001: SPA User Login

- As a `frontend user`
- I want to authenticate via secure browser flow
- So that I can call protected APIs from React
- Priority: `P0`

### US-002: Machine Client Authentication

- As a `partner/integration system`
- I want to obtain access tokens using client credentials
- So that I can call protected APIs without user interaction
- Priority: `P0`

### US-003: Role/Permission Authorization

- As a `platform admin`
- I want to assign roles and permissions
- So that endpoint access is governed by least privilege
- Priority: `P0`

### US-004: Workflow Group Enforcement

- As a `workflow operator`
- I want task actions to require both permission and group membership
- So that users cannot complete tasks outside their workflow groups
- Priority: `P0`

### US-005: Per-Client Data Scope

- As a `security administrator`
- I want to restrict principals to specific business clients
- So that data access is limited to approved client scopes
- Priority: `P0`

### US-006: Entity Auditing

- As a `compliance stakeholder`
- I want created/updated actor IDs persisted for every entity write
- So that all changes are attributable
- Priority: `P0`

### US-007: Bootstrap First Admin

- As a `platform operator`
- I want an environment-driven bootstrap admin account on startup
- So that initial secure administration can begin without manual DB setup
- Priority: `P1`

## 6. API Specification

All protected resource endpoints continue to require `X-API-VERSION: 1`.

### Endpoint: `GET /.well-known/openid-configuration`

- Purpose: OIDC discovery metadata.
- Auth: None.
- Response schema: Standard OIDC discovery document.
- Errors: `500`.
- Idempotency: Read-only.

### Endpoint: `GET /oauth2/authorize`

- Purpose: Begin SPA authorization code flow with PKCE.
- Auth: Browser user login required if not authenticated.
- Request schema: Standard OAuth2 params (`response_type=code`, `client_id`, `redirect_uri`, `scope`, `code_challenge`, `code_challenge_method=S256`, `state`, `nonce`).
- Response schema: Redirect with authorization code.
- Errors: OAuth2 standard (`invalid_request`, `unauthorized_client`, etc.).
- Idempotency: N/A.

### Endpoint: `POST /oauth2/token`

- Purpose: Issue/refresh access tokens.
- Auth:
  - SPA token exchange uses PKCE code verifier.
  - System clients use client authentication (`client_secret_basic` or `client_secret_post`).
- Request schema:
  - `grant_type=authorization_code` (with `code`, `redirect_uri`, `code_verifier`) for SPA.
  - `grant_type=refresh_token` for refresh.
  - `grant_type=client_credentials` for machine clients.
- Response schema (`200`):
```json
{
  "access_token": "<jwt>",
  "token_type": "Bearer",
  "expires_in": 900,
  "refresh_token": "<opaque-or-jwt>",
  "scope": "openid profile api.read api.write"
}
```
- Errors: OAuth2 standard (`invalid_grant`, `invalid_client`, `unauthorized_client`, `invalid_scope`).
- Idempotency: N/A.

### Endpoint: `POST /oauth2/revoke`

- Purpose: Revoke refresh/access tokens.
- Auth: Client-authenticated.
- Request schema: OAuth2 token revocation request.
- Response schema: `200` empty.
- Errors: OAuth2 standard.
- Idempotency: Repeating revoke is safe.

### Endpoint: `GET /api/auth/me`

- Purpose: Return normalized principal context for UI and debugging.
- Auth: Bearer token required.
- Response schema (`200`):
```json
{
  "userId": "uuid",
  "username": "string",
  "email": "string",
  "roles": ["REQUEST_OPERATOR"],
  "permissions": ["request.read", "task.complete"],
  "workflowGroupIds": ["uuid"],
  "clientScopeIds": ["CLIENT_A", "CLIENT_B"]
}
```
- Errors: `401`, `403`.
- Idempotency: Read-only.

### Endpoint Group: Admin RBAC/Scope APIs

- Purpose: CRUD/manage roles, permissions, role-permission mappings, user-role mappings, workflow groups, user-group mappings, and principal client scopes.
- Auth: Requires admin permission (`admin.security.manage`).
- Response schema: JSON DTOs per resource.
- Errors: `400`, `401`, `403`, `404`, `409`.
- Idempotency:
  - Create endpoints are not idempotent unless explicit key added later.
  - Assignment endpoints should be idempotent by unique constraints (repeated assignment returns `204` unchanged).

### Protected Existing APIs

- Purpose: Enforce authz on existing `/api/requests/**` and `/api/tasks/**`.
- Auth: Bearer token required.
- Authorization:
  - Request APIs require relevant request permissions and client scope allowance.
  - Task complete requires both `task.complete` and workflow-group membership for task/request.
  - Task claim/assign/list similarly require explicit task permissions.
- Errors:
  - `401` when missing/invalid token.
  - `403` when authenticated but lacking permission/group/scope.

### JWT Claim Contract

- Required claims:
  - `sub`: UUID user ID (or dedicated system user UUID for machine token).
  - `username`
  - `token_type`: `user` or `system`
  - `roles`: string array
  - `permissions`: string array
- Excluded by design:
  - Per-client business scope list (loaded from DB at request time).

## 7. Data Model and Persistence

- Tables/entities affected:
  - `users`
  - `user_credentials`
  - `oauth_clients`
  - `system_client_users`
  - `roles`
  - `permissions`
  - `role_permissions`
  - `user_roles`
  - `workflow_groups`
  - `user_workflow_groups`
  - `principal_client_scopes`
  - `oauth_refresh_token_families`
  - Domain tables (`requests`, `request_tasks`, `api_idempotency_records`, future domain tables) updated with auditing columns.

- Key columns and constraints:
  - `users`
    - `id UUID PK`
    - `username VARCHAR(100) UNIQUE NOT NULL`
    - `email VARCHAR(320) UNIQUE NOT NULL`
    - `enabled BOOLEAN NOT NULL`
    - `is_system BOOLEAN NOT NULL DEFAULT false`
    - `created_at TIMESTAMPTZ NOT NULL`
    - `updated_at TIMESTAMPTZ NOT NULL`
  - `user_credentials`
    - `user_id UUID PK/FK users(id)`
    - `password_hash VARCHAR(255) NOT NULL`
    - `password_updated_at TIMESTAMPTZ NOT NULL`
  - `oauth_clients`
    - `id UUID PK`
    - `client_id VARCHAR(100) UNIQUE NOT NULL`
    - `client_secret_hash VARCHAR(255) NOT NULL`
    - `grant_types VARCHAR(255) NOT NULL`
    - `scopes VARCHAR(500) NOT NULL`
    - `redirect_uris TEXT NULL`
    - `enabled BOOLEAN NOT NULL`
  - `system_client_users`
    - `oauth_client_id UUID UNIQUE FK oauth_clients(id)`
    - `user_id UUID UNIQUE FK users(id)` (must reference `users.is_system=true`)
  - `roles`
    - `id UUID PK`
    - `code VARCHAR(100) UNIQUE NOT NULL`
    - `name VARCHAR(150) NOT NULL`
  - `permissions`
    - `id UUID PK`
    - `code VARCHAR(120) UNIQUE NOT NULL`
    - `description VARCHAR(255) NOT NULL`
  - `role_permissions`
    - `role_id UUID FK roles(id)`
    - `permission_id UUID FK permissions(id)`
    - Unique `(role_id, permission_id)`
  - `user_roles`
    - `user_id UUID FK users(id)`
    - `role_id UUID FK roles(id)`
    - Unique `(user_id, role_id)`
  - `workflow_groups`
    - `id UUID PK`
    - `code VARCHAR(100) UNIQUE NOT NULL`
    - `name VARCHAR(150) NOT NULL`
  - `user_workflow_groups`
    - `user_id UUID FK users(id)`
    - `workflow_group_id UUID FK workflow_groups(id)`
    - Unique `(user_id, workflow_group_id)`
  - `principal_client_scopes`
    - `id UUID PK`
    - `principal_user_id UUID FK users(id)`
    - `business_client_id VARCHAR(100) NOT NULL`
    - Unique `(principal_user_id, business_client_id)`
  - `oauth_refresh_token_families`
    - `id UUID PK`
    - `user_id UUID FK users(id)`
    - `client_id UUID FK oauth_clients(id)`
    - `current_refresh_token_hash VARCHAR(255) NOT NULL`
    - `revoked BOOLEAN NOT NULL DEFAULT false`
    - `expires_at TIMESTAMPTZ NOT NULL`
    - `updated_at TIMESTAMPTZ NOT NULL`

- Domain auditing column additions:
  - `created_by UUID NOT NULL` FK `users(id)`
  - `updated_by UUID NOT NULL` FK `users(id)`

- New indexes:
  - `users(lower(username))`, `users(lower(email))`.
  - `user_roles(user_id)`, `role_permissions(role_id)`, `permissions(code)`.
  - `principal_client_scopes(principal_user_id)`, `principal_client_scopes(business_client_id)`.
  - `user_workflow_groups(user_id)`.

- Transaction boundaries:
  - Auth token issuance and refresh in auth transaction boundaries managed by authorization server services.
  - Admin mutation APIs each transactional.
  - Domain write APIs maintain existing transaction boundaries and add audit field population in the same transaction.

- Concurrency strategy:
  - Unique constraints enforce idempotent assignment semantics.
  - Refresh token rotation uses compare-and-set update on token family to detect reuse.

## 8. Camunda 7 Workflow Specification

- Process key/name: Existing request process remains in place.
- Trigger: Existing submit/task lifecycle.
- Variables:
  - Add/ensure `workflowGroupCode` (or equivalent resolvable group identifier) exists on process/task context so group authorization can be checked.
- Service tasks and retry strategy:
  - No new service tasks required for auth.
  - Authorization failures must not create incidents; they return API `403`.
- Incidents/escalations:
  - Existing workflow incident behavior unchanged.
- Compensation/cancellation behavior:
  - Unchanged.
- Authorization rule for user tasks:
  - For task complete endpoint, evaluator must assert:
    - Principal has permission `task.complete`.
    - Principal belongs to required workflow group associated to the task/request.

## 9. Non-Functional Requirements

- Performance targets:
  - Token issuance (`/oauth2/token`): p95 <= 250ms, p99 <= 600ms.
  - Authorized business API calls add <= 20ms p95 overhead from permission/group/scope checks with warm caches.
- Reliability/SLA expectations:
  - Auth endpoints monthly availability target 99.9%.
  - Refresh token rotation must block replay/reuse reliably.
- Security controls:
  - Password hashes via strong adaptive hash (Argon2id preferred; BCrypt acceptable).
  - JWT signing key rotation policy defined and documented.
  - CORS locked to known frontend origins for SPA.
  - CSRF disabled for stateless API endpoints; enabled/configured where browser session endpoints require it.
  - Client secrets stored hashed, never plaintext.
- Observability (logs/metrics/traces):
  - Structured logs include `principalId`, `tokenType`, `clientId`, `permission`, `workflowGroup`, `businessClientId`, `requestId`.
  - Metrics:
    - `auth.login.success/failure`
    - `auth.token.issued`
    - `auth.refresh.success/failure/reuse_detected`
    - `auth.access.denied` by reason (`permission`, `group`, `client_scope`)
  - Trace spans include auth check sub-spans for business APIs.

## 10. Acceptance Tests (Integration)

### AT-001: SPA Authorization Code + PKCE Success

- Covers user story: `US-001`
- Preconditions: Registered SPA client with redirect URI; enabled user with credentials.
- Test steps:
  1. Execute authorize request with valid PKCE challenge.
  2. Exchange code at `/oauth2/token` with valid verifier.
- Expected API result:
  - `200` token response with JWT access token (`exp ~ 15m`) and refresh token.
- Expected DB state:
  - Authorization/refresh token family persisted.
- Expected Camunda state:
  - None changed.

### AT-002: Client Credentials Success With System User Mapping

- Covers user story: `US-002`
- Preconditions: Enabled machine client mapped in `system_client_users`.
- Test steps: `POST /oauth2/token` with `grant_type=client_credentials`.
- Expected API result:
  - `200` with JWT access token.
- Expected DB state:
  - Token issuance audit entry/log and expected auth artifacts.
- Expected Camunda state:
  - None changed.

### AT-003: Multi-Role Permission Union

- Covers user story: `US-003`
- Preconditions: User assigned two roles with non-overlapping permissions.
- Test steps: Call endpoints requiring each permission.
- Expected API result:
  - User authorized for union of permissions.
- Expected DB state:
  - None changed.
- Expected Camunda state:
  - None changed.

### AT-004: Task Complete Denied Without Workflow Group

- Covers user story: `US-004`
- Preconditions: User has `task.complete` but not required workflow group membership.
- Test steps: `POST /api/tasks/{taskId}/complete`.
- Expected API result:
  - `403`.
- Expected DB state:
  - No task completion side effects.
- Expected Camunda state:
  - Task remains active.

### AT-005: Task Complete Allowed With Permission + Group

- Covers user story: `US-004`
- Preconditions: User has `task.complete` and required workflow group membership.
- Test steps: `POST /api/tasks/{taskId}/complete`.
- Expected API result:
  - `204`.
- Expected DB state:
  - Existing request/task status updates proceed.
- Expected Camunda state:
  - Task completed according to process path.

### AT-006: Request Access Denied Outside Client Scope

- Covers user story: `US-005`
- Preconditions: User scope has only `CLIENT_A`; target request belongs to `CLIENT_B`.
- Test steps: `GET /api/requests/{id}` (or list including `CLIENT_B` filter).
- Expected API result:
  - `403` (or empty result for list endpoints where policy is filter-only; policy must be consistent and tested).
- Expected DB state:
  - None changed.
- Expected Camunda state:
  - None changed.

### AT-007: Auditing Columns Populated for User Write

- Covers user story: `US-006`
- Preconditions: Authenticated end user.
- Test steps: Create or update a request entity.
- Expected API result:
  - Successful write response.
- Expected DB state:
  - `created_by`/`updated_by` set to authenticated user UUID.
- Expected Camunda state:
  - Unchanged unless endpoint triggers workflow.

### AT-008: Auditing Columns Populated for Machine Write

- Covers user story: `US-006`
- Preconditions: Machine client token mapped to dedicated system user.
- Test steps: Perform write endpoint allowed to machine client.
- Expected API result:
  - Successful write response.
- Expected DB state:
  - `created_by`/`updated_by` set to mapped system user UUID.
- Expected Camunda state:
  - Endpoint-dependent.

### AT-009: Refresh Token Rotation and Reuse Detection

- Covers user story: `US-001`
- Preconditions: Valid refresh token issued.
- Test steps:
  1. Exchange refresh token once (success, new refresh token).
  2. Reuse old refresh token.
- Expected API result:
  - Step 1 `200`.
  - Step 2 `401/400 invalid_grant`; token family revoked.
- Expected DB state:
  - Token family marked revoked on reuse detection.
- Expected Camunda state:
  - None changed.

### AT-010: Environment Bootstrap Admin

- Covers user story: `US-007`
- Preconditions: Empty DB; bootstrap env vars set.
- Test steps: Start application.
- Expected API result:
  - N/A.
- Expected DB state:
  - One enabled admin user with hashed password and admin role assignment.
  - Startup is idempotent (second start does not create duplicates).
- Expected Camunda state:
  - None changed.

## 11. Edge Cases and Failure Scenarios

- Case: Login identifier matches both username and email records due to bad data.
- Expected behavior: Prohibit ambiguous state via unique constraints and normalized validation.

- Case: Disabled user attempts login.
- Expected behavior: Auth fails with generic credential error (no user enumeration).

- Case: Machine client exists without mapped system user row.
- Expected behavior: Token issuance denied until mapping exists.

- Case: Permission exists but role removed during active token lifetime.
- Expected behavior: Access continues only until token expiry; next token reflects new grants.

- Case: Principal has permission but no client scope rows.
- Expected behavior: Default deny for scoped resources.

- Case: Auditor resolution with unauthenticated context during internal startup writes.
- Expected behavior: Use explicit bootstrap/system security context or fail fast based on operation policy; never write null audit user to NOT NULL columns.

- Case: JWT signing key rotation.
- Expected behavior: New tokens signed with active key; old keys retained for verification during overlap period.

## 12. Adjacent Feature Recommendations

- Recommendation: Add short-lived in-memory/Redis cache for authorization lookups (roles/groups/client scopes) keyed by principal ID.
- Reason: Reduces repeated DB hits on high-throughput endpoints.
- Include now? `yes`

- Recommendation: Add admin audit event table for security mutations (role changes, scope assignments, client changes).
- Reason: Strengthens compliance traceability.
- Include now? `yes`

- Recommendation: Introduce endpoint-specific permission matrix documentation in `README` and OpenAPI extensions.
- Reason: Makes frontend/integration onboarding faster.
- Include now? `yes`

- Recommendation: Add account lockout and login throttling policy.
- Reason: Reduces brute-force risk.
- Include now? `yes`

## Explicit Out Of Scope

- External IdP federation.
- MFA/webauthn in v1.
- Self-service registration/password reset flows.
- Token introspection endpoint for third-party resource servers.
