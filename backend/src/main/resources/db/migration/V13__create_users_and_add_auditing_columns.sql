CREATE TABLE users (
  id UUID PRIMARY KEY,
  username VARCHAR(100) NOT NULL UNIQUE,
  email VARCHAR(320) NOT NULL UNIQUE,
  enabled BOOLEAN NOT NULL,
  is_system BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

INSERT INTO users (id, username, email, enabled, is_system, created_at, updated_at)
VALUES (
  '00000000-0000-0000-0000-000000000000',
  'system',
  'system@local.invalid',
  true,
  true,
  now(),
  now()
)
ON CONFLICT (id) DO NOTHING;

ALTER TABLE requests
  ADD COLUMN created_by UUID NOT NULL,
  ADD COLUMN updated_by UUID NOT NULL;

ALTER TABLE requests
  ADD CONSTRAINT fk_requests_created_by FOREIGN KEY (created_by) REFERENCES users (id),
  ADD CONSTRAINT fk_requests_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);

ALTER TABLE request_tasks
  ADD COLUMN created_by UUID NOT NULL,
  ADD COLUMN updated_by UUID NOT NULL,
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE request_tasks
  ADD CONSTRAINT fk_request_tasks_created_by FOREIGN KEY (created_by) REFERENCES users (id),
  ADD CONSTRAINT fk_request_tasks_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);

CREATE TABLE oauth_clients (
  client_id VARCHAR(100) PRIMARY KEY,
  client_secret_hash VARCHAR(255),
  grant_types VARCHAR(255) NOT NULL,
  scopes VARCHAR(500) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT true,
  redirect_uris TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE system_client_users (
  client_id VARCHAR(100) PRIMARY KEY REFERENCES oauth_clients(client_id) ON DELETE CASCADE,
  user_id UUID NOT NULL UNIQUE REFERENCES users(id)
);

CREATE TABLE oauth_refresh_token_families (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  client_id VARCHAR(100) NOT NULL REFERENCES oauth_clients(client_id),
  revoked BOOLEAN NOT NULL DEFAULT false,
  expires_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  UNIQUE (user_id, client_id)
);

CREATE TABLE oauth_refresh_tokens (
  token_hash VARCHAR(128) PRIMARY KEY,
  family_id UUID NOT NULL REFERENCES oauth_refresh_token_families(id) ON DELETE CASCADE,
  is_active BOOLEAN NOT NULL,
  issued_at TIMESTAMPTZ NOT NULL,
  used_at TIMESTAMPTZ NULL
);

CREATE INDEX idx_oauth_refresh_tokens_family_id ON oauth_refresh_tokens(family_id);

CREATE TABLE user_credentials (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  password_hash VARCHAR(255) NOT NULL,
  password_updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE roles (
  id UUID PRIMARY KEY,
  code VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(150) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE permissions (
  id UUID PRIMARY KEY,
  code VARCHAR(120) NOT NULL UNIQUE,
  description VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE role_permissions (
  role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
  permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_roles (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (user_id, role_id)
);

CREATE TABLE workflow_groups (
  id UUID PRIMARY KEY,
  code VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(150) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE user_workflow_groups (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  workflow_group_id UUID NOT NULL REFERENCES workflow_groups(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (user_id, workflow_group_id)
);

CREATE TABLE principal_client_scopes (
  id UUID PRIMARY KEY,
  principal_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  business_client_id VARCHAR(100) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  UNIQUE (principal_user_id, business_client_id)
);

ALTER TABLE requests
  ADD COLUMN workflow_group_code VARCHAR(100);

ALTER TABLE requests
  ADD COLUMN business_client_id VARCHAR(100) NOT NULL DEFAULT 'CLIENT_A';

CREATE INDEX idx_requests_business_client_id ON requests(business_client_id);

ALTER TABLE api_idempotency_records
  ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  ADD COLUMN created_by UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
  ADD COLUMN updated_by UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000';

ALTER TABLE api_idempotency_records
  ADD CONSTRAINT fk_idempotency_created_by FOREIGN KEY (created_by) REFERENCES users (id),
  ADD CONSTRAINT fk_idempotency_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);

ALTER TABLE forms
  ADD COLUMN created_by UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
  ADD COLUMN updated_by UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000';

ALTER TABLE forms
  ADD CONSTRAINT fk_forms_created_by FOREIGN KEY (created_by) REFERENCES users (id),
  ADD CONSTRAINT fk_forms_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);

ALTER TABLE request_types
  ADD COLUMN created_by UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
  ADD COLUMN updated_by UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000';

ALTER TABLE request_types
  ADD CONSTRAINT fk_request_types_created_by FOREIGN KEY (created_by) REFERENCES users (id),
  ADD CONSTRAINT fk_request_types_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);

ALTER TABLE request_type_versions
  ADD COLUMN created_by UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
  ADD COLUMN updated_by UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000';

ALTER TABLE request_type_versions
  ADD CONSTRAINT fk_request_type_versions_created_by FOREIGN KEY (created_by) REFERENCES users (id),
  ADD CONSTRAINT fk_request_type_versions_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);
