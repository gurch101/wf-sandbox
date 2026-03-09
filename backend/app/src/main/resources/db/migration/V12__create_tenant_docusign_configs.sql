CREATE TABLE tenant_docusign_configs (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  tenant_id INTEGER NOT NULL UNIQUE REFERENCES tenants(id) ON DELETE CASCADE,
  base_path VARCHAR(255) NOT NULL,
  account_id VARCHAR(128) NOT NULL,
  auth_token TEXT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_by INTEGER NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL,
  updated_by INTEGER NOT NULL REFERENCES users(id),
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_tenant_docusign_configs_active
  ON tenant_docusign_configs (tenant_id, active);
