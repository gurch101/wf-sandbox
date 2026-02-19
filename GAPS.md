# Auth Gaps

## 1. SPA User Login Is Not Fully Implemented

- `authorization_code + PKCE` is wired and tested.
- Username/email + password authentication is wired to `users` + `user_credentials` via JDBC-backed `UserDetailsService` and `DaoAuthenticationProvider`.

## 2. Authorization Enforcement Is Partial

- `request.read` / `request.write` checks are implemented on request endpoints.
- `task.complete + workflow-group membership + client scope` is implemented.
- Explicit `task.list` is enforced for task-filtered request search (`taskAssignee` / `taskAssignees`).
- Dedicated task APIs now enforce explicit permissions:
  - `task.list` on `GET /api/tasks`.
  - `task.claim` on `PUT /api/tasks/{taskId}/claim`.
  - `task.assign` on `PUT /api/tasks/{taskId}/assign`.
  - Claim/assign additionally enforce workflow-group membership.

## 3. Client Scope Enforcement (`A/B/C`) Not Implemented

- `principal_client_scopes` storage and admin assignment API exist.
- Runtime scope enforcement is implemented for request reads/search:
  - `GET /api/requests/{id}` denies access outside principal client scope.
  - `/api/requests/search` results are filtered to principal client scopes.
- Task-level scope guards are implemented:
  - `task.complete`, `task.claim`, and `task.assign` enforce request client-scope membership.
  - `GET /api/tasks` returns tasks filtered by allowed client scopes.

## 4. `/api/auth/me` Is Minimal

- Returns roles/permissions from authorities/token.
- Hydrates workflow groups and client scopes from DB for UUID principals.

## 5. Admin Security APIs Are Not Full CRUD

- Create and assignment endpoints exist.
- Roles, permissions, and workflow groups now support list/get/delete with pagination/filtering and `409` conflict semantics on duplicate create / in-use delete.
- Assignment and principal client scope lifecycle now includes list + unassign operations for role-permission, user-role, user-workflow-group, and user-client-scope mappings.
- Update endpoints are implemented for roles, permissions, and workflow groups.

## 6. Auditing Rollout Is Incomplete

- `created_by` / `updated_by` is implemented for `requests`, `request_tasks`, and `api_idempotency_records`.
- Current domain tables now follow the standard audit fields.

## 7. JWT Key Management Needs Hardening

- JWT signing keys are persisted on filesystem and reused across restarts.
- Rotation strategy is supported via `auth.jwt.keys.active-key-id` + `auth.jwt.keys.retired-key-ids`.
