# Auth Module Domain

## Responsibilities

The `auth` module provides authentication, token issuance, and authorization context for users and machine clients.

It is responsible for:

- OAuth2 and OIDC-compatible authorization flows via Spring Authorization Server.
- Username/email + password authentication against the local `users` and `user_credentials` tables.
- JWT signing key management and key rotation support.
- Role-based access control (RBAC) administration:
  - roles
  - permissions
  - workflow groups
  - user assignments
- Principal business-client scope assignment used for data partitioning.
- Refresh-token rotation and replay detection via refresh-token family tracking.

## Core Concepts

### User

A person or system identity represented in `users`. Human users can authenticate with credentials in `user_credentials`. Machine clients map to users via `system_client_users`.

### Role

A named bundle of permissions (for example `REQUEST_OPERATOR`) represented in `roles`. Roles are assigned to users through `user_roles`.

### Permission

A granular capability (for example `request.read` or `task.claim`) represented in `permissions`. Permissions are linked to roles through `role_permissions` and enforced at API boundaries.

### Workflow Group

A functional grouping (for example approvers for a workflow) represented in `workflow_groups`. Membership is represented in `user_workflow_groups` and enforced for task operations.

### Client Scope

A business partition identifier (for example `CLIENT_A`) assigned per user in `principal_client_scopes`. Client scopes limit which request/task data a principal can access.

### Refresh Token Family

A refresh-token family is the set of sequentially rotated refresh tokens for one `(user_id, client_id)` pair. It is represented by:

- `oauth_refresh_token_families`: family metadata (`revoked`, `expires_at`, `updated_at`)
- `oauth_refresh_tokens`: hashed token instances (`is_active`, `used_at`)

If token replay is detected, the full family is revoked to force re-authentication.

## How Role vs Client Scope Differ

- Role/permission answers: "What actions can this principal perform?"
- Client scope answers: "Which business-client data partitions can this principal access?"

Both are required for many request/task operations.
