CREATE TABLE users (
  id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  username VARCHAR(100) NOT NULL UNIQUE,
  email VARCHAR(255) NOT NULL UNIQUE,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_by INTEGER NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_by INTEGER NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
);

INSERT INTO users (id, username, email, active, created_by, created_at, updated_by, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (1, 'system', 'system@sandbox.local', TRUE, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP);

ALTER TABLE users
  ADD CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  ADD CONSTRAINT fk_users_updated_by FOREIGN KEY (updated_by) REFERENCES users(id);

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

INSERT INTO tenants (id, name, active, created_by, created_at, updated_by, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (1, 'default-tenant', TRUE, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP);

ALTER TABLE users
  ADD COLUMN tenant_id INTEGER,
  ADD CONSTRAINT fk_users_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id);

CREATE INDEX idx_users_tenant_id ON users (tenant_id);

INSERT INTO users (id, username, email, active, created_by, created_at, updated_by, updated_at, tenant_id)
OVERRIDING SYSTEM VALUE
VALUES
  (2, 'alice', 'alice@sandbox.local', TRUE, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 1),
  (3, 'bob', 'bob@sandbox.local', TRUE, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, NULL);

SELECT setval(
  pg_get_serial_sequence('tenants', 'id'),
  GREATEST((SELECT COALESCE(MAX(id), 1) FROM tenants), 1),
  true
);

SELECT setval(
  pg_get_serial_sequence('users', 'id'),
  GREATEST((SELECT COALESCE(MAX(id), 1) FROM users), 1),
  true
);

CREATE TABLE storage_objects (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  file_name VARCHAR(255) NOT NULL,
  mime_type VARCHAR(255) NOT NULL,
  content_size BIGINT NOT NULL,
  checksum_sha256 VARCHAR(64) NOT NULL,
  storage_provider VARCHAR(32) NOT NULL,
  storage_path TEXT NOT NULL UNIQUE,
  created_by INTEGER NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT fk_storage_objects_created_by FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE requests (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  status VARCHAR(64) NOT NULL,
  process_instance_id VARCHAR(64),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  request_type_key VARCHAR(100),
  request_type_version INTEGER,
  created_by INTEGER NOT NULL,
  updated_by INTEGER NOT NULL,
  CONSTRAINT fk_requests_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_requests_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
);

CREATE INDEX idx_requests_status ON requests(status);
CREATE INDEX idx_requests_status_created_at_desc ON requests(status, created_at DESC);
CREATE INDEX idx_requests_type_key ON requests(request_type_key);
CREATE INDEX idx_requests_type_key_version ON requests(request_type_key, request_type_version);
CREATE INDEX idx_requests_upper_type_key ON requests(upper(request_type_key));

CREATE TABLE api_idempotency_records (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  idempotency_key VARCHAR(128) NOT NULL,
  operation VARCHAR(255) NOT NULL,
  request_hash VARCHAR(128) NOT NULL,
  status VARCHAR(20) NOT NULL,
  response_status INT,
  response_body JSONB,
  created_at TIMESTAMPTZ NOT NULL,
  created_by INTEGER NOT NULL,
  UNIQUE (idempotency_key, operation),
  CONSTRAINT fk_api_idempotency_records_created_by FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE INDEX idx_idempotency_key_operation ON api_idempotency_records(idempotency_key, operation);
CREATE INDEX idx_idempotency_created_at ON api_idempotency_records(created_at);

CREATE TABLE request_tasks (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  request_id BIGINT NOT NULL REFERENCES requests(id) ON DELETE CASCADE,
  process_instance_id VARCHAR(64) NOT NULL,
  task_id VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL,
  assignee VARCHAR(255),
  action VARCHAR(32),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  created_by INTEGER NOT NULL,
  updated_by INTEGER NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_request_tasks_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_request_tasks_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
);

CREATE INDEX idx_request_tasks_request_id ON request_tasks(request_id);
CREATE INDEX idx_request_tasks_assignee ON request_tasks(assignee);
CREATE INDEX idx_request_tasks_status ON request_tasks(status);

CREATE TABLE request_types (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  type_key VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  active_version_id BIGINT,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_by INTEGER NOT NULL,
  updated_by INTEGER NOT NULL,
  CONSTRAINT fk_request_types_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_request_types_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
);

CREATE TABLE request_type_versions (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  request_type_id BIGINT NOT NULL REFERENCES request_types(id) ON DELETE CASCADE,
  type_version INTEGER NOT NULL,
  process_definition_key VARCHAR(100) NOT NULL,
  config_json JSONB,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  created_by INTEGER NOT NULL,
  updated_by INTEGER NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE (request_type_id, type_version),
  UNIQUE (process_definition_key),
  CONSTRAINT fk_request_type_versions_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_request_type_versions_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
);

ALTER TABLE request_types
  ADD CONSTRAINT fk_request_types_active_version
  FOREIGN KEY (active_version_id) REFERENCES request_type_versions(id);

CREATE INDEX idx_request_types_active_version_id ON request_types(active_version_id);

CREATE TABLE document_templates (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  en_name VARCHAR(255) NOT NULL,
  fr_name VARCHAR(255),
  en_description TEXT,
  fr_description TEXT,
  storage_object_id BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  form_map_json TEXT,
  esignable BOOLEAN NOT NULL DEFAULT FALSE,
  tenant_id INTEGER,
  created_by INTEGER NOT NULL,
  updated_by INTEGER NOT NULL,
  language VARCHAR(16) NOT NULL DEFAULT 'ENGLISH',
  esign_anchor_metadata_json TEXT,
  CONSTRAINT fk_document_templates_storage_object_id FOREIGN KEY (storage_object_id) REFERENCES storage_objects(id),
  CONSTRAINT fk_document_templates_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_document_templates_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_document_templates_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
);

CREATE INDEX idx_document_templates_upper_en_name_prefix
  ON document_templates ((upper(en_name)) text_pattern_ops);
CREATE INDEX idx_document_templates_upper_fr_name_prefix
  ON document_templates ((upper(fr_name)) text_pattern_ops);
CREATE INDEX idx_document_templates_created_at_desc ON document_templates(created_at DESC);
CREATE INDEX idx_document_templates_tenant_id ON document_templates(tenant_id);

CREATE TABLE audit_log_events (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  resource_type VARCHAR(100) NOT NULL,
  resource_id VARCHAR(100) NOT NULL,
  action VARCHAR(16) NOT NULL,
  actor_user_id INTEGER REFERENCES users(id),
  tenant_id INTEGER REFERENCES tenants(id),
  correlation_id VARCHAR(128),
  before_state JSONB,
  after_state JSONB,
  created_by INTEGER NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT chk_audit_log_events_action
      CHECK (action IN ('CREATE', 'UPDATE', 'DELETE')),
  CONSTRAINT chk_audit_log_events_state_by_action
      CHECK (
          (action = 'CREATE' AND before_state IS NULL AND after_state IS NOT NULL)
          OR (action = 'UPDATE' AND before_state IS NOT NULL AND after_state IS NOT NULL)
          OR (action = 'DELETE' AND before_state IS NOT NULL AND after_state IS NULL)
      )
);

CREATE INDEX idx_audit_log_events_resource_time
    ON audit_log_events(resource_type, resource_id, created_at DESC);
CREATE INDEX idx_audit_log_events_actor_time
    ON audit_log_events(actor_user_id, created_at DESC);
CREATE INDEX idx_audit_log_events_tenant_time
    ON audit_log_events(tenant_id, created_at DESC);
CREATE INDEX idx_audit_log_events_correlation
    ON audit_log_events(correlation_id)
    WHERE correlation_id IS NOT NULL;

CREATE TABLE request_activity_events (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  request_id BIGINT NOT NULL REFERENCES requests(id) ON DELETE CASCADE,
  event_type VARCHAR(64) NOT NULL,
  actor_user_id INTEGER REFERENCES users(id),
  correlation_id VARCHAR(128),
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_by INTEGER NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_request_activity_events_request_time
    ON request_activity_events(request_id, created_at DESC);
CREATE INDEX idx_request_activity_events_event_type
    ON request_activity_events(event_type);

CREATE TABLE esign_envelopes (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  external_envelope_id VARCHAR(128) UNIQUE,
  subject VARCHAR(255) NOT NULL,
  message TEXT,
  delivery_mode VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  tenant_id INTEGER,
  source_storage_object_id BIGINT NOT NULL,
  signed_storage_object_id BIGINT,
  certificate_storage_object_id BIGINT,
  reminders_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  reminder_interval_hours INTEGER,
  voided_reason TEXT,
  completed_at TIMESTAMPTZ,
  last_provider_update_at TIMESTAMPTZ,
  created_by INTEGER NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_by INTEGER NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_esign_envelopes_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_esign_envelopes_updated_by FOREIGN KEY (updated_by) REFERENCES users(id),
  CONSTRAINT fk_esign_envelopes_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_esign_envelopes_source_storage_object_id FOREIGN KEY (source_storage_object_id) REFERENCES storage_objects(id),
  CONSTRAINT fk_esign_envelopes_signed_storage_object_id FOREIGN KEY (signed_storage_object_id) REFERENCES storage_objects(id),
  CONSTRAINT fk_esign_envelopes_certificate_storage_object_id FOREIGN KEY (certificate_storage_object_id) REFERENCES storage_objects(id)
);

CREATE TABLE esign_signers (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  envelope_id BIGINT NOT NULL,
  role_key VARCHAR(32) NOT NULL,
  signature_anchor_text VARCHAR(32) NOT NULL,
  date_anchor_text VARCHAR(32),
  routing_order INTEGER NOT NULL,
  full_name VARCHAR(255) NOT NULL,
  email VARCHAR(255),
  delivery_method VARCHAR(16),
  auth_method VARCHAR(32) NOT NULL,
  sms_number VARCHAR(50),
  provider_recipient_id VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  viewed_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  last_status_at TIMESTAMPTZ,
  created_by INTEGER NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_by INTEGER NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_esign_signers_envelope_id FOREIGN KEY (envelope_id) REFERENCES esign_envelopes(id),
  CONSTRAINT fk_esign_signers_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_esign_signers_updated_by FOREIGN KEY (updated_by) REFERENCES users(id),
  CONSTRAINT uk_esign_signers_envelope_role UNIQUE (envelope_id, role_key)
);

CREATE INDEX idx_esign_envelopes_status ON esign_envelopes(status);
CREATE INDEX idx_esign_envelopes_tenant_id ON esign_envelopes(tenant_id);
CREATE INDEX idx_esign_signers_envelope_id ON esign_signers(envelope_id);
CREATE INDEX idx_esign_signers_status ON esign_signers(status);
