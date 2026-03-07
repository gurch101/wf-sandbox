CREATE TABLE tenants (
  id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name VARCHAR(100) NOT NULL UNIQUE,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_by INTEGER NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_by INTEGER NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_tenants_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_tenants_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
);

ALTER TABLE users
  ADD COLUMN tenant_id INTEGER;

ALTER TABLE users
  ADD CONSTRAINT fk_users_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id);

CREATE INDEX idx_users_tenant_id ON users (tenant_id);

SELECT setval(
  pg_get_serial_sequence('users', 'id'),
  GREATEST((SELECT COALESCE(MAX(id), 1) FROM users), 1),
  true
);

