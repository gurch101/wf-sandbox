CREATE TABLE policy_versions (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  request_type_id BIGINT NOT NULL REFERENCES request_types(id) ON DELETE CASCADE,
  policy_version INTEGER NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_by INTEGER NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_by INTEGER NOT NULL REFERENCES users(id),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE (request_type_id, policy_version)
);

CREATE TABLE policy_input_catalog (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  policy_version_id BIGINT NOT NULL REFERENCES policy_versions(id) ON DELETE CASCADE,
  field_key VARCHAR(200) NOT NULL,
  label VARCHAR(200) NOT NULL,
  source_type VARCHAR(16) NOT NULL,
  data_type VARCHAR(16) NOT NULL,
  required BOOLEAN NOT NULL DEFAULT FALSE,
  path VARCHAR(300),
  provider_key VARCHAR(100),
  depends_on_json JSONB,
  freshness_sla_seconds INTEGER,
  examples_json JSONB,
  created_by INTEGER NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_by INTEGER NOT NULL REFERENCES users(id),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE (policy_version_id, field_key)
);

CREATE INDEX idx_policy_input_catalog_version
    ON policy_input_catalog(policy_version_id);

CREATE TABLE policy_output_contracts (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  policy_version_id BIGINT NOT NULL UNIQUE REFERENCES policy_versions(id) ON DELETE CASCADE,
  output_schema_json JSONB NOT NULL,
  created_by INTEGER NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_by INTEGER NOT NULL REFERENCES users(id),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  version BIGINT NOT NULL DEFAULT 0
);
