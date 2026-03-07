CREATE TABLE request_types (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  type_key VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  active_version_id BIGINT,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE request_type_versions (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  request_type_id BIGINT NOT NULL REFERENCES request_types(id) ON DELETE CASCADE,
  version INTEGER NOT NULL,
  payload_handler_id VARCHAR(100) NOT NULL,
  process_definition_key VARCHAR(100) NOT NULL,
  config_json JSONB,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  UNIQUE (request_type_id, version),
  UNIQUE (process_definition_key)
);

ALTER TABLE request_types
  ADD CONSTRAINT fk_request_types_active_version
  FOREIGN KEY (active_version_id) REFERENCES request_type_versions(id);

CREATE INDEX idx_request_types_active_version_id ON request_types(active_version_id);

ALTER TABLE requests
  ADD COLUMN request_type_key VARCHAR(100),
  ADD COLUMN request_type_version INTEGER,
  ADD COLUMN payload_json JSONB;

CREATE INDEX idx_requests_type_key ON requests(request_type_key);
CREATE INDEX idx_requests_type_key_version ON requests(request_type_key, request_type_version);

DROP INDEX IF EXISTS idx_requests_upper_name;
ALTER TABLE requests DROP COLUMN IF EXISTS name;
CREATE INDEX IF NOT EXISTS idx_requests_upper_type_key ON requests(upper(request_type_key));

ALTER TABLE request_tasks DROP COLUMN IF EXISTS comment;
